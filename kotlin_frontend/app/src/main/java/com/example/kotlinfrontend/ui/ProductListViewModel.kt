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
import kotlinx.coroutines.launch

class ProductListViewModel : ViewModel() {

    private val repository = ProductRepository()

    private val pageSize = 20

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _filter = MutableStateFlow(ProductFilter())
    val filter: StateFlow<ProductFilter> = _filter

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories

    /**
     * Chip selection state:
     * - null means "All (grouped)" mode
     * - non-null means single-category paged list mode
     */
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory

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

    /**
     * Paged list (used when a specific category is selected).
     * When in grouped mode (selectedCategory == null), the Activity hides this list and shows sections instead.
     */
    val products: Flow<PagingData<Product>> =
        combine(queryAndFilter, _selectedCategory) { params, category ->
            EffectiveParams(params = params, selectedCategory = category)
        }
            .distinctUntilChanged()
            .flatMapLatest { effective ->
                val effectiveFilter =
                    effective.params.filter.copy(category = effective.selectedCategory)

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
                            searchQuery = effective.params.query,
                            filter = effectiveFilter
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

    init {
        refreshCategories()
    }

    // PUBLIC_INTERFACE
    fun refreshCategories() {
        /** Fetch categories from backend and publish them (Activity renders chips). */
        viewModelScope.launch {
            try {
                _categories.value = repository.fetchCategories()
            } catch (_: Throwable) {
                // Leave categories empty; UI will fall back to just "All".
                _categories.value = emptyList()
            }
        }
    }

    // PUBLIC_INTERFACE
    fun setSearchQuery(query: String) {
        /** Update the search query used by the PagingSource (debounced in the flow). */
        _searchQuery.value = query
    }

    // PUBLIC_INTERFACE
    fun selectCategory(category: String?) {
        /**
         * Select a category:
         * - null => "All (grouped)" mode
         * - non-null => single-category paged list
         *
         * Note: We keep _filter.category in sync so other parts of UI (active filter chips) reflect it.
         */
        _selectedCategory.value = category
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
        /** Clear all filters and trigger a new paging query (keeps category selection in sync). */
        val keepSelectedCategory = _selectedCategory.value
        _filter.value = ProductFilter(category = keepSelectedCategory)
    }

    // PUBLIC_INTERFACE
    fun setFilter(filter: ProductFilter) {
        /** Replace the entire filter object in one update (useful when applying presets). */
        _filter.value = filter
        _selectedCategory.value = filter.category
    }

    // PUBLIC_INTERFACE
    fun clearAll() {
        /** Clear both query and filters (used by "Clear all"). */
        _searchQuery.value = ""
        _selectedCategory.value = null
        _filter.value = ProductFilter()
    }

    // PUBLIC_INTERFACE
    fun applyPreset(query: String, filter: ProductFilter) {
        /** Apply a saved preset (query + filters) as one action. */
        _searchQuery.value = query
        _filter.value = filter
        _selectedCategory.value = filter.category
    }

    // PUBLIC_INTERFACE
    suspend fun loadCategoryPreview(category: String, previewSize: Int): List<Product> {
        /** Lazy-load a preview list for a category section using current search/filter state. */
        val params = activeQueryParams.value
        return repository.fetchCategoryPreview(
            category = category,
            previewSize = previewSize,
            searchQuery = params.query,
            filter = params.filter
        )
    }

    data class QueryParams(
        val query: String,
        val filter: ProductFilter
    )

    private data class EffectiveParams(
        val params: QueryParams,
        val selectedCategory: String?
    )
}
