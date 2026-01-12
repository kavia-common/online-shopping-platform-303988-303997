package com.example.kotlinfrontend.model

/**
 * App domain model representing an item in the shopping cart.
 *
 * Category is stored so the cart can be grouped by category even after process death.
 */
data class CartItem(
    val productId: String,
    val name: String,
    val price: Double,
    val category: String,
    val quantity: Int
) {
    init {
        require(productId.isNotBlank()) { "productId cannot be blank" }
        require(category.isNotBlank()) { "category cannot be blank" }
        require(quantity >= 0) { "quantity cannot be negative" }
    }

    // PUBLIC_INTERFACE
    fun subtotal(): Double {
        /** Returns price * quantity. */
        return price * quantity
    }
}
