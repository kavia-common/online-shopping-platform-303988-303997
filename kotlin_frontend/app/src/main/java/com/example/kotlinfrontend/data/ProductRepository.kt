package com.example.kotlinfrontend.data

import com.example.kotlinfrontend.model.Product
import com.example.kotlinfrontend.model.ProductFilter
import com.example.kotlinfrontend.network.ApiClient
import com.example.kotlinfrontend.network.ProductApi
import com.example.kotlinfrontend.network.dto.ProductDto
import kotlin.math.roundToInt

class ProductRepository(
    private val api: ProductApi = ApiClient.createProductApi()
) {

    data class PageResult(
        val items: List<Product>,
        val totalCount: Int
    )

    /**
     * Fetch a page of products with optional search query and filters.
     *
     * Paging 3 uses 0-based page indexes (Int keys), which maps directly to Spring Pageable "page".
     */
    suspend fun fetchProductsPage(
        pageIndex: Int,
        pageSize: Int,
        searchQuery: String,
        filter: ProductFilter
    ): PageResult {
        val trimmedQuery = searchQuery.trim().ifBlank { null }

        // Our UI stores cents as Int; backend uses price as Double.
        val minPrice = filter.minPriceCents?.let { it / 100.0 }
        val maxPrice = filter.maxPriceCents?.let { it / 100.0 }

        // Keep default sort stable; tweak later if needed.
        val page = api.getProducts(
            page = pageIndex,
            size = pageSize,
            sort = "createdAt,desc",
            query = trimmedQuery,
            category = filter.category,
            minPrice = minPrice,
            maxPrice = maxPrice
        )

        val items = page.content.map { it.toDomain() }
        val totalCount = page.totalElements.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        return PageResult(items = items, totalCount = totalCount)
    }
}

private fun ProductDto.toDomain(): Product {
    // Some backends might return numeric ids; we defensively stringify nullable.
    val safeId = (id ?: "").ifBlank { "unknown" }

    return Product(
        id = safeId,
        name = name,
        description = description,
        price = price,
        category = category,
        imageUrl = imageUrl,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
