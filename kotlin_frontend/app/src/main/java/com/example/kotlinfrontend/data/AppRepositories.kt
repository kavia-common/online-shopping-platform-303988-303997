package com.example.kotlinfrontend.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Minimal repository locator to avoid introducing a DI framework.
 */
object AppRepositories {

    @Volatile
    private var cartRepository: CartRepository? = null

    @Volatile
    private var notificationsRepository: NotificationsRepository? = null

    // PUBLIC_INTERFACE
    fun cart(context: Context): CartRepository {
        /** Return process-wide CartRepository instance (state restored from disk on first init). */
        return cartRepository ?: synchronized(this) {
            cartRepository ?: CartRepository(context.applicationContext).also { cartRepository = it }
        }
    }

    // PUBLIC_INTERFACE
    fun cartItemCount(context: Context): Flow<Int> {
        /** Returns total quantity of all items in cart (for badge). */
        return cart(context).items.map { list -> list.sumOf { it.quantity } }
    }

    // PUBLIC_INTERFACE
    fun notifications(context: Context): NotificationsRepository {
        /** Return process-wide NotificationsRepository instance (state restored from disk on first init). */
        return notificationsRepository ?: synchronized(this) {
            notificationsRepository ?: NotificationsRepository(context.applicationContext).also {
                notificationsRepository = it
            }
        }
    }

    // PUBLIC_INTERFACE
    fun unreadNotificationsCount(context: Context): Flow<Int> {
        /** Returns unread notifications count (for badge). */
        return notifications(context).unreadCount
    }
}
