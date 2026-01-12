package com.example.kotlinfrontend.data

import android.content.Context
import android.content.SharedPreferences
import com.example.kotlinfrontend.model.FilterPreset
import com.example.kotlinfrontend.model.ProductFilter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simple local persistence for filter presets.
 *
 * Uses SharedPreferences to keep the implementation minimal and dependency-free.
 */
class FilterPresetStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // PUBLIC_INTERFACE
    fun getAll(): List<FilterPreset> {
        /** Return all saved presets in stable (saved) order. */
        val raw = prefs.getString(KEY_PRESETS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { idx ->
                val obj = array.optJSONObject(idx) ?: return@mapNotNull null
                presetFromJson(obj)
            }
        } catch (_: Throwable) {
            // Corrupted JSON -> fail safe: treat as empty.
            emptyList()
        }
    }

    // PUBLIC_INTERFACE
    fun upsert(preset: FilterPreset) {
        /** Insert or replace a preset by name (case-insensitive). */
        val existing = getAll().toMutableList()
        val index = existing.indexOfFirst { it.name.equals(preset.name, ignoreCase = true) }
        if (index >= 0) existing[index] = preset else existing.add(preset)
        persist(existing)
    }

    // PUBLIC_INTERFACE
    fun deleteByName(name: String): Boolean {
        /** Delete preset by name (case-insensitive). Returns true if anything was deleted. */
        val existing = getAll().toMutableList()
        val before = existing.size
        existing.removeAll { it.name.equals(name, ignoreCase = true) }
        val changed = existing.size != before
        if (changed) persist(existing)
        return changed
    }

    // PUBLIC_INTERFACE
    fun clear() {
        /** Remove all saved presets. */
        prefs.edit().remove(KEY_PRESETS_JSON).apply()
    }

    private fun persist(presets: List<FilterPreset>) {
        val array = JSONArray()
        presets.forEach { array.put(presetToJson(it)) }
        prefs.edit().putString(KEY_PRESETS_JSON, array.toString()).apply()
    }

    private fun presetToJson(preset: FilterPreset): JSONObject {
        val obj = JSONObject()
        obj.put("name", preset.name)
        obj.put("query", preset.query)

        val f = JSONObject()
        f.put("category", preset.filter.category)
        f.put("minPriceCents", preset.filter.minPriceCents)
        f.put("maxPriceCents", preset.filter.maxPriceCents)
        obj.put("filter", f)

        return obj
    }

    private fun presetFromJson(obj: JSONObject): FilterPreset? {
        val name = obj.optString("name", "").trim()
        if (name.isBlank()) return null

        val query = obj.optString("query", "")
        val f = obj.optJSONObject("filter") ?: JSONObject()

        val category = f.optString("category", "").let { it.ifBlank { null } }
        val minPrice = if (f.has("minPriceCents") && !f.isNull("minPriceCents")) f.optInt("minPriceCents") else null
        val maxPrice = if (f.has("maxPriceCents") && !f.isNull("maxPriceCents")) f.optInt("maxPriceCents") else null

        return FilterPreset(
            name = name,
            query = query,
            filter = ProductFilter(
                category = category,
                minPriceCents = minPrice,
                maxPriceCents = maxPrice
            )
        )
    }

    private companion object {
        const val PREFS_NAME = "filter_presets"
        const val KEY_PRESETS_JSON = "presets_json"
    }
}
