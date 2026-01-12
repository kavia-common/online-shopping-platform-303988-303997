package com.example.kotlinfrontend.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs for the Spring Boot product-api.
 *
 * Field names are kept consistent with backend JSON.
 */

@JsonClass(generateAdapter = true)
data class ProductDto(
    @Json(name = "id")
    val id: String? = null,

    @Json(name = "name")
    val name: String,

    @Json(name = "description")
    val description: String? = null,

    /**
     * Price is assumed to be a decimal number in backend JSON (e.g., 12.99).
     */
    @Json(name = "price")
    val price: Double,

    @Json(name = "category")
    val category: String? = null,

    @Json(name = "imageUrl")
    val imageUrl: String? = null,

    /**
     * Typically ISO-8601 strings (e.g., "2026-01-01T12:34:56Z").
     */
    @Json(name = "createdAt")
    val createdAt: String? = null,

    @Json(name = "updatedAt")
    val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ProductCreateRequestDto(
    @Json(name = "name")
    val name: String,

    @Json(name = "description")
    val description: String? = null,

    @Json(name = "price")
    val price: Double,

    @Json(name = "category")
    val category: String? = null,

    @Json(name = "imageUrl")
    val imageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class ProductUpdateRequestDto(
    @Json(name = "name")
    val name: String,

    @Json(name = "description")
    val description: String? = null,

    @Json(name = "price")
    val price: Double,

    @Json(name = "category")
    val category: String? = null,

    @Json(name = "imageUrl")
    val imageUrl: String? = null
)

/**
 * Generic Spring Data Page response (minimal subset needed by PagingSource).
 *
 * Spring's default JSON includes fields like:
 * - content: [...]
 * - number: current page index (0-based)
 * - size: page size
 * - totalElements: total count
 * - totalPages: total pages
 * - last: bool
 */
@JsonClass(generateAdapter = true)
data class PageResponseDto<T>(
    @Json(name = "content")
    val content: List<T> = emptyList(),

    @Json(name = "number")
    val number: Int = 0,

    @Json(name = "size")
    val size: Int = 0,

    @Json(name = "totalElements")
    val totalElements: Long = 0,

    @Json(name = "totalPages")
    val totalPages: Int = 0,

    @Json(name = "last")
    val last: Boolean = false
)
