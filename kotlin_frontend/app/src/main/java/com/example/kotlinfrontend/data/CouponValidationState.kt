package com.example.kotlinfrontend.data

/**
 * Coupon validation status for UI.
 */
sealed class CouponValidationState {
    data object None : CouponValidationState()
    data object PendingServerValidation : CouponValidationState()
    data object Valid : CouponValidationState()
    data class Invalid(val message: String) : CouponValidationState()
}
