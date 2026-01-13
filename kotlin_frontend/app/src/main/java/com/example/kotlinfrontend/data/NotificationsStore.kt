package com.example.kotlinfrontend.data

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.kotlinfrontend.model.NotificationItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local notifications store with persistence.
 *
 * Uses SharedPreferences + JSON encoding to persist notifications and read/unread state.
 */
class NotificationsStore(appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val notificationsLiveData = MutableLiveData<List<NotificationItem>>(emptyList())
    private val unreadCountLiveData = MutableLiveData<Int>(0)

    init {
        loadFromDisk()
    }

    // PUBLIC_INTERFACE
    fun observeNotifications(): LiveData<List<NotificationItem>> = notificationsLiveData

    // PUBLIC_INTERFACE
    fun observeUnreadCount(): LiveData<Int> = unreadCountLiveData

    // PUBLIC_INTERFACE
    fun addNotification(item: NotificationItem) {
        val current = notificationsLiveData.value.orEmpty().toMutableList()
        current.add(0, item) // newest first
        persistAndPublish(current)
    }

    // PUBLIC_INTERFACE
    fun markRead(notificationId: String) {
        val current = notificationsLiveData.value.orEmpty().toMutableList()
        val updated = current.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        }
        persistAndPublish(updated)
    }

    // PUBLIC_INTERFACE
    fun markAllRead() {
        val updated = notificationsLiveData.value.orEmpty().map { it.copy(isRead = true) }
        persistAndPublish(updated)
    }

    // PUBLIC_INTERFACE
    fun clearAll() {
        prefs.edit().remove(KEY_NOTIFICATIONS_JSON).apply()
        notificationsLiveData.postValue(emptyList())
        unreadCountLiveData.postValue(0)
    }

    private fun loadFromDisk() {
        val json = prefs.getString(KEY_NOTIFICATIONS_JSON, null)
        if (json.isNullOrBlank()) {
            notificationsLiveData.postValue(emptyList())
            unreadCountLiveData.postValue(0)
            return
        }

        val list = try {
            decode(json)
        } catch (_: Exception) {
            emptyList()
        }
        notificationsLiveData.postValue(list)
        unreadCountLiveData.postValue(list.count { !it.isRead })
    }

    private fun persistAndPublish(list: List<NotificationItem>) {
        prefs.edit().putString(KEY_NOTIFICATIONS_JSON, encode(list)).apply()
        notificationsLiveData.postValue(list)
        unreadCountLiveData.postValue(list.count { !it.isRead })
    }

    private fun encode(list: List<NotificationItem>): String {
        val arr = JSONArray()
        for (n in list) {
            val o = JSONObject()
            o.put("id", n.id)
            o.put("title", n.title)
            o.put("body", n.body)
            o.put("timestampMs", n.timestampMs)
            o.put("isRead", n.isRead)
            o.put("iconKey", n.iconKey)
            o.put("type", n.type)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun decode(json: String): List<NotificationItem> {
        val arr = JSONArray(json)
        val out = ArrayList<NotificationItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                NotificationItem(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    body = o.optString("body"),
                    timestampMs = o.optLong("timestampMs"),
                    isRead = o.optBoolean("isRead", false),
                    iconKey = o.optString("iconKey", "ic_notifications_24"),
                    type = o.optString("type", "generic")
                )
            )
        }
        return out
    }

    companion object {
        private const val PREFS_NAME = "notifications_store"
        private const val KEY_NOTIFICATIONS_JSON = "notifications_json"
    }
}
