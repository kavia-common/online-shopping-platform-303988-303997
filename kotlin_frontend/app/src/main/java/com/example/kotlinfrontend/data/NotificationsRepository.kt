package com.example.kotlinfrontend.data

import android.content.Context
import com.example.kotlinfrontend.model.NotificationDeeplink
import com.example.kotlinfrontend.model.NotificationItem
import com.example.kotlinfrontend.model.NotificationType
import com.example.kotlinfrontend.network.ApiClient
import com.example.kotlinfrontend.network.dto.NotificationsDtos
import com.example.kotlinfrontend.network.dto.toDomain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * Repository for in-app notifications.
 *
 * - Persists locally using SharedPreferences + Moshi JSON.
 * - Exposes an unread count Flow for a badge.
 * - Attempts backend sync when available; gracefully falls back to local-only if backend 404/unavailable.
 */
class NotificationsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = NotificationsStore(appContext)

    private val api by lazy { ApiClient.createNotificationApi() }

    private val _items = MutableStateFlow(store.load().sortedByDescending { it.createdAtMillis })
    val items = _items.asStateFlow()

    val unreadCount: Flow<Int> = items.map { list -> list.count { !it.read } }

    // PUBLIC_INTERFACE
    suspend fun fetchLatest() {
        /**
         * Fetch latest notifications from backend.
         *
         * If backend returns 404 or network fails, this method keeps local state unchanged
         * (local-only mode).
         */
        try {
            val remote = api.getNotifications().map { it.toDomain() }
            // Merge: remote wins by id, preserve local read state when possible.
            val localById = _items.value.associateBy { it.id }
            val merged = remote.map { r ->
                val local = localById[r.id]
                if (local != null) r.copy(read = local.read || r.read) else r
            }
            // Also keep any local-only notifications not present remotely.
            val remoteIds = merged.map { it.id }.toSet()
            val localOnly = _items.value.filter { it.id !in remoteIds }
            setAndPersist((merged + localOnly).distinctBy { it.id }.sortedByDescending { it.createdAtMillis })
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Fallback: local-only mode. No-op.
        }
    }

    // PUBLIC_INTERFACE
    fun markRead(id: String) {
        /** Mark a single notification read locally and best-effort inform backend. */
        val updated = _items.value.map { if (it.id == id) it.copy(read = true) else it }
        setAndPersist(updated.sortedByDescending { it.createdAtMillis })

        // Best-effort backend update (fire-and-forget via a tiny coroutine without adding new infra).
        // Caller/UI already updated optimistically.
        SimpleRepoScope.launchIo(appContext) {
            try {
                api.markRead(NotificationsDtos.MarkReadRequest(id = id))
            } catch (_: Throwable) {
                // Ignore.
            }
        }
    }

    // PUBLIC_INTERFACE
    fun markAllRead() {
        /** Mark all notifications read locally and best-effort inform backend. */
        val updated = _items.value.map { it.copy(read = true) }
        setAndPersist(updated)

        SimpleRepoScope.launchIo(appContext) {
            try {
                api.markAllRead()
            } catch (_: Throwable) {
                // Ignore.
            }
        }
    }

    // PUBLIC_INTERFACE
    fun dismiss(id: String): NotificationItem? {
        /** Remove a notification locally. Returns removed item for undo. */
        val existing = _items.value.firstOrNull { it.id == id } ?: return null
        val next = _items.value.filter { it.id != id }
        setAndPersist(next)
        return existing
    }

    // PUBLIC_INTERFACE
    fun undoDismiss(item: NotificationItem) {
        /** Restore a previously dismissed notification (best-effort: insert at correct position). */
        val current = _items.value
        val next = (listOf(item) + current.filter { it.id != item.id })
            .sortedByDescending { it.createdAtMillis }
        setAndPersist(next)
    }

    enum class TimeBucket {
        TODAY,
        THIS_WEEK,
        EARLIER
    }

    // PUBLIC_INTERFACE
    fun bucketFor(createdAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): TimeBucket {
        /** Compute a time bucket for UI grouping: Today / This Week / Earlier. */
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val itemCal = Calendar.getInstance().apply { timeInMillis = createdAtMillis }

        val isSameDay =
            nowCal.get(Calendar.ERA) == itemCal.get(Calendar.ERA) &&
                nowCal.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) return TimeBucket.TODAY

        val nowWeek = nowCal.get(Calendar.WEEK_OF_YEAR)
        val itemWeek = itemCal.get(Calendar.WEEK_OF_YEAR)
        val isSameWeek =
            nowCal.get(Calendar.ERA) == itemCal.get(Calendar.ERA) &&
                nowCal.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) &&
                nowWeek == itemWeek

        return if (isSameWeek) TimeBucket.THIS_WEEK else TimeBucket.EARLIER
    }

    // PUBLIC_INTERFACE
    fun groupForUi(items: List<NotificationItem>, nowMillis: Long = System.currentTimeMillis()): Map<TimeBucket, List<NotificationItem>> {
        /**
         * Group items for sectioned UI.
         *
         * Returns only non-empty buckets, preserving reverse-chronological order within each bucket.
         */
        val sorted = items.sortedByDescending { it.createdAtMillis }
        val grouped = sorted.groupBy { bucketFor(it.createdAtMillis, nowMillis) }
        // Stable ordering of keys for UI.
        val order = listOf(TimeBucket.TODAY, TimeBucket.THIS_WEEK, TimeBucket.EARLIER)
        val out = LinkedHashMap<TimeBucket, List<NotificationItem>>()
        for (k in order) {
            val list = grouped[k].orEmpty()
            if (list.isNotEmpty()) out[k] = list
        }
        return out
    }

    // PUBLIC_INTERFACE
    fun insertLocal(notification: NotificationItem) {
        /**
         * Insert a local-only notification (e.g., generated by local events).
         *
         * De-dupes by id; newest first.
         */
        val current = _items.value
        val next = (listOf(notification) + current.filter { it.id != notification.id })
            .sortedByDescending { it.createdAtMillis }
        setAndPersist(next)
    }

    // PUBLIC_INTERFACE
    fun seedDemoIfEmpty() {
        /** Insert a small demo set if user has no notifications yet (keeps app experience non-empty). */
        if (_items.value.isNotEmpty()) return

        val now = System.currentTimeMillis()
        val demo = listOf(
            NotificationItem(
                id = "demo-order-placed-1",
                type = NotificationType.ORDER_PLACED,
                title = "Order placed",
                message = "We’ve received your order. We’ll notify you as it moves along.",
                createdAtMillis = now - 45 * 60 * 1000L,
                read = false,
                deeplink = NotificationDeeplink.ORDERS
            ),
            NotificationItem(
                id = "demo-order-shipped-1",
                type = NotificationType.SHIPPED,
                title = "Order shipped",
                message = "Your order has shipped. Track it in Orders.",
                createdAtMillis = now - 2 * 60 * 60 * 1000L,
                read = false,
                deeplink = NotificationDeeplink.ORDERS
            ),
            NotificationItem(
                id = "demo-cart-1",
                type = NotificationType.CART_REMINDER,
                title = "Don’t forget your cart",
                message = "Items in your cart are waiting. Complete checkout when you’re ready.",
                createdAtMillis = now - 26 * 60 * 60 * 1000L,
                read = true,
                deeplink = NotificationDeeplink.CART
            ),
            NotificationItem(
                id = "demo-general-1",
                type = NotificationType.GENERAL,
                title = "Weekend promo",
                message = "Use SAVE10 at checkout for 10% off eligible items.",
                createdAtMillis = now - 3 * 24 * 60 * 60 * 1000L,
                read = false,
                deeplink = NotificationDeeplink.NONE
            )
        )
        setAndPersist(demo.sortedByDescending { it.createdAtMillis })
    }

    private fun setAndPersist(list: List<NotificationItem>) {
        _items.value = list
        store.save(list)
    }
}
