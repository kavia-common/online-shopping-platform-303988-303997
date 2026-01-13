package com.example.kotlinfrontend.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Coupon DTOs aligned with backend compatibility routes:
 * - POST /api/carts/coupon/validate
 * - POST /api/carts/coupon/apply
 * - POST /api/carts/coupon/remove
 *
 * Assumption note:
 * - Backend may use BigDecimal for money amounts; the Android client uses Double for simplicity.
 *   This is acceptable for display and typical totals, but you should prefer backend-calculated totals
 *   whenever available to avoid rounding drift.
 *
 * Security note:
 * - Only non-sensitive fields are sent (email + cart line item + coupon code). No payment/card data here.
 */

@JsonClass(generateAdapter = true)
data class CartLineItemDto(
    @Json(name = "productId")
    val productId: String,
    @Json(name = "category")
    val category: String,
    @Json(name = "unitPrice")
    val unitPrice: Double,
    @Json(name = "qty")
    val qty: Int
)

/**
 * Shared request shape for coupon validation/apply compatibility endpoints.
 *
 * Backend expects:
 * - validate/apply: { code, email, items? }
 */
@JsonClass(generateAdapter = true)
data class CouponRequestDto(
    @Json(name = "code")
    val code: String,
    @Json(name = "email")
    val email: String?,
    @Json(name = "items")
    val items: List<CartLineItemDto>? = null
)

/**
 * Remove coupon request for compatibility endpoint:
 * - remove: { email }
 */
@JsonClass(generateAdapter = true)
data class CouponRemoveRequestDto(
    @Json(name = "email")
    val email: String?
)

/**
 * Normalized response shape used by the Android app UI regardless of whether the
 * response came from validate or apply.
 *
 * Fields chosen per task requirements:
 * - valid
 * - discountAmount
 * - messages
 * - rule metadata (minSubtotal, allowedCategories, remainingUses)
 */
@JsonClass(generateAdapter = true)
data class CouponResultDto(
    @Json(name = "valid")
    val valid: Boolean,
    @Json(name = "discountAmount")
    val discountAmount: Double? = null,
    @Json(name = "messages")
    val messages: List<String> = emptyList(),
    @Json(name = "minSubtotal")
    val minSubtotal: Double? = null,
    @Json(name = "allowedCategories")
    val allowedCategories: List<String>? = null,
    @Json(name = "remainingUses")
    val remainingUses: Int? = null
)

/**
 * Legacy/optional coupon metadata returned by some endpoints.
 * Kept for backward compatibility if backend returns coupon info on validate.
 */
@JsonClass(generateAdapter = true)
data class CouponDto(
    @Json(name = "code")
    val code: String,
    @Json(name = "description")
    val description: String? = null,
    /**
     * Expected values (backend-dependent): "percent" | "fixed"
     */
    @Json(name = "discountType")
    val discountType: String,
    @Json(name = "amount")
    val amount: Double,
    @Json(name = "minSubtotal")
    val minSubtotal: Double? = null,
    /**
     * Epoch millis, optional.
     */
    @Json(name = "expiresAt")
    val expiresAtEpochMillis: Long? = null
)

/**
 * Old validate response used previously by this project.
 * Kept so we can map from it into CouponResultDto in the repository.
 *
 * Some backends return:
 * { valid, message, coupon }
 */
@JsonClass(generateAdapter = true)
data class CouponValidationResponseDto(
    @Json(name = "valid")
    val valid: Boolean,
    @Json(name = "message")
    val message: String? = null,
    @Json(name = "coupon")
    val coupon: CouponDto? = null
)
