package org.example.app.data

import android.content.Context
import androidx.annotation.VisibleForTesting

/**
 * Local key-value persistence for lightweight app state.
 *
 * Uses SharedPreferences to avoid introducing a database and to keep startup fast.
 */
internal class LocalStore(private val appContext: Context) {

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Reads a previously persisted cart map.
     *
     * Storage format is a single String in the form "productId:qty,productId2:qty2".
     * This keeps dependencies minimal (no JSON library required).
     */
    fun readCart(): Map<String, Int> {
        val raw = prefs.getString(KEY_CART, null)?.trim().orEmpty()
        if (raw.isBlank()) return emptyMap()

        val result = linkedMapOf<String, Int>()
        raw.split(",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { token ->
                val parts = token.split(":")
                if (parts.size != 2) return@forEach

                val id = parts[0].trim()
                val qty = parts[1].trim().toIntOrNull()

                if (id.isNotBlank() && qty != null && qty > 0) {
                    result[id] = qty
                }
            }

        return result.toMap()
    }

    /**
     * Persists the provided cart map.
     *
     * Only positive quantities are stored.
     */
    fun writeCart(cart: Map<String, Int>) {
        val raw = cart.entries
            .asSequence()
            .filter { it.key.isNotBlank() && it.value > 0 }
            .joinToString(separator = ",") { "${it.key}:${it.value}" }

        prefs.edit().putString(KEY_CART, raw).apply()
    }

    /**
     * Reads the saved recent searches list (most-recent-first).
     */
    fun readRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null)?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()

        return raw.split("|")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    /**
     * Persists the recent searches list (most-recent-first).
     */
    fun writeRecentSearches(items: List<String>) {
        val raw = items
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "|")

        prefs.edit().putString(KEY_RECENT_SEARCHES, raw).apply()
    }

    companion object {
        @VisibleForTesting
        internal const val PREFS_NAME = "ocean_shop_prefs"

        @VisibleForTesting
        internal const val KEY_CART = "cart_v1"

        @VisibleForTesting
        internal const val KEY_RECENT_SEARCHES = "recent_searches_v1"
    }
}
