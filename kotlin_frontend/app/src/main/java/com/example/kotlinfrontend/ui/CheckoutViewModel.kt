package com.example.kotlinfrontend.ui

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.kotlinfrontend.data.AppRepositories
import com.example.kotlinfrontend.data.CouponUxFeedback
import com.example.kotlinfrontend.data.CouponValidationState
import com.example.kotlinfrontend.data.DemoNotificationsEngine
import com.example.kotlinfrontend.data.OrderRepository
import com.example.kotlinfrontend.data.PaymentPrefs
import com.example.kotlinfrontend.model.CartItem
import com.example.kotlinfrontend.model.CartTotals
import com.example.kotlinfrontend.model.Coupon
import com.example.kotlinfrontend.model.PaymentMethod
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

/**
 * ViewModel for the checkout screen.
 *
 * Holds all form state (rotation-safe via SavedStateHandle) and performs a simulated payment step
 * before creating the order.
 *
 * Payment card inputs:
 * - card number is validated by Luhn and formatted for readability (spaces)
 * - expiry is parsed as MM/YY and validated to be current/future
 * - CVC length varies by brand (Amex=4, others=3)
 * - only non-sensitive metadata may be attached to order payload (brand id, last4 derived in-memory)
 */
class CheckoutViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val cartRepo = AppRepositories.cart(application)
    private val orderRepo = OrderRepository()

    data class FormState(
        val customerName: String = "",
        val email: String = "",
        val addressLine1: String = "",
        val city: String = "",
        val state: String = "",
        val postalCode: String = "",
        val country: String = "",
        val note: String = "",
        val mockPaymentSuccess: Boolean = true,
        val couponInput: String = "",
        val selectedPaymentMethodId: String = PaymentMethod.Card.ID,

        // Card inputs (Card method only). These are kept in-memory + SavedStateHandle for rotation.
        // Security: do NOT persist across app restarts; do NOT log these values.
        val cardNumber: String = "",
        val expiry: String = "",
        val cvc: String = "",
        val nameOnCard: String = "",
        val zip: String = ""
    )

    data class FieldErrors(
        val customerName: String? = null,
        val email: String? = null,
        val addressLine1: String? = null,
        val city: String? = null,
        val state: String? = null,
        val postalCode: String? = null,
        val country: String? = null
    )

    data class CardFieldErrors(
        val cardNumber: String? = null,
        val expiry: String? = null,
        val cvc: String? = null,
        val nameOnCard: String? = null,
        val zip: String? = null
    )

    data class PaymentMetadata(
        val cardBrandId: String? = null,
        val cardLast4: String? = null
    )

    sealed class PaymentState {
        data object Idle : PaymentState()
        data object Processing : PaymentState()

        /**
         * Payment was declined/failed in a non-network way. Show inline message and allow retry.
         */
        data class Declined(val message: String) : PaymentState()

        /**
         * Payment succeeded (simulated). We keep the reference for passing to order create.
         */
        data class Succeeded(val paymentReference: String) : PaymentState()
    }

    sealed class SubmitState {
        data object Idle : SubmitState()
        data object Loading : SubmitState()
        data class Success(val orderId: String, val total: Double) : SubmitState()
        data class Error(
            val message: String,
            val fieldErrors: FieldErrors = FieldErrors()
        ) : SubmitState()
    }

    private val _form = MutableStateFlow(
        savedStateHandle.get<FormState>(KEY_FORM) ?: initialFormState()
    )
    val form: StateFlow<FormState> = _form.asStateFlow()

    private val _fieldErrors = MutableStateFlow(FieldErrors())
    val fieldErrors: StateFlow<FieldErrors> = _fieldErrors.asStateFlow()

    private val _cardErrors = MutableStateFlow(CardFieldErrors())
    val cardErrors: StateFlow<CardFieldErrors> = _cardErrors.asStateFlow()

    private val _derivedCardBrand = MutableStateFlow(PaymentCardUtils.CardBrand.Unknown)
    val derivedCardBrand: StateFlow<PaymentCardUtils.CardBrand> = _derivedCardBrand.asStateFlow()

    private val _paymentMetadata = MutableStateFlow(PaymentMetadata())
    val paymentMetadata: StateFlow<PaymentMetadata> = _paymentMetadata.asStateFlow()

    private val _isPaymentSectionValid = MutableStateFlow(false)
    val isPaymentSectionValid: StateFlow<Boolean> = _isPaymentSectionValid.asStateFlow()

    private val _canPlaceOrder = MutableStateFlow(false)
    val canPlaceOrder: StateFlow<Boolean> = _canPlaceOrder.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState.asStateFlow()

    val cartItems: StateFlow<List<CartItem>> = cartRepo.items

    val coupon: StateFlow<Coupon?> = cartRepo.coupon
    val couponValidationState: StateFlow<CouponValidationState> = cartRepo.couponValidationState
    val lastCouponUxFeedback: StateFlow<CouponUxFeedback> = cartRepo.lastCouponUxFeedback

    val savedCoupons: StateFlow<List<String>> = cartRepo.savedCoupons
    val couponSuggestions: StateFlow<List<String>> = cartRepo.couponSuggestions

    val totals: StateFlow<CartTotals> = cartRepo.totals

    // PUBLIC_INTERFACE
    fun prefillEmailIfAvailable() {
        /** Prefills email from Cart identity (if present and form email is blank). */
        val identityEmail = cartRepo.activeEmail.value
        if (!identityEmail.isNullOrBlank() && _form.value.email.isBlank()) {
            updateEmail(identityEmail)
        }
    }

    // PUBLIC_INTERFACE
    fun applyLastPaymentMethodIfAvailable() {
        /** Loads last used payment method from SharedPreferences and applies it to form state. */
        val last = PaymentPrefs.getLastPaymentMethod(getApplication()) ?: return
        updatePaymentMethod(last.id, persistAsLastUsed = false)
    }

    // PUBLIC_INTERFACE
    fun updateCustomerName(value: String) {
        /** Update customer name field. */
        setForm(_form.value.copy(customerName = value))
    }

    // PUBLIC_INTERFACE
    fun updateEmail(value: String) {
        /** Update email field. */
        setForm(_form.value.copy(email = value))
    }

    // PUBLIC_INTERFACE
    fun updateAddressLine1(value: String) {
        /** Update shipping address line1 field. */
        setForm(_form.value.copy(addressLine1 = value))
    }

    // PUBLIC_INTERFACE
    fun updateCity(value: String) {
        /** Update shipping city field. */
        setForm(_form.value.copy(city = value))
    }

    // PUBLIC_INTERFACE
    fun updateState(value: String) {
        /** Update shipping state field. */
        setForm(_form.value.copy(state = value))
    }

    // PUBLIC_INTERFACE
    fun updatePostalCode(value: String) {
        /** Update shipping postal code field. */
        setForm(_form.value.copy(postalCode = value))
    }

    // PUBLIC_INTERFACE
    fun updateCountry(value: String) {
        /** Update shipping country field. */
        setForm(_form.value.copy(country = value))
    }

    // PUBLIC_INTERFACE
    fun updateNote(value: String) {
        /** Update optional note field. */
        setForm(_form.value.copy(note = value))
    }

    // PUBLIC_INTERFACE
    fun setMockPaymentSuccess(enabled: Boolean) {
        /** Toggle for simulated payment approval/decline. */
        setForm(_form.value.copy(mockPaymentSuccess = enabled))
        // If user switches to success, clear the inline declined state to reduce confusion.
        if (enabled && _paymentState.value is PaymentState.Declined) {
            _paymentState.value = PaymentState.Idle
        }
    }

    // PUBLIC_INTERFACE
    fun updatePaymentMethod(methodId: String, persistAsLastUsed: Boolean = true) {
        /** Update selected payment method and optionally persist as last-used. */
        setForm(_form.value.copy(selectedPaymentMethodId = methodId))
        if (persistAsLastUsed) {
            PaymentMethod.fromId(methodId)?.let { PaymentPrefs.setLastPaymentMethod(getApplication(), it) }
        }
        // Switching method clears any previous decline message.
        if (_paymentState.value is PaymentState.Declined) {
            _paymentState.value = PaymentState.Idle
        }

        // When leaving Card method, clear card errors; when entering, validate.
        if (methodId != PaymentMethod.Card.ID) {
            _cardErrors.value = CardFieldErrors()
        }
        validatePaymentSection(live = true)
        recomputeCanPlaceOrder()
    }

    private fun recomputeCanPlaceOrder() {
        // Enablement is computed from: cart not empty + shipping fields valid + payment section valid + not submitting.
        val cartOk = cartRepo.items.value.isNotEmpty()
        val shippingOk = validate(_form.value) == FieldErrors()

        val paymentOk = when (_form.value.selectedPaymentMethodId) {
            PaymentMethod.Card.ID -> _isPaymentSectionValid.value
            else -> true // Wallet/COD have no additional required inputs currently
        }

        val notLoading = _submitState.value !is SubmitState.Loading
        _canPlaceOrder.value = cartOk && shippingOk && paymentOk && notLoading
    }

    private fun validatePaymentSection(live: Boolean) {
        val form = _form.value
        if (form.selectedPaymentMethodId != PaymentMethod.Card.ID) {
            _isPaymentSectionValid.value = true
            _cardErrors.value = CardFieldErrors()
            return
        }

        val numberDigits = PaymentCardUtils.digitsOnly(form.cardNumber)
        val brand = PaymentCardUtils.detectBrand(numberDigits)
        _derivedCardBrand.value = brand

        val numberError = when {
            numberDigits.isBlank() -> if (live) "Required" else "Required"
            numberDigits.length < 12 -> if (live) "Enter a valid card number" else "Enter a valid card number"
            !PaymentCardUtils.luhnValid(numberDigits) -> "Card number is invalid"
            else -> null
        }

        val expiryParsed = PaymentCardUtils.parseExpiry(form.expiry)
        val expiryError = when {
            PaymentCardUtils.digitsOnly(form.expiry).isBlank() -> "Required"
            expiryParsed == null -> "Use MM/YY"
            !PaymentCardUtils.isExpiryValidAndNotPast(expiryParsed) -> "Card is expired"
            else -> null
        }

        val cvcDigits = PaymentCardUtils.digitsOnly(form.cvc)
        val requiredCvcLen = PaymentCardUtils.maxCvcLengthForBrand(brand)
        val cvcError = when {
            cvcDigits.isBlank() -> "Required"
            cvcDigits.length < requiredCvcLen -> "CVC must be $requiredCvcLen digits"
            else -> null
        }

        val nameError = if (form.nameOnCard.trim().isBlank()) "Required" else null

        val zipError = if (!PaymentCardUtils.isZipPlausible(form.zip)) "Enter a valid ZIP" else null

        val errors = CardFieldErrors(
            cardNumber = numberError,
            expiry = expiryError,
            cvc = cvcError,
            nameOnCard = nameError,
            zip = zipError
        )

        _cardErrors.value = errors
        _isPaymentSectionValid.value = errors == CardFieldErrors()
    }

    // PUBLIC_INTERFACE
    fun updateCouponInput(value: String) {
        /** Update the editable coupon input field (does not apply). */
        setForm(_form.value.copy(couponInput = value))
    }

    // PUBLIC_INTERFACE
    fun updateCardNumber(rawUserInput: String) {
        /**
         * Updates card number with live formatting. Also updates derived brand + last4 metadata
         * (in-memory only).
         */
        val formatted = PaymentCardUtils.formatCardNumber(rawUserInput)
        val digits = PaymentCardUtils.digitsOnly(formatted)
        val brand = PaymentCardUtils.detectBrand(digits)

        setForm(_form.value.copy(cardNumber = formatted))

        _derivedCardBrand.value = brand
        _paymentMetadata.value = _paymentMetadata.value.copy(
            cardBrandId = brand.id.takeIf { it != PaymentCardUtils.CardBrand.Unknown.id },
            cardLast4 = PaymentCardUtils.last4(digits)
        )

        validatePaymentSection(live = true)

        // Persist last valid brand (non-sensitive) once the card looks valid.
        if (PaymentCardUtils.luhnValid(digits)) {
            PaymentPrefs.setLastValidCardBrandId(getApplication(), brand.id)
        }
    }

    // PUBLIC_INTERFACE
    fun updateExpiry(rawUserInput: String) {
        /** Updates expiry with live formatting (MM/YY). */
        val formatted = PaymentCardUtils.formatExpiry(rawUserInput)
        setForm(_form.value.copy(expiry = formatted))
        validatePaymentSection(live = true)
    }

    // PUBLIC_INTERFACE
    fun updateCvc(rawUserInput: String) {
        /** Updates CVC with digits-only. Length validation depends on derived brand. */
        val maxLen = PaymentCardUtils.maxCvcLengthForBrand(_derivedCardBrand.value)
        val digits = PaymentCardUtils.digitsOnly(rawUserInput).take(maxLen)
        setForm(_form.value.copy(cvc = digits))
        validatePaymentSection(live = true)
    }

    // PUBLIC_INTERFACE
    fun updateNameOnCard(value: String) {
        /** Updates name on card (required for Card method). */
        setForm(_form.value.copy(nameOnCard = value))
        validatePaymentSection(live = true)
    }

    // PUBLIC_INTERFACE
    fun updateZip(value: String) {
        /** Updates optional ZIP/postal code for card. */
        val normalized = PaymentCardUtils.normalizeZip(value)
        setForm(_form.value.copy(zip = normalized))
        validatePaymentSection(live = true)
    }

    // PUBLIC_INTERFACE
    fun applyCouponFromCheckout() {
        /** Apply coupon using current coupon input value. */
        cartRepo.applyCoupon(_form.value.couponInput)
    }

    // PUBLIC_INTERFACE
    fun removeCoupon() {
        /** Remove applied coupon. */
        cartRepo.removeCoupon()
    }

    // PUBLIC_INTERFACE
    fun addSavedCoupon(code: String) {
        /** Add coupon code to saved list. */
        cartRepo.addSavedCoupon(code)
    }

    // PUBLIC_INTERFACE
    fun removeSavedCoupon(code: String) {
        /** Remove coupon code from saved list. */
        cartRepo.removeSavedCoupon(code)
    }

    // PUBLIC_INTERFACE
    fun clearSavedCoupons() {
        /** Clear all saved coupon codes. */
        cartRepo.clearSavedCoupons()
    }

    // PUBLIC_INTERFACE
    fun isCouponSaved(code: String): Boolean {
        /** Returns true if coupon is saved (case-insensitive). */
        return cartRepo.isCouponSaved(code)
    }

    // PUBLIC_INTERFACE
    fun clearSubmitError() {
        /** Clear transient submission error state, keeping form state intact. */
        if (_submitState.value is SubmitState.Error) {
            _submitState.value = SubmitState.Idle
        }
    }

    // PUBLIC_INTERFACE
    fun placeOrder() {
        /**
         * Validates the form, runs a simulated payment processing step, then maps cart items ->
         * backend Order create request, calls API, and clears cart on success.
         *
         * Network errors are reported via snackbars (SubmitState.Error), while payment declines are
         * reported inline via PaymentState.Declined.
         *
         * Card method: blocks submission unless card inputs are valid.
         * Security: only brand + last4 (derived in-memory) are attached as non-sensitive metadata.
         */
        val items = cartRepo.items.value
        if (items.isEmpty()) {
            _submitState.value = SubmitState.Error(message = "Your cart is empty.")
            recomputeCanPlaceOrder()
            return
        }

        val form = _form.value
        val errors = validate(form)

        // Payment validation for Card
        validatePaymentSection(live = false)
        val paymentOk = when (form.selectedPaymentMethodId) {
            PaymentMethod.Card.ID -> _isPaymentSectionValid.value
            else -> true
        }

        if (errors != FieldErrors() || !paymentOk) {
            _fieldErrors.value = errors

            val message = if (!paymentOk && form.selectedPaymentMethodId == PaymentMethod.Card.ID) {
                "Please fix the card details."
            } else {
                "Please fix the highlighted fields."
            }

            _submitState.value = SubmitState.Error(
                message = message,
                fieldErrors = errors
            )
            recomputeCanPlaceOrder()
            return
        }

        // Reset error holders before starting any async work.
        _fieldErrors.value = FieldErrors()
        _submitState.value = SubmitState.Loading
        _paymentState.value = PaymentState.Processing
        recomputeCanPlaceOrder()

        viewModelScope.launch {
            // Step 1) Payment processing (simulated for now)
            val paymentResult = runPaymentSimulation(form)

            when (paymentResult) {
                is PaymentState.Declined -> {
                    _paymentState.value = paymentResult
                    _submitState.value = SubmitState.Idle
                    recomputeCanPlaceOrder()
                    return@launch
                }
                is PaymentState.Succeeded -> {
                    _paymentState.value = paymentResult
                }
                else -> {
                    // Defensive: treat unknown state as failure.
                    _paymentState.value = PaymentState.Declined("Payment could not be processed. Please try again.")
                    _submitState.value = SubmitState.Idle
                    recomputeCanPlaceOrder()
                    return@launch
                }
            }

            // Step 2) Create order
            try {
                val pairs = items.map { it.productId to it.quantity }
                val couponCode = cartRepo.coupon.value?.code

                val pm = PaymentMethod.fromId(form.selectedPaymentMethodId)
                val paymentMethodId = pm?.id

                // Attach non-sensitive metadata to paymentReference for demo only.
                // The backend dto supports only paymentReference string currently.
                // Example: "mock-abcdef123456|brand=visa|last4=4242"
                val basePaymentRef = (paymentState.value as? PaymentState.Succeeded)?.paymentReference
                val meta = paymentMetadata.value
                val metaSuffix = buildString {
                    if (!meta.cardBrandId.isNullOrBlank()) append("|brand=").append(meta.cardBrandId)
                    if (!meta.cardLast4.isNullOrBlank()) append("|last4=").append(meta.cardLast4)
                }
                val paymentRef = (basePaymentRef ?: "").takeIf { it.isNotBlank() }?.plus(metaSuffix) ?: basePaymentRef

                val created = orderRepo.createOrder(
                    email = form.email,
                    items = pairs,
                    couponCode = couponCode,
                    paymentMethod = paymentMethodId,
                    paymentReference = paymentRef
                )

                // Clear local cart after successful order creation.
                cartRepo.clear()

                val orderId = created.id
                val total = cartRepo.discountedTotals().total.takeIf { it > 0.0 } ?: (created.totalAmount ?: 0.0)
                _submitState.value = SubmitState.Success(orderId = orderId, total = total)

                // Demo notifications: schedule local lifecycle events after successful placement.
                // (Frontend-only; will no-op if demo is disabled.)
                DemoNotificationsEngine(getApplication()).scheduleOrderLifecycleAfterPlacement(orderId = orderId)
            } catch (t: Throwable) {
                _submitState.value = mapError(t)
            } finally {
                recomputeCanPlaceOrder()
                // Keep payment state as-is so the UI can show the last outcome.
                // (Success navigates away immediately anyway.)
            }
        }
    }

    private fun initialFormState(): FormState {
        // Prefer last used method if available, otherwise default.
        val last = PaymentPrefs.getLastPaymentMethod(getApplication())
        return FormState(
            selectedPaymentMethodId = last?.id ?: PaymentMethod.Card.ID
        )
    }

    private fun setForm(newForm: FormState) {
        _form.value = newForm
        savedStateHandle[KEY_FORM] = newForm

        // As user types, clear field errors for friendlier UX.
        if (_fieldErrors.value != FieldErrors()) {
            _fieldErrors.value = FieldErrors()
        }
        // Likewise clear card errors while typing; validatePaymentSection() will repopulate as needed.
        if (_cardErrors.value != CardFieldErrors()) {
            _cardErrors.value = CardFieldErrors()
        }

        recomputeCanPlaceOrder()
    }

    private fun validate(form: FormState): FieldErrors {
        val customerNameError = if (form.customerName.trim().isBlank()) "Required" else null

        val email = form.email.trim()
        val emailError = when {
            email.isBlank() -> "Required"
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Invalid email"
            else -> null
        }

        fun req(s: String): String? = if (s.trim().isBlank()) "Required" else null

        return FieldErrors(
            customerName = customerNameError,
            email = emailError,
            addressLine1 = req(form.addressLine1),
            city = req(form.city),
            state = req(form.state),
            postalCode = req(form.postalCode),
            country = req(form.country)
        )
    }

    private suspend fun runPaymentSimulation(form: FormState): PaymentState {
        // A small delay to show progress.
        delay(900)

        // If user selected Cash on Delivery, treat as "approved" without decline.
        if (form.selectedPaymentMethodId == PaymentMethod.CashOnDelivery.ID) {
            return PaymentState.Succeeded(paymentReference = "cod-" + UUID.randomUUID().toString().take(8))
        }

        // For other methods, use the mock toggle to simulate approval/decline.
        if (!form.mockPaymentSuccess) {
            return PaymentState.Declined("Transaction declined. Please try another payment method or retry.")
        }

        return PaymentState.Succeeded(paymentReference = "mock-" + UUID.randomUUID().toString().take(12))
    }

    private fun mapError(t: Throwable): SubmitState.Error {
        return when (t) {
            is IOException -> SubmitState.Error("Network error. Check your connection and try again.")
            is HttpException -> {
                val body = try {
                    t.response()?.errorBody()?.string()
                } catch (_: Throwable) {
                    null
                }

                val msg = when {
                    !body.isNullOrBlank() -> "Server error (${t.code()}): ${body.take(160)}"
                    else -> "Server error (${t.code()}). Please try again."
                }

                SubmitState.Error(message = msg)
            }
            else -> SubmitState.Error(t.message ?: "Something went wrong. Please try again.")
        }
    }

    private companion object {
        private const val KEY_FORM = "checkout_form_state"
    }
}
