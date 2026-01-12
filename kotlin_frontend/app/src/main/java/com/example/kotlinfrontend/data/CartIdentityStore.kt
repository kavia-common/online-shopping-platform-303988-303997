package com.example.kotlinfrontend.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the temporary user identity used to scope cart persistence on the backend.
 *
 * For now we keep this intentionally lightweight (email only).
 */
class CartIdentityStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // PUBLIC_INTERFACE
    fun getEmail(): String? {
        /** Returns stored email used for cart ownership, or null if not set. */
        return prefs.getString(KEY_EMAIL, null)?.trim()?.ifBlank { null }
    }

    // PUBLIC_INTERFACE
    fun setEmail(email: String?) {
        /** Stores email used for cart ownership. Passing null clears it. */
        val normalized = email?.trim()?.ifBlank { null }
        if (normalized == null) {
            prefs.edit().remove(KEY_EMAIL).apply()
        } else {
            prefs.edit().putString(KEY_EMAIL, normalized).apply()
        }
    }

    private companion object {
        private const val PREFS_NAME = "kf_cart_identity_prefs"
        private const val KEY_EMAIL = "cart_email"
    }
}
