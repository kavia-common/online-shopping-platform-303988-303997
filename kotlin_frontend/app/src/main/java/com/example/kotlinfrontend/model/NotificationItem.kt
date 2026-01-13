package com.example.kotlinfrontend.model

/**
 * Domain model for an in-app notification.
 */
data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val createdAtMillis: Long,
    val read: Boolean,
    val deeplink: NotificationDeeplink
)

enum class NotificationType {
    ORDER,
    CART,
    PROMO
}

enum class NotificationDeeplink {
    ORDERS,
    CART,
    NONE
}
