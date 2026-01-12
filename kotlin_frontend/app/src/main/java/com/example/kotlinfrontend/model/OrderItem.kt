package com.example.kotlinfrontend.model

/**
 * App domain model for a single line item within an order.
 */
data class OrderItem(
    val productId: String?,
    val productName: String?,
    val unitPrice: Double?,
    val quantity: Int
)
