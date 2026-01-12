package com.example.kotlinfrontend.data

import androidx.paging.PagingSource
import com.example.kotlinfrontend.model.Product
import com.example.kotlinfrontend.model.ProductFilter
import com.example.kotlinfrontend.network.ApiClient
import com.example.kotlinfrontend.network.ProductApi
import com.example.kotlinfrontend.network.dto.ProductDto

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
        return fetchProductsPage(
            pageIndex = pageIndex,
            pageSize = pageSize,
            searchQuery = searchQuery,
            categoryOverride = filter.category,
            minPriceCents = filter.minPriceCents,
            maxPriceCents = filter.maxPriceCents
        )
    }

    /**
     * Fetch a page of products with an explicit category override (used by category sections).
     */
    suspend fun fetchProductsPage(
        pageIndex: Int,
        pageSize: Int,
        searchQuery: String,
        categoryOverride: String?,
        minPriceCents: Int?,
        maxPriceCents: Int?
    ): PageResult {
        val trimmedQuery = searchQuery.trim().ifBlank { null }

        // Our UI stores cents as Int; backend uses price as Double.
        val minPrice = minPriceCents?.let { it / 100.0 }
        val maxPrice = maxPriceCents?.let { it / 100.0 }

        // Keep default sort stable; tweak later if needed.
        val page = api.getProducts(
            page = pageIndex,
            size = pageSize,
            sort = "createdAt,desc",
            query = trimmedQuery,
            category = categoryOverride,
            minPrice = minPrice,
            maxPrice = maxPrice
        )

        val items = page.content.map { it.toDomain() }
        val totalCount = page.totalElements.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        return PageResult(items = items, totalCount = totalCount)
    }

    // PUBLIC_INTERFACE
    suspend fun fetchCategories(): List<String> {
        /** Fetch distinct categories from backend and return as a sorted list (null/blank removed). */
        return api.getProductCategories()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }
    }

    // PUBLIC_INTERFACE
    fun pagingSourceFactory(
        pageSize: Int,
        searchQuery: String,
        filter: ProductFilter,
        categoryOverride: String?
    ): () -> PagingSource<Int, Product> {
        /** Returns a PagingSource factory that loads products for the given category (or no category if null). */
        val effectiveFilter = filter.copy(category = categoryOverride)
        return {
            ProductPagingSource(
                repository = this,
                pageSize = pageSize,
                searchQuery = searchQuery,
                filter = effectiveFilter
            )
        }
    }

    // PUBLIC_INTERFACE
    suspend fun fetchCategoryPreview(
        category: String,
        previewSize: Int,
        searchQuery: String,
        filter: ProductFilter
    ): List<Product> {
        /**
         * Fetches a small preview list for a category section.
         * Intended to be called lazily by UI when a section is near/visible.
         */
        val result = fetchProductsPage(
            pageIndex = 0,
            pageSize = previewSize,
            searchQuery = searchQuery,
            categoryOverride = category,
            minPriceCents = filter.minPriceCents,
            maxPriceCents = filter.maxPriceCents
        )
        return result.items
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
