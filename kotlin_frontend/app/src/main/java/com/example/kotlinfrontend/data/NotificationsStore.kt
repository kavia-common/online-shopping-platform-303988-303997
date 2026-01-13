package com.example.kotlinfrontend.data

import android.content.Context
import android.content.SharedPreferences
import com.example.kotlinfrontend.model.NotificationItem
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Local persistence for notifications.
 *
 * Uses SharedPreferences + JSON to survive app restarts/process death without Room.
 */
class NotificationsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder().build()

    private val listType = Types.newParameterizedType(List::class.java, NotificationItem::class.java)
    private val adapter: JsonAdapter<List<NotificationItem>> = moshi.adapter(listType)

    // PUBLIC_INTERFACE
    fun load(): List<NotificationItem> {
        /** Load notifications from disk. Returns empty list if missing/corrupt. */
        val json = prefs.getString(KEY_NOTIFICATIONS_JSON, null) ?: return emptyList()
        return try {
            adapter.fromJson(json).orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    // PUBLIC_INTERFACE
    fun save(items: List<NotificationItem>) {
        /** Save notifications to disk. */
        val json = adapter.toJson(items)
        prefs.edit().putString(KEY_NOTIFICATIONS_JSON, json).apply()
    }

    private companion object {
        private const val PREFS_NAME = "kf_notifications_prefs"
        private const val KEY_NOTIFICATIONS_JSON = "notifications_json"
    }
}
