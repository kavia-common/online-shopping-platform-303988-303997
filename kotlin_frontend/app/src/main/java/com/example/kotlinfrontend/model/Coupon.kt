package com.example.kotlinfrontend.model

/**
 * Coupon/discount model for the cart.
 *
 * Notes:
 * - `discountType` controls how `amount` is interpreted (percent vs fixed currency).
 * - `minSubtotal` and `expiresAtEpochMillis` are optional constraints; repository uses them if present.
 */
data class Coupon(
    val code: String,
    val description: String? = null,
    val discountType: DiscountType,
    val amount: Double,
    val minSubtotal: Double? = null,
    val expiresAtEpochMillis: Long? = null
)

enum class DiscountType {
    PERCENT,
    FIXED
}
