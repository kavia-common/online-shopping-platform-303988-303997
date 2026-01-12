package com.example.kotlinfrontend.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs for Cart backend integration.
 *
 * Note: Field names use typical camelCase JSON. If backend uses different names,
 * adjust @Json(name = "...") accordingly.
 */

@JsonClass(generateAdapter = true)
data class CartDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "userId") val userId: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "items") val items: List<CartItemDto> = emptyList(),

    // Summary/totals. Backend may provide some or all of these; null-safe.
    @Json(name = "distinctItems") val distinctItems: Int? = null,
    @Json(name = "totalQuantity") val totalQuantity: Int? = null,
    @Json(name = "subtotal") val subtotal: Double? = null,
    @Json(name = "total") val total: Double? = null
)

@JsonClass(generateAdapter = true)
data class CartItemDto(
    @Json(name = "productId") val productId: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "quantity") val quantity: Int,
    @Json(name = "price") val price: Double,
    @Json(name = "category") val category: String? = null,

    // Optional backend fields
    @Json(name = "lineTotal") val lineTotal: Double? = null
)

@JsonClass(generateAdapter = true)
data class CartItemMutationRequestDto(
    @Json(name = "productId") val productId: String,
    @Json(name = "quantity") val quantity: Int,
    @Json(name = "price") val price: Double? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class CartUpdateQuantityRequestDto(
    @Json(name = "productId") val productId: String,
    @Json(name = "quantity") val quantity: Int
)
