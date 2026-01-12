package com.example.kotlinfrontend.data

import com.example.kotlinfrontend.model.Product
import kotlin.math.min
import kotlin.random.Random

class ProductRepository {

    data class PageResult(
        val items: List<Product>,
        val totalCount: Int
    )

    /**
     * Fetch a page of products.
     *
     * This is currently a local stub to demonstrate lazy loading with proper states.
     * Replace this with a real network call later (Retrofit/OkHttp/etc).
     */
    suspend fun fetchProductsPage(pageIndex: Int, pageSize: Int): PageResult {
        // Simulate latency
        kotlinx.coroutines.delay(650)

        // Simulate occasional network-like failures (so retry paths can be verified)
        if (Random.nextFloat() < 0.12f) {
            throw RuntimeException("Network error while loading products. Please retry.")
        }

        val totalCount = 200
        val start = pageIndex * pageSize
        if (start >= totalCount) {
            return PageResult(emptyList(), totalCount)
        }
        val endExclusive = min(start + pageSize, totalCount)

        val items = (start until endExclusive).map { idx ->
            Product(
                id = "prod_$idx",
                title = "Product #$idx",
                description = "A minimal, modern product description for item #$idx. Scroll to load more.",
                priceCents = 999 + (idx * 13) % 2500
            )
        }

        return PageResult(items, totalCount)
    }
}
