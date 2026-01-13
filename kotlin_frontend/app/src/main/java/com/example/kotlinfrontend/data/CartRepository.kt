package com.example.kotlinfrontend.data

import android.content.Context
import com.example.kotlinfrontend.model.CartItem
import com.example.kotlinfrontend.model.CartSummary
import com.example.kotlinfrontend.model.CartTotals
import com.example.kotlinfrontend.model.Coupon
import com.example.kotlinfrontend.model.DiscountType
import com.example.kotlinfrontend.model.Product
import com.example.kotlinfrontend.network.ApiClient
import com.example.kotlinfrontend.network.CartApi
import com.example.kotlinfrontend.network.dto.CartDto
import com.example.kotlinfrontend.network.dto.CartItemMutationRequestDto
import com.example.kotlinfrontend.network.dto.CartUpdateQuantityRequestDto
import com.example.kotlinfrontend.network.dto.CartLineItemDto
import com.example.kotlinfrontend.network.dto.CouponRemoveRequestDto
import com.example.kotlinfrontend.network.dto.CouponRequestDto
import com.example.kotlinfrontend.network.dto.CouponResultDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

/**
 * Repository for cart state and persistence.
 *
 * Backend-synced mode:
 * - A single MutableStateFlow is the source of truth for in-memory UI.
 * - We optimistically update UI, persist to local cache, then attempt network sync.
 * - On network failure, we keep local state and emit error events.
 *
 * Coupon/discount:
 * - Coupon is persisted separately (CouponStore) so it survives process death.
 * - Totals are recomputed whenever items or coupon change.
 * - We attempt backend validation/apply/remove when identity exists; otherwise local-only validation is used.
 *
 * New (server-side coupon rules):
 * - Coupon validate/apply/remove now sends current cart line items:
 *   productId, category, unitPrice, qty
 * - This enables backend to enforce min subtotal, category eligibility, and usage limits.
 * - All new request fields are optional; we keep graceful fallback if backend ignores them.
 */
class CartRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = CartStore(appContext)
    private val couponStore = CouponStore(appContext)
    private val savedCouponsStore = SavedCouponsStore(appContext)
    private val identityStore = CartIdentityStore(appContext)

    private val cartApi: CartApi = ApiClient.createCartApi()

    /**
     * Single source of truth in-memory (thread-safe via mutex around compound operations).
     */
    private val _items = MutableStateFlow<List<CartItem>>(store.load())
    val items: StateFlow<List<CartItem>> = _items.asStateFlow()

    private val _activeEmail = MutableStateFlow<String?>(identityStore.getEmail())
    val activeEmail: StateFlow<String?> = _activeEmail.asStateFlow()

    private val _coupon = MutableStateFlow<Coupon?>(couponStore.load())
    val coupon: StateFlow<Coupon?> = _coupon.asStateFlow()

    private val _couponValidationState = MutableStateFlow<CouponValidationState>(CouponValidationState.None)
    val couponValidationState: StateFlow<CouponValidationState> = _couponValidationState.asStateFlow()

    /**
     * Last user-facing coupon validation feedback (for inline error/helper text in Cart/Checkout).
     *
     * - Network/transient failures are still emitted via errorEvents (Snackbars).
     * - Rule violations should be shown inline to preserve the typed code and make it actionable.
     */
    private val _lastCouponUxFeedback = MutableStateFlow<CouponUxFeedback>(CouponUxFeedback(CouponUxFeedback.Type.NONE, ""))
    val lastCouponUxFeedback: StateFlow<CouponUxFeedback> = _lastCouponUxFeedback.asStateFlow()

    /**
     * Recently saved coupon codes (local-only).
     */
    private val _savedCoupons = MutableStateFlow<List<String>>(savedCouponsStore.load())
    val savedCoupons: StateFlow<List<String>> = _savedCoupons.asStateFlow()

    /**
     * Coupon suggestions to show in autocomplete/dropdown.
     *
     * Currently: local saved coupons only.
     * Future: can merge server suggestions with local saved.
     */
    private val _couponSuggestions = MutableStateFlow<List<String>>(savedCouponsStore.load())
    val couponSuggestions: StateFlow<List<String>> = _couponSuggestions.asStateFlow()

    private val _totals = MutableStateFlow(computeTotals(items = _items.value, coupon = _coupon.value))
    val totals: StateFlow<CartTotals> = _totals.asStateFlow()

    private val _errorEvents = MutableSharedFlow<CartErrorEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errorEvents: SharedFlow<CartErrorEvent> = _errorEvents.asSharedFlow()

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mutex = Mutex()

    @Volatile
    private var hasFetchedFromBackendForIdentity: String? = null

    // PUBLIC_INTERFACE
    fun setActiveEmail(email: String?) {
        /**
         * Set (or clear) the cart identity (email). This will:
         * - store to SharedPreferences
         * - update flow
         * - attempt to fetch cart from backend (best effort)
         * - migrate local cached items to backend (best effort) on first successful identity setup
         *
         * Coupon behavior:
         * - We keep coupon locally even if identity changes, but server operations are attempted only when email exists.
         */
        val normalized = email?.trim()?.ifBlank { null }
        identityStore.setEmail(normalized)
        _activeEmail.value = normalized
        hasFetchedFromBackendForIdentity = null

        if (normalized != null) {
            // Best-effort: migrate local cache items and then refresh from backend to reconcile.
            repoScope.launch {
                migrateLocalCacheToBackendIfNeeded(normalized)
                refreshFromBackend(normalized)
                // Optional: attempt server validation of existing coupon if user has one.
                val existingCoupon = _coupon.value
                if (existingCoupon != null) {
                    validateCouponWithBackendBestEffort(email = normalized, code = existingCoupon.code)
                }
            }
        }
    }

    // PUBLIC_INTERFACE
    fun ensureCartLoaded() {
        /**
         * Ensure the repository has attempted to fetch the backend cart at least once
         * for the current identity. Safe to call multiple times.
         */
        val email = _activeEmail.value ?: return
        if (hasFetchedFromBackendForIdentity == email) return

        repoScope.launch {
            refreshFromBackend(email)
        }
    }

    // PUBLIC_INTERFACE
    fun addItem(product: Product, qty: Int) {
        /** Add qty for a product. If already in cart, increments quantity. */
        if (qty <= 0) return

        val category = product.category?.trim().orEmpty().ifBlank { "Uncategorized" }
        val email = _activeEmail.value

        repoScope.launch {
            mutex.withLock {
                val current = _items.value
                val idx = current.indexOfFirst { it.productId == product.id }
                val updated = if (idx >= 0) {
                    val existing = current[idx]
                    val newQty = existing.quantity + qty
                    current.toMutableList().apply {
                        this[idx] = existing.copy(quantity = newQty)
                    }.toList()
                } else {
                    current + CartItem(
                        productId = product.id,
                        name = product.name,
                        price = product.price,
                        category = category,
                        quantity = qty
                    )
                }
                setAndPersist(updated)
            }

            // Backend sync (best effort). If no identity yet, keep local only.
            if (email != null) {
                try {
                    val body = CartItemMutationRequestDto(
                        productId = product.id,
                        quantity = qty,
                        price = product.price,
                        category = category,
                        name = product.name
                    )
                    val cart = cartApi.addItem(email = email, body = body)
                    reconcileFromCartDto(cart)
                } catch (t: Throwable) {
                    emitError("Couldn't sync cart. You're offline?", CartErrorEvent.Operation.ADD_ITEM, t)
                }
            }
        }
    }

    // PUBLIC_INTERFACE
    fun updateQty(productId: String, qty: Int) {
        /** Set absolute quantity for a cart item; if qty <= 0 the item is removed. */
        val email = _activeEmail.value

        repoScope.launch {
            mutex.withLock {
                val current = _items.value
                val idx = current.indexOfFirst { it.productId == productId }
                if (idx < 0) return@withLock

                val updated = if (qty <= 0) {
                    current.filterNot { it.productId == productId }
                } else {
                    current.toMutableList().apply {
                        this[idx] = this[idx].copy(quantity = qty)
                    }.toList()
                }
                setAndPersist(updated)
            }

            if (email != null) {
                try {
                    val cart = if (qty <= 0) {
                        cartApi.removeItem(email = email, productId = productId)
                    } else {
                        val body = CartUpdateQuantityRequestDto(productId = productId, quantity = qty)
                        cartApi.updateQuantity(email = email, body = body)
                    }
                    reconcileFromCartDto(cart)
                } catch (t: Throwable) {
                    emitError("Couldn't sync cart quantity. You're offline?", CartErrorEvent.Operation.UPDATE_QTY, t)
                }
            }
        }
    }

    // PUBLIC_INTERFACE
    fun removeItem(productId: String) {
        /** Remove a product from the cart. */
        val email = _activeEmail.value

        repoScope.launch {
            mutex.withLock {
                val updated = _items.value.filterNot { it.productId == productId }
                setAndPersist(updated)
            }

            if (email != null) {
                try {
                    val cart = cartApi.removeItem(email = email, productId = productId)
                    reconcileFromCartDto(cart)
                } catch (t: Throwable) {
                    emitError("Couldn't remove item (offline?).", CartErrorEvent.Operation.REMOVE_ITEM, t)
                }
            }
        }
    }

    // PUBLIC_INTERFACE
    fun clear() {
        /** Clear cart items. */
        val email = _activeEmail.value

        repoScope.launch {
            mutex.withLock {
                _items.value = emptyList()
                store.clear()
                recomputeTotalsLocked()
            }

            if (email != null) {
                try {
                    val cart = cartApi.clearCart(email = email)
                    reconcileFromCartDto(cart)
                } catch (t: Throwable) {
                    emitError("Couldn't clear cart (offline?).", CartErrorEvent.Operation.CLEAR_CART, t)
                }
            }
        }
    }

    // PUBLIC_INTERFACE
    fun addSavedCoupon(codeRaw: String) {
        /** Add code to saved coupons list (most-recent-first). */
        val updated = savedCouponsStore.add(codeRaw)
        _savedCoupons.value = updated
        _couponSuggestions.value = mergeSuggestions(saved = updated, server = null)
    }

    // PUBLIC_INTERFACE
    fun removeSavedCoupon(codeRaw: String) {
        /** Remove code from saved coupons list. */
        val updated = savedCouponsStore.remove(codeRaw)
        _savedCoupons.value = updated
        _couponSuggestions.value = mergeSuggestions(saved = updated, server = null)
    }

    // PUBLIC_INTERFACE
    fun clearSavedCoupons() {
        /** Clear all saved coupons. */
        savedCouponsStore.clear()
        _savedCoupons.value = emptyList()
        _couponSuggestions.value = emptyList()
    }

    // PUBLIC_INTERFACE
    fun isCouponSaved(codeRaw: String): Boolean {
        /** Returns true if code is in saved coupons (case-insensitive). */
        return savedCouponsStore.isSaved(codeRaw)
    }

    // PUBLIC_INTERFACE
    fun applyCoupon(codeRaw: String) {
        /**
         * Apply a coupon code.
         *
         * UX behavior:
         * - Preserve the typed code in the field (UI should not clear it on errors).
         * - Rule/validation errors are exposed via couponValidationState + lastCouponUxFeedback (inline).
         * - Network/transient failures remain snackbars via errorEvents (with Retry).
         */
        val code = normalizeCouponCode(codeRaw)
        if (code.isBlank()) {
            repoScope.launch {
                mutex.withLock {
                    setCouponLocked(coupon = null, state = CouponValidationState.Invalid("Enter a coupon code."))
                    _lastCouponUxFeedback.value = CouponUxFeedback(
                        type = CouponUxFeedback.Type.INVALID_RULE,
                        message = "Enter a coupon code."
                    )
                }
            }
            return
        }

        val currentSubtotal = _items.value.sumOf { it.subtotal() }
        val localCoupon = defaultLocalCouponForCode(code = code)

        repoScope.launch {
            val previousCoupon = _coupon.value
            val previousState = _couponValidationState.value

            mutex.withLock {
                // Optimistic: apply coupon immediately using locally derived coupon metadata (fallback).
                setCouponLocked(coupon = localCoupon, state = CouponValidationState.PendingServerValidation)
                _lastCouponUxFeedback.value = CouponUxFeedback(CouponUxFeedback.Type.NONE, "")

                // If local constraints fail, treat as invalid immediately.
                val localConstraintError = validateCouponAgainstLocalConstraints(localCoupon, currentSubtotal)
                if (localConstraintError != null) {
                    val friendly = CouponErrorMessageMapper.map(localConstraintError)
                    setCouponLocked(coupon = null, state = CouponValidationState.Invalid(friendly))
                    _lastCouponUxFeedback.value = CouponUxFeedback(CouponUxFeedback.Type.INVALID_RULE, friendly)
                    return@launch
                }
            }

            val email = _activeEmail.value
            if (email.isNullOrBlank()) {
                // No identity => local-only (still pending server validation).
                mutex.withLock {
                    if (_coupon.value != null) _couponValidationState.value = CouponValidationState.PendingServerValidation
                }
                return@launch
            }

            // Backend best effort: validate then apply, sending line items.
            try {
                val isValid = validateCouponWithBackendBestEffort(email = email, code = code)
                if (!isValid) {
                    // validateCouponWithBackendBestEffort already updated state/coupon/feedback.
                    return@launch
                }

                // Attempt applyCoupon endpoint; if missing, we still accept locally (pending server).
                try {
                    // First: apply rules via normalized endpoint so we can show rule metadata/messages.
                    val result = cartApi.applyCouponRules(
                        body = CouponRequestDto(code = code, email = email, items = buildCouponLineItemsLockedUnsafe())
                    )

                    if (!result.valid) {
                        val friendly = CouponErrorMessageMapper.map(result.messages.firstOrNull() ?: "Coupon can't be applied.")
                        mutex.withLock {
                            setCouponLocked(coupon = null, state = CouponValidationState.Invalid(friendly))
                            _lastCouponUxFeedback.value = CouponUxFeedback(
                                type = CouponUxFeedback.Type.INVALID_RULE,
                                message = friendly
                            )
                        }
                        return@launch
                    }

                    // Then: apply to cart (so server cart totals/items can be reconciled if backend supports it).
                    // This keeps backward compatibility with existing cart endpoints.
                    try {
                        val cart = cartApi.applyCoupon(
                            email = email,
                            body = CouponRequestDto(code = code, email = email, items = buildCouponLineItemsLockedUnsafe())
                        )
                        reconcileFromCartDto(cart)
                    } catch (_: Throwable) {
                        // If cart apply endpoint fails (e.g., older backend), we still keep coupon as valid locally.
                    }

                    mutex.withLock {
                        _couponValidationState.value = CouponValidationState.Valid
                        _lastCouponUxFeedback.value = CouponUxFeedback(
                            type = CouponUxFeedback.Type.APPLIED,
                            message = buildAppliedMessage(result)
                        )
                        val updatedSaved = savedCouponsStore.add(code)
                        _savedCoupons.value = updatedSaved
                        _couponSuggestions.value = mergeSuggestions(saved = updatedSaved, server = null)
                        recomputeTotalsLocked()
                    }
                } catch (tApply: Throwable) {
                    // Soft failure: keep local coupon and allow UI to continue, but mark pending.
                    mutex.withLock { _couponValidationState.value = CouponValidationState.PendingServerValidation }
                }
            } catch (t: Throwable) {
                // Rollback on hard failures (e.g. network)
                mutex.withLock {
                    _coupon.value = previousCoupon
                    _couponValidationState.value = previousState
                    couponStore.save(previousCoupon)
                    recomputeTotalsLocked()
                }
                emitError("Couldn't apply coupon. Please try again.", CartErrorEvent.Operation.APPLY_COUPON, t)
            }
        }
    }

    // PUBLIC_INTERFACE
    fun removeCoupon() {
        /**
         * Remove coupon from cart (optimistic).
         *
         * Preferred backend route for Android: POST /api/carts/coupon/remove with body { email }.
         * We also keep a fallback to the older DELETE /api/carts/coupon?email=... route.
         */
        val email = _activeEmail.value
        repoScope.launch {
            val previousCoupon = _coupon.value
            val previousState = _couponValidationState.value

            mutex.withLock {
                setCouponLocked(coupon = null, state = CouponValidationState.None)
            }

            if (email.isNullOrBlank()) return@launch

            try {
                // Preferred: normalized remove endpoint
                try {
                    cartApi.removeCouponRules(body = CouponRemoveRequestDto(email = email))
                } catch (_: Throwable) {
                    // Fallback: old cart endpoint
                    cartApi.removeCoupon(email = email)
                }

                // We don't strictly need to reconcile cart items; coupon removal only affects totals.
                // If backend returns updated cart elsewhere, future refresh will reconcile.
                mutex.withLock {
                    _couponValidationState.value = CouponValidationState.None
                    _lastCouponUxFeedback.value = CouponUxFeedback(CouponUxFeedback.Type.NONE, "")
                    recomputeTotalsLocked()
                }
            } catch (t: Throwable) {
                mutex.withLock {
                    _coupon.value = previousCoupon
                    _couponValidationState.value = previousState
                    couponStore.save(previousCoupon)
                    recomputeTotalsLocked()
                }
                emitError("Couldn't remove coupon. Please try again.", CartErrorEvent.Operation.REMOVE_COUPON, t)
            }
        }
    }

    // PUBLIC_INTERFACE
    fun summary(): CartSummary {
        /** Compute cart totals (from local in-memory state). */
        val list = _items.value
        val totalQty = list.sumOf { it.quantity }
        val subtotal = list.sumOf { it.subtotal() }
        return CartSummary(
            distinctItems = list.size,
            totalQuantity = totalQty,
            subtotal = subtotal
        )
    }

    // PUBLIC_INTERFACE
    fun discountedTotals(): CartTotals {
        /** Compute cart totals including coupon discount (from local in-memory state). */
        return _totals.value
    }

    // PUBLIC_INTERFACE
    fun groupedByCategory(): Map<String, List<CartItem>> {
        /** Returns a stable grouping by category (sorted by category name). */
        return _items.value
            .groupBy { it.category }
            .toSortedMap(compareBy { it.lowercase() })
    }

    private suspend fun refreshFromBackend(email: String) {
        try {
            val cart = cartApi.getCurrentCart(email = email)
            reconcileFromCartDto(cart)
            hasFetchedFromBackendForIdentity = email
        } catch (t: Throwable) {
            emitError("Couldn't load cart from server. Showing local cart.", CartErrorEvent.Operation.GET_CART, t)
        }
    }

    /**
     * Migration behavior: sum quantities per productId between local cache and backend.
     *
     * Strategy:
     * - Fetch backend cart first
     * - Merge local cached items into backend by "add item" with merged delta
     * - Then refresh cart from backend to reconcile
     */
    private suspend fun migrateLocalCacheToBackendIfNeeded(email: String) {
        // If there's nothing local, skip.
        val local = _items.value
        if (local.isEmpty()) return

        try {
            val remoteCart = cartApi.getCurrentCart(email = email)
            val remoteMap = remoteCart.items.associateBy { it.productId }

            for (localItem in local) {
                val remoteQty = remoteMap[localItem.productId]?.quantity ?: 0
                val desired = remoteQty + localItem.quantity
                val delta = desired - remoteQty
                if (delta <= 0) continue

                cartApi.addItem(
                    email = email,
                    body = CartItemMutationRequestDto(
                        productId = localItem.productId,
                        quantity = delta,
                        price = localItem.price,
                        category = localItem.category,
                        name = localItem.name
                    )
                )
            }

            val reconciled = cartApi.getCurrentCart(email = email)
            reconcileFromCartDto(reconciled)
        } catch (t: Throwable) {
            emitError("Couldn't migrate local cart to server (offline?).", CartErrorEvent.Operation.MIGRATE_LOCAL, t)
        }
    }

    private suspend fun reconcileFromCartDto(cart: CartDto) {
        mutex.withLock {
            val mapped = cart.items.map { dto ->
                CartItem(
                    productId = dto.productId,
                    name = dto.name ?: "",
                    price = dto.price,
                    category = dto.category?.trim().orEmpty().ifBlank { "Uncategorized" },
                    quantity = dto.quantity
                )
            }
            setAndPersist(mapped)
        }
    }

    private fun setAndPersist(updated: List<CartItem>) {
        _items.value = updated
        store.save(updated)
        recomputeTotalsLockedUnsafe()
    }

    private fun recomputeTotalsLockedUnsafe() {
        // This is only called from places that already ensure serialized updates (mutex or single-thread IO).
        _totals.value = computeTotals(items = _items.value, coupon = _coupon.value)
    }

    private fun recomputeTotalsLocked() {
        _totals.value = computeTotals(items = _items.value, coupon = _coupon.value)
    }

    private fun setCouponLocked(coupon: Coupon?, state: CouponValidationState) {
        _coupon.value = coupon
        _couponValidationState.value = state
        couponStore.save(coupon)
        recomputeTotalsLocked()
    }

    private fun validateCouponAgainstLocalConstraints(coupon: Coupon, subtotal: Double): String? {
        val min = coupon.minSubtotal
        if (min != null && subtotal < min) {
            return "Minimum subtotal is $${String.format(Locale.US, "%.2f", min)}."
        }
        val expires = coupon.expiresAtEpochMillis
        if (expires != null && System.currentTimeMillis() > expires) {
            return "This coupon has expired."
        }
        return null
    }

    private suspend fun validateCouponWithBackendBestEffort(email: String, code: String): Boolean {
        /**
         * Attempt backend validation using compatibility endpoint.
         *
         * If endpoint is missing or network fails, keep PendingServerValidation (graceful fallback).
         */
        return try {
            val result = cartApi.validateCoupon(
                body = CouponRequestDto(code = code, email = email, items = buildCouponLineItemsLockedUnsafe())
            )

            if (!result.valid) {
                val friendly = CouponErrorMessageMapper.map(result.messages.firstOrNull() ?: "Coupon can't be applied.")
                mutex.withLock {
                    setCouponLocked(coupon = null, state = CouponValidationState.Invalid(friendly))
                    _lastCouponUxFeedback.value = CouponUxFeedback(
                        type = CouponUxFeedback.Type.INVALID_RULE,
                        message = friendly
                    )
                }
                false
            } else {
                // We may not have full coupon metadata in response; keep local coupon but mark valid.
                mutex.withLock {
                    _couponValidationState.value = CouponValidationState.Valid
                    recomputeTotalsLocked()
                    _lastCouponUxFeedback.value = CouponUxFeedback(
                        type = CouponUxFeedback.Type.APPLIED,
                        message = buildAppliedMessage(result)
                    )
                    val updatedSaved = savedCouponsStore.add(code)
                    _savedCoupons.value = updatedSaved
                    _couponSuggestions.value = mergeSuggestions(saved = updatedSaved, server = null)
                }
                true
            }
        } catch (_: Throwable) {
            mutex.withLock {
                if (_coupon.value != null) {
                    _couponValidationState.value = CouponValidationState.PendingServerValidation
                }
            }
            true
        }
    }

    private fun buildCouponLineItemsLockedUnsafe(): List<CartLineItemDto> {
        // Safe enough because StateFlow read is atomic; slight staleness is acceptable for coupon enforcement.
        // Backend remains source of truth and will reject if cart differs.
        val snapshot = _items.value
        if (snapshot.isEmpty()) return emptyList()

        return snapshot.mapNotNull { item ->
            val category = item.category.trim().ifBlank { "Uncategorized" }
            val qty = item.quantity
            if (qty <= 0) return@mapNotNull null

            CartLineItemDto(
                productId = item.productId,
                category = category,
                unitPrice = item.price,
                qty = qty
            )
        }
    }

    private fun String?.normalizeLower(): String = this?.trim()?.lowercase(Locale.US).orEmpty()

    private fun com.example.kotlinfrontend.network.dto.CouponDto.toDomainCouponFallback(code: String): Coupon {
        val type = when (discountType.normalizeLower()) {
            "percent", "percentage", "pct" -> DiscountType.PERCENT
            "fixed", "amount" -> DiscountType.FIXED
            else -> DiscountType.FIXED
        }
        return Coupon(
            code = code,
            description = description,
            discountType = type,
            amount = amount,
            minSubtotal = minSubtotal,
            expiresAtEpochMillis = expiresAtEpochMillis
        )
    }

    private fun normalizeCouponCode(raw: String): String {
        // Conservative normalization: trim + uppercase; keep hyphens.
        return raw.trim().uppercase(Locale.US)
    }

    private fun defaultLocalCouponForCode(code: String): Coupon {
        // Local fallback assumes a "percent" style coupon with amount=0 until validated by backend.
        // This keeps UI logic consistent while still allowing server-side override.
        // TODO: Replace with real local coupon catalog if needed.
        return Coupon(
            code = code,
            description = "Coupon applied",
            discountType = DiscountType.PERCENT,
            amount = 0.0,
            minSubtotal = null,
            expiresAtEpochMillis = null
        )
    }

    private fun computeTotals(items: List<CartItem>, coupon: Coupon?): CartTotals {
        val subtotal = items.sumOf { it.subtotal() }
        val tax = 0.0 // Placeholder

        val discount = if (coupon == null) {
            0.0
        } else {
            val rawDiscount = when (coupon.discountType) {
                DiscountType.PERCENT -> (subtotal * (coupon.amount / 100.0))
                DiscountType.FIXED -> coupon.amount
            }
            rawDiscount.coerceIn(0.0, subtotal)
        }

        val total = (subtotal - discount + tax).coerceAtLeast(0.0)
        return CartTotals(
            subtotal = subtotal,
            discount = discount,
            tax = tax,
            total = total
        )
    }

    private fun buildAppliedMessage(result: CouponResultDto): String {
        // Prefer backend messages if provided; otherwise fall back to a generic success message.
        val msg = result.messages.firstOrNull()?.trim().orEmpty()
        return if (msg.isNotBlank()) msg else "Coupon applied."
    }

    private fun mergeSuggestions(saved: List<String>, server: List<String>?): List<String> {
        // Server suggestions (if provided in future) should appear after saved ones, de-duped.
        val merged = (saved + (server ?: emptyList()))
            .mapNotNull { it.trim().takeIf { s -> s.isNotBlank() } }
            .distinctBy { it.uppercase(Locale.US) }
        return merged
    }

    private suspend fun emitError(message: String, op: CartErrorEvent.Operation, t: Throwable) {
        val detail = t.message?.takeIf { it.isNotBlank() }
        val full = if (detail != null) "$message ($detail)" else message
        _errorEvents.emit(CartErrorEvent(message = full, operation = op))
    }
}
