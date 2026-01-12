package com.example.kotlinfrontend.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.kotlinfrontend.data.ProductPagingSource
import com.example.kotlinfrontend.data.ProductRepository
import com.example.kotlinfrontend.model.Product
import com.example.kotlinfrontend.model.ProductFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class ProductListViewModel : ViewModel() {

    private val repository = ProductRepository()

    private val pageSize = 20

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _filter = MutableStateFlow(ProductFilter())
    val filter: StateFlow<ProductFilter> = _filter

    // Debounce only the search query (filters should apply immediately).
    private val debouncedQuery = _searchQuery
        .debounce(300)
        .distinctUntilChanged()

    private val queryAndFilter = combine(
        debouncedQuery,
        _filter
    ) { query, filterState ->
        QueryParams(query = query, filter = filterState)
    }.distinctUntilChanged()

    val products: Flow<PagingData<Product>> =
        queryAndFilter
            .flatMapLatest { params ->
                Pager(
                    config = PagingConfig(
                        pageSize = pageSize,
                        initialLoadSize = pageSize * 2, // efficient initial fill; still bounded
                        prefetchDistance = 6,           // loads slightly ahead; avoids over-fetching
                        enablePlaceholders = false
                    ),
                    pagingSourceFactory = {
                        ProductPagingSource(
                            repository = repository,
                            pageSize = pageSize,
                            searchQuery = params.query,
                            filter = params.filter
                        )
                    }
                ).flow
            }
            .cachedIn(viewModelScope)

    /**
     * Exposed to the Activity so it can format empty-state messages.
     */
    val activeQueryParams: StateFlow<QueryParams> =
        queryAndFilter.stateIn(viewModelScope, SharingStarted.Eagerly, QueryParams("", ProductFilter()))

    // PUBLIC_INTERFACE
    fun setSearchQuery(query: String) {
        /** Update the search query used by the PagingSource (debounced in the flow). */
        _searchQuery.value = query
    }

    // PUBLIC_INTERFACE
    fun setCategory(category: String?) {
        /** Update category filter and trigger a new paging query. */
        _filter.value = _filter.value.copy(category = category)
    }

    // PUBLIC_INTERFACE
    fun setMinPriceCents(minPriceCents: Int?) {
        /** Update minimum price filter and trigger a new paging query. */
        _filter.value = _filter.value.copy(minPriceCents = minPriceCents)
    }

    // PUBLIC_INTERFACE
    fun setMaxPriceCents(maxPriceCents: Int?) {
        /** Update maximum price filter and trigger a new paging query. */
        _filter.value = _filter.value.copy(maxPriceCents = maxPriceCents)
    }

    // PUBLIC_INTERFACE
    fun clearFilters() {
        /** Clear all filters and trigger a new paging query. */
        _filter.value = ProductFilter()
    }

    data class QueryParams(
        val query: String,
        val filter: ProductFilter
    )
}
