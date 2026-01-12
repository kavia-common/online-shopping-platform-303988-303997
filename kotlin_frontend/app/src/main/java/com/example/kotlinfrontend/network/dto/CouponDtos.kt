package com.example.kotlinfrontend.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Coupon DTOs.
 *
 * Note: The backend now supports sending cart line items for coupon validation/apply/remove
 * so it can enforce min subtotal, category eligibility, and usage limits.
 *
 * We keep all new fields optional to remain compatible with older backends that ignore them.
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

@JsonClass(generateAdapter = true)
data class CouponApplyRequestDto(
    @Json(name = "code")
    val code: String,
    /**
     * Optional line items so backend can compute eligible subtotal by category
     * and enforce coupon rules.
     */
    @Json(name = "items")
    val items: List<CartLineItemDto>? = null
)

@JsonClass(generateAdapter = true)
data class CouponRemoveRequestDto(
    /**
     * Optional (some backends support removal by just email/cart identity and ignore the body).
     * Keeping it nullable to support DELETE with/without body depending on backend routing.
     */
    @Json(name = "code")
    val code: String? = null,
    @Json(name = "items")
    val items: List<CartLineItemDto>? = null
)

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

@JsonClass(generateAdapter = true)
data class CouponValidationResponseDto(
    @Json(name = "valid")
    val valid: Boolean,
    @Json(name = "message")
    val message: String? = null,
    @Json(name = "coupon")
    val coupon: CouponDto? = null
)
