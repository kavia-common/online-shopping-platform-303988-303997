package com.example.kotlinfrontend.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.kotlinfrontend.model.Product
import com.example.kotlinfrontend.model.ProductFilter

class ProductPagingSource(
    private val repository: ProductRepository,
    private val pageSize: Int,
    private val searchQuery: String,
    private val filter: ProductFilter
) : PagingSource<Int, Product>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Product> {
        return try {
            val pageIndex = params.key ?: 0
            val result = repository.fetchProductsPage(
                pageIndex = pageIndex,
                pageSize = pageSize,
                searchQuery = searchQuery,
                filter = filter
            )

            val nextKey = if (result.items.isEmpty()) null else pageIndex + 1
            val prevKey = if (pageIndex == 0) null else pageIndex - 1

            LoadResult.Page(
                data = result.items,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Product>): Int? {
        // Try to refresh near the user's current scroll position.
        val anchorPosition = state.anchorPosition ?: return null
        val closest = state.closestPageToPosition(anchorPosition) ?: return null
        return closest.prevKey?.plus(1) ?: closest.nextKey?.minus(1)
    }
}
