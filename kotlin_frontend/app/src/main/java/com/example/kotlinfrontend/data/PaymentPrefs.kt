package com.example.kotlinfrontend.data

import android.content.Context
import com.example.kotlinfrontend.model.PaymentMethod

/**
 * Small persistence helper for checkout payment preferences.
 *
 * We only store the last-selected method id so we can preselect it next time.
 */
object PaymentPrefs {

    private const val PREFS_NAME = "kf_payment_prefs"
    private const val KEY_LAST_PAYMENT_METHOD_ID = "last_payment_method_id"

    // Persist only non-sensitive card metadata.
    private const val KEY_LAST_VALID_CARD_BRAND_ID = "last_valid_card_brand_id"

    // PUBLIC_INTERFACE
    fun getLastPaymentMethod(context: Context): PaymentMethod? {
        /** Returns the last used payment method (or null if none stored). */
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = sp.getString(KEY_LAST_PAYMENT_METHOD_ID, null)
        return PaymentMethod.fromId(id)
    }

    // PUBLIC_INTERFACE
    fun setLastPaymentMethod(context: Context, method: PaymentMethod) {
        /** Persists the last used payment method id. */
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_LAST_PAYMENT_METHOD_ID, method.id).apply()
    }

    // PUBLIC_INTERFACE
    fun getLastValidCardBrandId(context: Context): String? {
        /**
         * Returns last used valid card brand id (e.g. "visa", "amex") or null if none stored.
         * Security: we do NOT store card numbers, expiry, name, CVC, or ZIP.
         */
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_LAST_VALID_CARD_BRAND_ID, null)
    }

    // PUBLIC_INTERFACE
    fun setLastValidCardBrandId(context: Context, brandId: String) {
        /**
         * Persists last used valid card brand id (non-sensitive metadata).
         * Security: do NOT persist PAN/CVC/expiry.
         */
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_LAST_VALID_CARD_BRAND_ID, brandId).apply()
    }
}
