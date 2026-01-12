package org.example.app.data

/**
 * Simple catalog category model.
 */
data class Category(
    val id: String,
    val name: String
)

/**
 * Simple product model for mock catalog.
 */
data class Product(
    val id: String,
    val name: String,
    val categoryId: String,
    val priceCents: Int,
    /**
     * Short description used in compact contexts.
     */
    val description: String,
    /**
     * Optional list of image URLs for richer product media. This demo app does not load network images;
     * UI shows placeholders and uses this list to decide whether to show "multi-image" indicators.
     */
    val imageUrls: List<String> = emptyList(),
    /**
     * Optional longer-form description used on Product Details.
     * When null/blank, fall back to [description].
     */
    val longDescription: String? = null,
    /**
     * Optional bullet-point specs shown on Product Details.
     */
    val bulletPoints: List<String> = emptyList(),
    /**
     * When true, the product should be visually emphasized as on sale.
     */
    val isOnSale: Boolean = false,
    /**
     * Sale price in cents. When present and [isOnSale] is true and lower than [priceCents],
     * UI will show sale price prominently and strike-through the original.
     */
    val salePriceCents: Int? = null,
    /**
     * Optional discount percent (e.g. 20 means "20% off").
     * If null, UI may compute a derived value from [priceCents] and [salePriceCents].
     */
    val discountPercent: Int? = null
) {
    fun formattedPrice(): String = "$" + String.format("%.2f", priceCents / 100.0)

    fun formattedPriceFromCents(cents: Int): String = "$" + String.format("%.2f", cents / 100.0)

    fun hasMultipleImages(): Boolean = imageUrls.size > 1

    fun effectiveLongDescription(): String {
        val candidate = longDescription?.trim().orEmpty()
        return if (candidate.isNotBlank()) candidate else description
    }

    fun effectiveSalePriceCents(): Int? {
        val sale = salePriceCents
        if (!isOnSale || sale == null) return null
        return if (sale in 1 until priceCents) sale else null
    }

    fun effectiveDiscountPercent(): Int? {
        val explicit = discountPercent
        if (!isOnSale) return null
        if (explicit != null && explicit in 1..99) return explicit

        val sale = effectiveSalePriceCents() ?: return null
        val pct = (((priceCents - sale) * 100.0) / priceCents).toInt()
        return pct.coerceIn(1, 99)
    }
}
