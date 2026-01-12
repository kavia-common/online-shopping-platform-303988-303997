package com.example.kotlinfrontend.data

import android.content.Context
import android.content.SharedPreferences
import com.example.kotlinfrontend.model.CartItem
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Local persistence for cart items.
 *
 * Uses SharedPreferences + JSON so cart survives app restarts/process death without needing Room.
 */
class CartStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder().build()

    private val listType = Types.newParameterizedType(List::class.java, CartItem::class.java)
    private val adapter: JsonAdapter<List<CartItem>> = moshi.adapter(listType)

    // PUBLIC_INTERFACE
    fun load(): List<CartItem> {
        /** Load cart items from disk. Returns empty list if missing/corrupt. */
        val json = prefs.getString(KEY_CART_JSON, null) ?: return emptyList()
        return try {
            adapter.fromJson(json).orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    // PUBLIC_INTERFACE
    fun save(items: List<CartItem>) {
        /** Save cart items to disk. */
        val json = adapter.toJson(items)
        prefs.edit().putString(KEY_CART_JSON, json).apply()
    }

    // PUBLIC_INTERFACE
    fun clear() {
        /** Remove cart data from disk. */
        prefs.edit().remove(KEY_CART_JSON).apply()
    }

    private companion object {
        private const val PREFS_NAME = "kf_cart_prefs"
        private const val KEY_CART_JSON = "cart_items_json"
    }
}
