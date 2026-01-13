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

/**
 * Notification type used for icon mapping and accessibility.
 *
 * Note: We keep this app-local and map legacy types (ORDER/CART/PROMO) to more granular types where needed.
 */
enum class NotificationType {
    ORDER_PLACED,
    ORDER_PAID,
    SHIPPED,
    DELIVERED,
    CART_REMINDER,
    GENERAL,
}

enum class NotificationDeeplink {
    ORDERS,
    CART,
    NONE
}
