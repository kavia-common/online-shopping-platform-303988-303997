package com.example.kotlinfrontend.model

/**
 * A derived model for cart totals used by the UI.
 */
data class CartSummary(
    val distinctItems: Int,
    val totalQuantity: Int,
    val subtotal: Double
)
