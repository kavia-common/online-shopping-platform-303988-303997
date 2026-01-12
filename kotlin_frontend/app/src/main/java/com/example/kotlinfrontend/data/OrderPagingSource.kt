package com.example.kotlinfrontend.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.kotlinfrontend.model.Order

class OrderPagingSource(
    private val repository: OrderRepository,
    private val pageSize: Int,
    private val filters: OrderRepository.OrderFilters
) : PagingSource<Int, Order>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Order> {
        return try {
            val pageIndex = params.key ?: 0
            val result = repository.fetchOrdersPage(
                pageIndex = pageIndex,
                pageSize = pageSize,
                filters = filters
            )

            val endOfPaginationReached = result.items.isEmpty() || result.items.size < pageSize

            val nextKey = if (endOfPaginationReached) null else pageIndex + 1
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

    override fun getRefreshKey(state: PagingState<Int, Order>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val closest = state.closestPageToPosition(anchorPosition) ?: return null
        return closest.prevKey?.plus(1) ?: closest.nextKey?.minus(1)
    }
}
