package com.example.kotlinfrontend.data

import com.example.kotlinfrontend.model.Product
import com.example.kotlinfrontend.model.ProductFilter
import kotlin.math.min
import kotlin.random.Random

class ProductRepository {

    data class PageResult(
        val items: List<Product>,
        val totalCount: Int
    )

    private val categories = listOf("Electronics", "Clothing", "Home", "Books")

    private fun categoryForIndex(idx: Int): String = categories[idx % categories.size]

    /**
     * Fetch a page of products with optional search query and filters.
     *
     * This is currently a local stub to demonstrate:
     * - Paging 3 integration
     * - Search + filter invalidation behavior
     * - Load states (loading/error/empty)
     *
     * Replace this with a real network call later (Retrofit/OkHttp/etc).
     */
    suspend fun fetchProductsPage(
        pageIndex: Int,
        pageSize: Int,
        searchQuery: String,
        filter: ProductFilter
    ): PageResult {
        // Simulate latency
        kotlinx.coroutines.delay(650)

        // Simulate occasional network-like failures (so retry paths can be verified)
        if (Random.nextFloat() < 0.12f) {
            throw RuntimeException("Network error while loading products. Please retry.")
        }

        // Build a stable-ish fake dataset.
        val all = buildAllProducts(totalCount = 200)

        // Apply search + filter.
        val trimmedQuery = searchQuery.trim()
        val filtered = all.asSequence()
            .filter { product ->
                // Search over title and description
                if (trimmedQuery.isBlank()) true
                else {
                    val q = trimmedQuery.lowercase()
                    product.title.lowercase().contains(q) || product.description.lowercase().contains(q)
                }
            }
            .filter { product ->
                // Category filter (embedded in title for now; structured so it can become a real field later)
                val category = extractCategoryFromTitle(product.title)
                filter.category?.let { it == category } ?: true
            }
            .filter { product ->
                filter.minPriceCents?.let { product.priceCents >= it } ?: true
            }
            .filter { product ->
                filter.maxPriceCents?.let { product.priceCents <= it } ?: true
            }
            .toList()

        val start = pageIndex * pageSize
        if (start >= filtered.size) {
            return PageResult(emptyList(), filtered.size)
        }
        val endExclusive = min(start + pageSize, filtered.size)
        val pageItems = filtered.subList(start, endExclusive)

        return PageResult(pageItems, filtered.size)
    }

    private fun buildAllProducts(totalCount: Int): List<Product> {
        return (0 until totalCount).map { idx ->
            val category = categoryForIndex(idx)
            val priceCents = 999 + (idx * 13) % 2500
            Product(
                id = "prod_$idx",
                title = "$category • Product #$idx",
                description = "A minimal, modern product description for item #$idx in $category. Scroll to load more.",
                priceCents = priceCents
            )
        }
    }

    private fun extractCategoryFromTitle(title: String): String {
        // Title format: "<Category> • Product #n"
        val split = title.split("•")
        return split.firstOrNull()?.trim().orEmpty()
    }
}
