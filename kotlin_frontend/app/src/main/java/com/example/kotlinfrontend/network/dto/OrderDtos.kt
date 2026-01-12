package com.example.kotlinfrontend.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs for the Spring Boot order APIs.
 *
 * NOTE: Backend field names may vary slightly across implementations. These DTOs follow common
 * Spring/JPA naming patterns ("id", "status", "createdAt", etc.) and are written to be tolerant
 * of missing/nullable fields.
 */

@JsonClass(generateAdapter = true)
data class OrderItemDto(
    @Json(name = "productId")
    val productId: String? = null,

    @Json(name = "productName")
    val productName: String? = null,

    @Json(name = "unitPrice")
    val unitPrice: Double? = null,

    @Json(name = "quantity")
    val quantity: Int = 0
)

@JsonClass(generateAdapter = true)
data class OrderDto(
    @Json(name = "id")
    val id: String? = null,

    /**
     * If backend supports guest checkout, email can be included for display/filtering.
     */
    @Json(name = "email")
    val email: String? = null,

    /**
     * Common values: CREATED, PAID, SHIPPED, DELIVERED, CANCELLED
     */
    @Json(name = "status")
    val status: String? = null,

    @Json(name = "items")
    val items: List<OrderItemDto> = emptyList(),

    @Json(name = "totalAmount")
    val totalAmount: Double? = null,

    @Json(name = "createdAt")
    val createdAt: String? = null,

    @Json(name = "updatedAt")
    val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class OrderCreateItemRequestDto(
    @Json(name = "productId")
    val productId: String,

    @Json(name = "quantity")
    val quantity: Int
)

@JsonClass(generateAdapter = true)
data class OrderCreateRequestDto(
    /**
     * Optional depending on backend rules; keep nullable.
     */
    @Json(name = "email")
    val email: String? = null,

    /**
     * Optional coupon code to be validated/priced by backend consistently.
     * Backend may ignore if not supported.
     */
    @Json(name = "couponCode")
    val couponCode: String? = null,

    @Json(name = "items")
    val items: List<OrderCreateItemRequestDto>
)
