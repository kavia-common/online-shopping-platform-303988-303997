package com.example.kotlinfrontend.network.dto

import com.example.kotlinfrontend.model.NotificationDeeplink
import com.example.kotlinfrontend.model.NotificationItem
import com.example.kotlinfrontend.model.NotificationType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

object NotificationsDtos {

    @JsonClass(generateAdapter = true)
    data class NotificationDto(
        @Json(name = "id") val id: String?,
        @Json(name = "type") val type: String?,
        @Json(name = "title") val title: String?,
        @Json(name = "message") val message: String?,
        @Json(name = "createdAtMillis") val createdAtMillis: Long?,
        @Json(name = "read") val read: Boolean?,
        @Json(name = "deeplink") val deeplink: String?
    )

    @JsonClass(generateAdapter = true)
    data class MarkReadRequest(
        @Json(name = "id") val id: String
    )
}

/**
 * Convert backend dto -> domain item with safe defaults.
 */
fun NotificationsDtos.NotificationDto.toDomain(): NotificationItem {
    val safeId = id ?: "local-${System.currentTimeMillis()}"

    // Backend currently sends coarse types (ORDER/CART/PROMO). We map them to the new granular app types.
    // If backend later starts sending the granular strings, this will also work.
    val safeType = when (type?.uppercase()) {
        "ORDER_PLACED" -> NotificationType.ORDER_PLACED
        "ORDER_PAID" -> NotificationType.ORDER_PAID
        "SHIPPED" -> NotificationType.SHIPPED
        "DELIVERED" -> NotificationType.DELIVERED
        "CART_REMINDER" -> NotificationType.CART_REMINDER
        "GENERAL" -> NotificationType.GENERAL

        // Legacy/coarse values:
        "ORDER" -> NotificationType.GENERAL
        "CART" -> NotificationType.CART_REMINDER
        "PROMO" -> NotificationType.GENERAL

        else -> NotificationType.GENERAL
    }

    val safeDeeplink = when (deeplink?.uppercase()) {
        "ORDERS" -> NotificationDeeplink.ORDERS
        "CART" -> NotificationDeeplink.CART
        else -> NotificationDeeplink.NONE
    }
    return NotificationItem(
        id = safeId,
        type = safeType,
        title = title.orEmpty().ifBlank { "Update" },
        message = message.orEmpty(),
        createdAtMillis = createdAtMillis ?: System.currentTimeMillis(),
        read = read ?: false,
        deeplink = safeDeeplink
    )
}
