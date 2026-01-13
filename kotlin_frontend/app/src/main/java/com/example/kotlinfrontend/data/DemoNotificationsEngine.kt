package com.example.kotlinfrontend.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.kotlinfrontend.model.NotificationItem
import java.util.UUID

/**
 * Demo-only notifications engine.
 *
 * Generates local notifications without backend support, persists demo toggle + schedule state,
 * and uses the existing NotificationsStore/Repository to surface items and unread count.
 */
class DemoNotificationsEngine(
    private val appContext: Context,
    private val notificationsRepository: NotificationsRepository,
    private val notificationsStore: NotificationsStore,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scheduledRunnables: MutableMap<String, Runnable> = LinkedHashMap()

    // PUBLIC_INTERFACE
    fun isDemoEnabled(): Boolean {
        return prefs.getBoolean(KEY_DEMO_ENABLED, false)
    }

    // PUBLIC_INTERFACE
    fun setDemoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEMO_ENABLED, enabled).apply()
        if (!enabled) {
            cancelAllScheduled()
            clearPendingSchedule()
        } else {
            // On enabling, restore any pending schedule (e.g., from app restart).
            restorePendingScheduleIfAny()
        }
    }

    // PUBLIC_INTERFACE
    fun cancelAllScheduled() {
        val keys = scheduledRunnables.keys.toList()
        for (k in keys) {
            scheduledRunnables[k]?.let { handler.removeCallbacks(it) }
        }
        scheduledRunnables.clear()
    }

    // PUBLIC_INTERFACE
    fun clearAllNotifications() {
        // Store already supports local persistence; repository is the façade.
        notificationsRepository.clearAll()
        cancelAllScheduled()
        clearPendingSchedule()
    }

    // PUBLIC_INTERFACE
    fun markAllRead() {
        notificationsRepository.markAllRead()
    }

    // PUBLIC_INTERFACE
    fun generateOrderNotification(status: DemoOrderStatus) {
        if (!isDemoEnabled()) return
        val now = System.currentTimeMillis()
        val item = when (status) {
            DemoOrderStatus.PLACED -> buildNotification(
                type = DemoType.ORDER_PLACED,
                title = "Order placed",
                body = "Thanks! Your order has been received and is being processed.",
                timestampMs = now
            )
            DemoOrderStatus.PAID -> buildNotification(
                type = DemoType.ORDER_PAID,
                title = "Payment received",
                body = "Payment confirmed. We’ll start preparing your items.",
                timestampMs = now
            )
            DemoOrderStatus.SHIPPED -> buildNotification(
                type = DemoType.ORDER_SHIPPED,
                title = "Order shipped",
                body = "Good news—your order is on its way.",
                timestampMs = now
            )
            DemoOrderStatus.DELIVERED -> buildNotification(
                type = DemoType.ORDER_DELIVERED,
                title = "Delivered",
                body = "Your order has been delivered. Enjoy!",
                timestampMs = now
            )
        }
        notificationsRepository.addLocalNotification(item)
    }

    // PUBLIC_INTERFACE
    fun generateCartReminder() {
        if (!isDemoEnabled()) return
        val now = System.currentTimeMillis()
        val item = buildNotification(
            type = DemoType.CART_REMINDER,
            title = "Items waiting in your cart",
            body = "Complete your purchase before items sell out.",
            timestampMs = now
        )
        notificationsRepository.addLocalNotification(item)
    }

    /**
     * Called after user places an order. Schedules Paid/Shipped/Delivered with delays.
     * Uses a schedule id stored in prefs to avoid duplicating across app restarts.
     */
    // PUBLIC_INTERFACE
    fun onOrderPlacedHook() {
        if (!isDemoEnabled()) return

        val scheduleId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_PENDING_SCHEDULE_ID, scheduleId)
            .putLong(KEY_PENDING_SCHEDULE_CREATED_AT, createdAt)
            .apply()

        // Generate the immediate "placed" notification now
        generateOrderNotification(DemoOrderStatus.PLACED)

        // Schedule follow-ups
        scheduleOnce(
            key = "order_paid_$scheduleId",
            delayMs = DELAY_PAID_MS
        ) { generateOrderNotification(DemoOrderStatus.PAID) }

        scheduleOnce(
            key = "order_shipped_$scheduleId",
            delayMs = DELAY_SHIPPED_MS
        ) { generateOrderNotification(DemoOrderStatus.SHIPPED) }

        scheduleOnce(
            key = "order_delivered_$scheduleId",
            delayMs = DELAY_DELIVERED_MS
        ) {
            generateOrderNotification(DemoOrderStatus.DELIVERED)
            // Clear pending schedule after completion.
            clearPendingSchedule()
        }
    }

    /**
     * Called when cart becomes non-empty / last cart update. Schedules a reminder after inactivity.
     * This should be invoked from cart updates (best-effort demo).
     */
    // PUBLIC_INTERFACE
    fun onCartActivityHook(cartHasItems: Boolean) {
        if (!isDemoEnabled()) return

        // Cancel any existing reminder schedule.
        val existingId = prefs.getString(KEY_CART_SCHEDULE_ID, null)
        if (existingId != null) {
            cancelScheduled("cart_reminder_$existingId")
        }

        if (!cartHasItems) {
            prefs.edit().remove(KEY_CART_SCHEDULE_ID).remove(KEY_CART_SCHEDULE_CREATED_AT).apply()
            return
        }

        val scheduleId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_CART_SCHEDULE_ID, scheduleId)
            .putLong(KEY_CART_SCHEDULE_CREATED_AT, createdAt)
            .apply()

        scheduleOnce(
            key = "cart_reminder_$scheduleId",
            delayMs = DELAY_CART_REMINDER_MS
        ) {
            generateCartReminder()
            prefs.edit().remove(KEY_CART_SCHEDULE_ID).remove(KEY_CART_SCHEDULE_CREATED_AT).apply()
        }
    }

    private fun scheduleOnce(key: String, delayMs: Long, action: () -> Unit) {
        // Ensure we don't duplicate within this runtime session.
        cancelScheduled(key)

        val runnable = Runnable {
            scheduledRunnables.remove(key)
            action()
        }
        scheduledRunnables[key] = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelScheduled(key: String) {
        scheduledRunnables[key]?.let {
            handler.removeCallbacks(it)
            scheduledRunnables.remove(key)
        }
    }

    private fun clearPendingSchedule() {
        prefs.edit().remove(KEY_PENDING_SCHEDULE_ID).remove(KEY_PENDING_SCHEDULE_CREATED_AT).apply()
    }

    private fun restorePendingScheduleIfAny() {
        val scheduleId = prefs.getString(KEY_PENDING_SCHEDULE_ID, null) ?: return
        val createdAt = prefs.getLong(KEY_PENDING_SCHEDULE_CREATED_AT, 0L)
        if (createdAt <= 0L) return

        val elapsed = System.currentTimeMillis() - createdAt

        // If already expired, clear.
        if (elapsed > DELAY_DELIVERED_MS + 60_000L) {
            clearPendingSchedule()
            return
        }

        // Reschedule remaining steps if not already executed in store.
        // Note: we do not have explicit "executed" markers; we rely on time windows and
        // avoid duplication by checking recent notifications in store where possible.
        if (elapsed < DELAY_PAID_MS) {
            scheduleOnce("order_paid_$scheduleId", DELAY_PAID_MS - elapsed) {
                generateOrderNotification(DemoOrderStatus.PAID)
            }
        }
        if (elapsed < DELAY_SHIPPED_MS) {
            scheduleOnce("order_shipped_$scheduleId", DELAY_SHIPPED_MS - elapsed) {
                generateOrderNotification(DemoOrderStatus.SHIPPED)
            }
        }
        if (elapsed < DELAY_DELIVERED_MS) {
            scheduleOnce("order_delivered_$scheduleId", DELAY_DELIVERED_MS - elapsed) {
                generateOrderNotification(DemoOrderStatus.DELIVERED)
                clearPendingSchedule()
            }
        } else {
            clearPendingSchedule()
        }

        // Restore cart reminder too, if any.
        val cartId = prefs.getString(KEY_CART_SCHEDULE_ID, null) ?: return
        val cartCreatedAt = prefs.getLong(KEY_CART_SCHEDULE_CREATED_AT, 0L)
        if (cartCreatedAt <= 0L) return
        val cartElapsed = System.currentTimeMillis() - cartCreatedAt
        if (cartElapsed < DELAY_CART_REMINDER_MS) {
            scheduleOnce("cart_reminder_$cartId", DELAY_CART_REMINDER_MS - cartElapsed) {
                generateCartReminder()
                prefs.edit().remove(KEY_CART_SCHEDULE_ID).remove(KEY_CART_SCHEDULE_CREATED_AT).apply()
            }
        } else {
            prefs.edit().remove(KEY_CART_SCHEDULE_ID).remove(KEY_CART_SCHEDULE_CREATED_AT).apply()
        }
    }

    private fun buildNotification(
        type: DemoType,
        title: String,
        body: String,
        timestampMs: Long
    ): NotificationItem {
        // NotificationItem exists already; we populate typical fields while staying defensive.
        // If the model has different fields, repository/store will adapt; see repo methods.
        return NotificationItem(
            id = "demo_${UUID.randomUUID()}",
            title = title,
            body = body,
            timestampMs = timestampMs,
            isRead = false,
            iconKey = type.iconKey,
            type = type.typeKey
        )
    }

    enum class DemoOrderStatus { PLACED, PAID, SHIPPED, DELIVERED }

    enum class DemoType(val typeKey: String, val iconKey: String) {
        ORDER_PLACED("order_placed", "ic_notifications_24"),
        ORDER_PAID("order_paid", "ic_payment_card_generic"),
        ORDER_SHIPPED("order_shipped", "ic_category_generic"),
        ORDER_DELIVERED("order_delivered", "ic_category_generic"),
        CART_REMINDER("cart_reminder", "ic_cart_24"),
    }

    companion object {
        private const val PREFS_NAME = "demo_notifications_prefs"
        private const val KEY_DEMO_ENABLED = "demo_enabled"

        private const val KEY_PENDING_SCHEDULE_ID = "pending_order_schedule_id"
        private const val KEY_PENDING_SCHEDULE_CREATED_AT = "pending_order_schedule_created_at"

        private const val KEY_CART_SCHEDULE_ID = "pending_cart_schedule_id"
        private const val KEY_CART_SCHEDULE_CREATED_AT = "pending_cart_schedule_created_at"

        // Delays tuned to be demo-friendly but noticeable.
        private const val DELAY_PAID_MS = 5_000L
        private const val DELAY_SHIPPED_MS = 12_000L
        private const val DELAY_DELIVERED_MS = 20_000L

        private const val DELAY_CART_REMINDER_MS = 30_000L
    }
}
