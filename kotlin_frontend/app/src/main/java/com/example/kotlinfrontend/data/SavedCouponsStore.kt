package com.example.kotlinfrontend.data

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.Locale

/**
 * Local persistence for recently used coupon codes.
 *
 * Implementation notes:
 * - SharedPreferences + JSON list of strings via Moshi.
 * - Stores most-recent-first.
 * - De-dupes by normalized coupon code (trim + uppercase).
 */
class SavedCouponsStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder().build()
    private val adapter: JsonAdapter<List<String>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, String::class.java))

    // PUBLIC_INTERFACE
    fun load(): List<String> {
        /** Load saved coupons (most-recent-first). Returns empty list on missing/corrupt data. */
        val json = prefs.getString(KEY_SAVED_COUPONS_JSON, null) ?: return emptyList()
        return try {
            adapter.fromJson(json).orEmpty()
                .mapNotNull { normalize(it).takeIf { code -> code.isNotBlank() } }
                .distinctBy { it.uppercase(Locale.US) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    // PUBLIC_INTERFACE
    fun add(codeRaw: String, maxItems: Int = DEFAULT_MAX_ITEMS): List<String> {
        /**
         * Add a coupon code to saved list (most-recent-first).
         * - De-dupes by normalized code.
         * - Enforces maxItems limit.
         *
         * Returns the updated list.
         */
        val code = normalize(codeRaw)
        if (code.isBlank()) return load()

        val current = load()
        val updated = (listOf(code) + current.filterNot { it.equals(code, ignoreCase = true) })
            .take(maxItems.coerceAtLeast(1))
        saveInternal(updated)
        return updated
    }

    // PUBLIC_INTERFACE
    fun remove(codeRaw: String): List<String> {
        /** Remove a coupon code from saved list. Returns updated list. */
        val code = normalize(codeRaw)
        if (code.isBlank()) return load()

        val updated = load().filterNot { it.equals(code, ignoreCase = true) }
        saveInternal(updated)
        return updated
    }

    // PUBLIC_INTERFACE
    fun clear() {
        /** Clear all saved coupon codes. */
        prefs.edit().remove(KEY_SAVED_COUPONS_JSON).apply()
    }

    // PUBLIC_INTERFACE
    fun isSaved(codeRaw: String): Boolean {
        /** Returns true if code is in saved list (case-insensitive). */
        val code = normalize(codeRaw)
        if (code.isBlank()) return false
        return load().any { it.equals(code, ignoreCase = true) }
    }

    private fun saveInternal(list: List<String>) {
        val json = adapter.toJson(list)
        prefs.edit().putString(KEY_SAVED_COUPONS_JSON, json).apply()
    }

    private fun normalize(raw: String): String {
        // Conservative normalization: trim + uppercase; keep hyphens.
        return raw.trim().uppercase(Locale.US)
    }

    private companion object {
        private const val PREFS_NAME = "kf_saved_coupons_prefs"
        private const val KEY_SAVED_COUPONS_JSON = "saved_coupons_json"
        const val DEFAULT_MAX_ITEMS: Int = 8
    }
}
