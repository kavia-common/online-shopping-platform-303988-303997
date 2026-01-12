package com.example.kotlinfrontend.model

/**
 * A derived model for cart totals including discount.
 *
 * - `tax` is a placeholder for future enhancement (kept for UI completeness).
 * - All values are in the same currency as product prices.
 */
data class CartTotals(
    val subtotal: Double,
    val discount: Double,
    val tax: Double,
    val total: Double
)
