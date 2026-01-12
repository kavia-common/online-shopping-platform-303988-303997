package org.example.app.data

/**
 * Simple catalog category model.
 */
data class Category(
    val id: String,
    val name: String
)

/**
 * Simple product model for mock catalog.
 */
data class Product(
    val id: String,
    val name: String,
    val categoryId: String,
    val priceCents: Int,
    val description: String
) {
    fun formattedPrice(): String = "$" + String.format("%.2f", priceCents / 100.0)
}
