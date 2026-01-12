package com.example.kotlinfrontend.ui

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.kotlinfrontend.data.AppRepositories
import com.example.kotlinfrontend.data.OrderRepository
import com.example.kotlinfrontend.model.CartItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * ViewModel for the checkout screen.
 *
 * Holds all form state (rotation-safe via SavedStateHandle) and performs the place-order call
 * with lifecycle-safe coroutines (viewModelScope).
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
        val mockPaymentSuccess: Boolean = true
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
        savedStateHandle.get<FormState>(KEY_FORM) ?: FormState()
    )
    val form: StateFlow<FormState> = _form.asStateFlow()

    private val _fieldErrors = MutableStateFlow(FieldErrors())
    val fieldErrors: StateFlow<FieldErrors> = _fieldErrors.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    val cartItems: StateFlow<List<CartItem>> = cartRepo.items

    // PUBLIC_INTERFACE
    fun prefillEmailIfAvailable() {
        /** Prefills email from Cart identity (if present and form email is blank). */
        val identityEmail = cartRepo.activeEmail.value
        if (!identityEmail.isNullOrBlank() && _form.value.email.isBlank()) {
            updateEmail(identityEmail)
        }
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
        /** Placeholder payment toggle. If disabled, submission is blocked with inline error. */
        setForm(_form.value.copy(mockPaymentSuccess = enabled))
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
         * Validates the form, maps cart items -> backend Order create request, calls API, and clears cart on success.
         */
        val items = cartRepo.items.value
        if (items.isEmpty()) {
            _submitState.value = SubmitState.Error(message = "Your cart is empty.")
            return
        }

        val form = _form.value
        val errors = validate(form)

        if (errors != FieldErrors()) {
            _fieldErrors.value = errors
            _submitState.value = SubmitState.Error(
                message = "Please fix the highlighted fields.",
                fieldErrors = errors
            )
            return
        }

        if (!form.mockPaymentSuccess) {
            _submitState.value = SubmitState.Error(
                message = "Payment failed (mock). Enable “Mock payment success” to continue."
            )
            return
        }

        _fieldErrors.value = FieldErrors()
        _submitState.value = SubmitState.Loading

        viewModelScope.launch {
            try {
                // Keep using existing DTOs: OrderCreateRequestDto only supports email + items.
                // (Address fields are captured in UI for later enhancement; not sent yet.)
                val pairs = items.map { it.productId to it.quantity }
                val created = orderRepo.createOrder(email = form.email, items = pairs)

                // Clear local cart after successful order creation.
                cartRepo.clear()

                val orderId = created.id
                val total = cartRepo.summary().subtotal.takeIf { it > 0.0 } ?: (created.totalAmount ?: 0.0)
                _submitState.value = SubmitState.Success(orderId = orderId, total = total)
            } catch (t: Throwable) {
                _submitState.value = mapError(t)
            }
        }
    }

    private fun setForm(newForm: FormState) {
        _form.value = newForm
        savedStateHandle[KEY_FORM] = newForm
        // As user types, clear field errors for friendlier UX.
        if (_fieldErrors.value != FieldErrors()) {
            _fieldErrors.value = FieldErrors()
        }
    }

    private fun validate(form: FormState): FieldErrors {
        // Customer name: required by requirements (even if not used in backend payload yet).
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

    /**
     * Maps known networking errors into user-friendly messages.
     *
     * We also attempt a best-effort extraction of field-level errors if backend returns them as JSON,
     * but we avoid tight coupling to a specific backend error schema.
     */
    private fun mapError(t: Throwable): SubmitState.Error {
        return when (t) {
            is IOException -> SubmitState.Error("Network error. Check your connection and try again.")
            is HttpException -> {
                // Try to extract a readable server message.
                val body = try {
                    t.response()?.errorBody()?.string()
                } catch (_: Throwable) {
                    null
                }

                // Best-effort: if server returns a JSON like {"message":"..."} show it; otherwise generic.
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
