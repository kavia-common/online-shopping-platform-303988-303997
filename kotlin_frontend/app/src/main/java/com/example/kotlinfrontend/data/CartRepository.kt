package com.example.kotlinfrontend.data

import android.content.Context
import com.example.kotlinfrontend.model.CartItem
import com.example.kotlinfrontend.model.CartSummary
import com.example.kotlinfrontend.model.Product
import com.example.kotlinfrontend.network.ApiClient
import com.example.kotlinfrontend.network.CartApi
import com.example.kotlinfrontend.network.dto.CartDto
import com.example.kotlinfrontend.network.dto.CartItemMutationRequestDto
import com.example.kotlinfrontend.network.dto.CartUpdateQuantityRequestDto
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

/**
 * Repository for cart state and persistence.
 *
 * Backend-synced mode:
 * - A single MutableStateFlow is the source of truth for in-memory UI.
 * - We optimistically update UI, persist to local cache, then attempt network sync.
 * - On network failure, we keep local state and emit error events.
 *
 * Identity:
 * - Backend cart is scoped by an "active email". This is stored in shared prefs.
 * - When identity is first set, we migrate existing local items to backend (sum quantities per productId).
 */
class CartRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = CartStore(appContext)
    private val identityStore = CartIdentityStore(appContext)

    private val cartApi: CartApi = ApiClient.createCartApi()

    /**
     * Single source of truth in-memory (thread-safe via mutex around compound operations).
     */
    private val _items = MutableStateFlow<List<CartItem>>(store.load())
    val items: StateFlow<List<CartItem>> = _items.asStateFlow()

    private val _activeEmail = MutableStateFlow<String?>(identityStore.getEmail())
    val activeEmail: StateFlow<String?> = _activeEmail.asStateFlow()

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
     *
     * This is best-effort and only runs once per identity set.
     */
    private suspend fun migrateLocalCacheToBackendIfNeeded(email: String) {
        // If there's nothing local, skip.
        val local = _items.value
        if (local.isEmpty()) return

        try {
            val remoteCart = cartApi.getCurrentCart(email = email)
            val remoteMap = remoteCart.items.associateBy { it.productId }

            // For each local item, compute desired final quantity = local + remote.
            // We'll "add" only the delta relative to remote (since we don't know if backend has
            // a merge-vs-set semantics for add). If remote absent, delta = local qty.
            for (localItem in local) {
                val remoteQty = remoteMap[localItem.productId]?.quantity ?: 0
                val desired = remoteQty + localItem.quantity
                val delta = desired - remoteQty
                if (delta <= 0) continue

                // Attempt to add delta.
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

            // After migration, reconcile from backend.
            val reconciled = cartApi.getCurrentCart(email = email)
            reconcileFromCartDto(reconciled)

            // Since backend is now source of truth, the local cache should mirror it.
            // (reconcileFromCartDto already saves)
        } catch (t: Throwable) {
            emitError("Couldn't migrate local cart to server (offline?).", CartErrorEvent.Operation.MIGRATE_LOCAL, t)
        }
    }

    private suspend fun reconcileFromCartDto(cart: CartDto) {
        mutex.withLock {
            val mapped = cart.items.map { dto ->
                CartItem(
                    productId = dto.productId,
                    name = dto.name ?: "", // backend may omit name; keep non-null
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
    }

    private suspend fun emitError(message: String, op: CartErrorEvent.Operation, t: Throwable) {
        // Avoid overly technical messages; include detail only if present.
        val detail = t.message?.takeIf { it.isNotBlank() }
        val full = if (detail != null) "$message ($detail)" else message
        _errorEvents.emit(CartErrorEvent(message = full, operation = op))
    }
}
