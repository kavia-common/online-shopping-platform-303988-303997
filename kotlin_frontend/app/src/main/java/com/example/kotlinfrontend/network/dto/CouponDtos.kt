package com.example.kotlinfrontend.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CouponApplyRequestDto(
    @Json(name = "code")
    val code: String
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
