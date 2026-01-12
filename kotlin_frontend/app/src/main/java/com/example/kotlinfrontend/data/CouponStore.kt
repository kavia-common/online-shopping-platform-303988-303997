package com.example.kotlinfrontend.data

import android.content.Context
import android.content.SharedPreferences
import com.example.kotlinfrontend.model.Coupon
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

/**
 * Local persistence for an applied coupon.
 *
 * Stored in SharedPreferences + JSON (same persistence strategy as CartStore).
 */
class CouponStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder().build()
    private val adapter: JsonAdapter<Coupon> = moshi.adapter(Coupon::class.java)

    // PUBLIC_INTERFACE
    fun load(): Coupon? {
        /** Load applied coupon from disk, or null if none/corrupt. */
        val json = prefs.getString(KEY_COUPON_JSON, null) ?: return null
        return try {
            adapter.fromJson(json)
        } catch (_: Throwable) {
            null
        }
    }

    // PUBLIC_INTERFACE
    fun save(coupon: Coupon?) {
        /** Save coupon to disk. Passing null clears it. */
        if (coupon == null) {
            prefs.edit().remove(KEY_COUPON_JSON).apply()
            return
        }
        val json = adapter.toJson(coupon)
        prefs.edit().putString(KEY_COUPON_JSON, json).apply()
    }

    // PUBLIC_INTERFACE
    fun clear() {
        /** Remove coupon data from disk. */
        prefs.edit().remove(KEY_COUPON_JSON).apply()
    }

    private companion object {
        private const val PREFS_NAME = "kf_coupon_prefs"
        private const val KEY_COUPON_JSON = "coupon_json"
    }
}
