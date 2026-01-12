package com.example.kotlinfrontend.model

/**
 * App domain model for an order.
 */
data class Order(
    val id: String,
    val email: String?,
    val status: OrderStatus,
    val items: List<OrderItem>,
    val totalAmount: Double?,
    val createdAt: String?,
    val updatedAt: String?
) {
    // PUBLIC_INTERFACE
    fun statusLabel(): String {
        /** Human-friendly label for chips/rows. */
        return status.name
    }
}
