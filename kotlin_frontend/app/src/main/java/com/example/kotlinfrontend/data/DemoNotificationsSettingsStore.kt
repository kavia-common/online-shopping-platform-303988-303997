package com.example.kotlinfrontend.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists demo notifications settings and schedule state.
 *
 * We keep this separate from NotificationsStore so that:
 * - existing notification persistence remains unchanged
 * - we can persist demo mode enabled state and a small pending schedule snapshot
 */
class DemoNotificationsSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // PUBLIC_INTERFACE
    fun isDemoEnabled(): Boolean {
        /** Returns whether demo notifications mode is enabled. */
        return prefs.getBoolean(KEY_DEMO_ENABLED, false)
    }

    // PUBLIC_INTERFACE
    fun setDemoEnabled(enabled: Boolean) {
        /** Enables/disables demo notifications mode (persisted). */
        prefs.edit().putBoolean(KEY_DEMO_ENABLED, enabled).apply()
    }

    // PUBLIC_INTERFACE
    fun getPendingScheduleJson(): String? {
        /** Returns persisted JSON snapshot of pending demo schedule, or null. */
        return prefs.getString(KEY_PENDING_SCHEDULE_JSON, null)
    }

    // PUBLIC_INTERFACE
    fun setPendingScheduleJson(json: String?) {
        /** Persists JSON snapshot of pending demo schedule. Passing null clears it. */
        prefs.edit().putString(KEY_PENDING_SCHEDULE_JSON, json).apply()
    }

    // PUBLIC_INTERFACE
    fun clearAll() {
        /** Clears demo enabled flag and pending schedule snapshot. */
        prefs.edit()
            .remove(KEY_DEMO_ENABLED)
            .remove(KEY_PENDING_SCHEDULE_JSON)
            .apply()
    }

    private companion object {
        private const val PREFS_NAME = "kf_demo_notifications_prefs"
        private const val KEY_DEMO_ENABLED = "demo_enabled"
        private const val KEY_PENDING_SCHEDULE_JSON = "pending_schedule_json"
    }
}
