package com.example.kotlinfrontend.data

import android.content.Context
import com.example.kotlinfrontend.model.NotificationItem

/**
 * Repository for notifications.
 *
 * Backend notifications are not available, so this repository relies on NotificationsStore.
 * Demo mode can inject simulated notifications via [addLocalNotification].
 */
class NotificationsRepository(
    private val appContext: Context,
    private val notificationsStore: NotificationsStore
) {

    // PUBLIC_INTERFACE
    fun observeNotifications() = notificationsStore.observeNotifications()

    // PUBLIC_INTERFACE
    fun observeUnreadCount() = notificationsStore.observeUnreadCount()

    // PUBLIC_INTERFACE
    fun markRead(notificationId: String) {
        notificationsStore.markRead(notificationId)
    }

    // PUBLIC_INTERFACE
    fun markAllRead() {
        notificationsStore.markAllRead()
    }

    // PUBLIC_INTERFACE
    fun clearAll() {
        notificationsStore.clearAll()
    }

    // PUBLIC_INTERFACE
    fun addLocalNotification(item: NotificationItem) {
        notificationsStore.addNotification(item)
    }
}
