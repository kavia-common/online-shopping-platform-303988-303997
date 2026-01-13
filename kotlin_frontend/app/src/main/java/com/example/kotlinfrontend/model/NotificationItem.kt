package com.example.kotlinfrontend.model

/**
 * Notification item rendered in the Notifications screen.
 */
data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val timestampMs: Long,
    val isRead: Boolean,
    val iconKey: String,
    val type: String
)
