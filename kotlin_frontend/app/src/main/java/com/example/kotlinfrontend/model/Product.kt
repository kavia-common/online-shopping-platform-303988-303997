package com.example.kotlinfrontend.model

/**
 * App domain model for a product.
 *
 * Matches backend fields, but also provides UI-friendly computed values.
 */
data class Product(
    val id: String,
    val name: String,
    val description: String?,
    val price: Double,
    val category: String?,
    val imageUrl: String?,
    val createdAt: String?,
    val updatedAt: String?
) {
    /**
     * Backwards compatible alias for existing UI code.
     */
    val title: String get() = name

    // PUBLIC_INTERFACE
    fun priceText(): String {
        /** Format price as currency-like string (simple $ formatting). */
        return "$" + String.format("%.2f", price)
    }
}
