package com.example.kotlinfrontend.ui

import androidx.annotation.DrawableRes
import com.example.kotlinfrontend.R
import java.util.Locale

/**
 * Utility for mapping backend category names to lightweight vector icons.
 *
 * Keep this mapping small and stable; unknown categories fall back to a generic icon.
 */
object CategoryIconMapper {

    /**
     * PUBLIC_INTERFACE
     *
     * Resolve an icon resource ID for a given category name.
     *
     * @param categoryName raw category name from backend
     * @return drawable resource ID (always non-null; falls back to a generic icon)
     */
    // PUBLIC_INTERFACE
    @DrawableRes
    fun iconResForCategory(categoryName: String?): Int {
        val normalized = categoryName
            ?.trim()
            ?.lowercase(Locale.US)
            .orEmpty()

        if (normalized.isBlank()) return R.drawable.ic_category_generic

        // Match common category variants without being overly strict.
        return when {
            normalized.contains("electronic") || normalized.contains("tech") || normalized.contains("phone") ->
                R.drawable.ic_category_electronics

            normalized.contains("cloth") || normalized.contains("apparel") || normalized.contains("fashion") ->
                R.drawable.ic_category_clothing

            normalized.contains("home") || normalized.contains("house") || normalized.contains("kitchen") ->
                R.drawable.ic_category_home

            normalized.contains("beauty") || normalized.contains("cosmetic") || normalized.contains("skincare") ->
                R.drawable.ic_category_beauty

            normalized.contains("sport") || normalized.contains("fitness") || normalized.contains("outdoor") ->
                R.drawable.ic_category_sports

            normalized.contains("book") || normalized.contains("magazine") ->
                R.drawable.ic_category_books

            normalized.contains("toy") || normalized.contains("game") ->
                R.drawable.ic_category_toys

            normalized.contains("grocery") || normalized.contains("food") ->
                R.drawable.ic_category_groceries

            else -> R.drawable.ic_category_generic
        }
    }

    /**
     * PUBLIC_INTERFACE
     *
     * Build an accessibility-friendly content description for a category icon.
     */
    // PUBLIC_INTERFACE
    fun contentDescriptionForCategory(categoryName: String?): String {
        val safe = categoryName?.trim().takeUnless { it.isNullOrBlank() } ?: "Category"
        return "$safe category"
    }
}
