package com.example.kotlinfrontend.model

/**
 * Known order statuses used by the backend order lifecycle.
 *
 * This app also supports UNKNOWN for forward compatibility.
 */
enum class OrderStatus {
    CREATED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    UNKNOWN;

    companion object {
        fun fromApi(value: String?): OrderStatus {
            val raw = value?.trim()?.uppercase() ?: return UNKNOWN
            return entries.firstOrNull { it.name == raw } ?: UNKNOWN
        }
    }
}
