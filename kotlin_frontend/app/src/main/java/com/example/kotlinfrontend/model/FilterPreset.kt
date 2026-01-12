package com.example.kotlinfrontend.model

/**
 * Saved search/filter preset.
 *
 * Stored locally and can be applied later to restore both the search query and filter state.
 */
data class FilterPreset(
    val name: String,
    val query: String,
    val filter: ProductFilter
)
