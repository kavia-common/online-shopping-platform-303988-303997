package com.example.kotlinfrontend.model

/**
 * Simple filter state for the product list.
 *
 * Keep this model UI-agnostic so it can be passed down to repository/paging source and later mapped
 * directly to API query params.
 */
data class ProductFilter(
    val category: String? = null,
    val minPriceCents: Int? = null,
    val maxPriceCents: Int? = null
) {
    fun isActive(): Boolean = category != null || minPriceCents != null || maxPriceCents != null
}
