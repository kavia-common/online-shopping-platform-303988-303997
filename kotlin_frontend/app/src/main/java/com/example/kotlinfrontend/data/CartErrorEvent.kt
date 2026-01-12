package com.example.kotlinfrontend.data

/**
 * Transient cart error event for UI consumption.
 *
 * UI should display a snackbar/toast and may offer a retry action by calling repository methods again.
 */
data class CartErrorEvent(
    val message: String,
    val operation: Operation
) {
    enum class Operation {
        GET_CART,
        ADD_ITEM,
        UPDATE_QTY,
        REMOVE_ITEM,
        CLEAR_CART,
        MIGRATE_LOCAL,
        APPLY_COUPON,
        REMOVE_COUPON
    }
}
