package com.example.kotlinfrontend.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kotlinfrontend.databinding.ActivityProductListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProductListActivity : ComponentActivity() {

    private lateinit var binding: ActivityProductListBinding

    private lateinit var viewModel: ProductListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[ProductListViewModel::class.java]

        binding = ActivityProductListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val productAdapter = ProductAdapter()

        val footer = ProductLoadStateAdapter(onRetry = { productAdapter.retry() })
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = productAdapter.withLoadStateFooter(footer)

        setupSearchAndFilters(productAdapter)

        // Pull-to-refresh triggers a paging refresh (re-runs current query+filters; doesn't over-fetch).
        binding.swipeRefresh.setOnRefreshListener {
            productAdapter.refresh()
        }

        binding.retryButton.setOnClickListener {
            productAdapter.retry()
        }

        // Collect paging data
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.products.collectLatest { pagingData ->
                    productAdapter.submitData(pagingData)
                }
            }
        }

        // Keep empty-state messaging tied to active search/filter inputs
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeQueryParams.collectLatest { params ->
                    updateEmptyStateText(query = params.query, hasActiveFilters = params.filter.isActive())
                }
            }
        }

        // Manage loading/error/empty states based on LoadState
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                productAdapter.loadStateFlow.collectLatest { loadStates ->
                    val refresh = loadStates.refresh

                    // SwipeRefreshLayout spinner should follow refresh state.
                    binding.swipeRefresh.isRefreshing = refresh is LoadState.Loading

                    // Inline progress: show when refresh is loading but list already has content
                    // (typical when user changes query/filters while scrolled / already loaded some items).
                    binding.inlineProgressContainer.isVisible =
                        refresh is LoadState.Loading && productAdapter.itemCount > 0

                    val isListEmpty =
                        refresh is LoadState.NotLoading &&
                            loadStates.append.endOfPaginationReached &&
                            productAdapter.itemCount == 0

                    // Fullscreen loading only for initial load when list is empty.
                    binding.fullscreenLoading.isVisible =
                        refresh is LoadState.Loading && productAdapter.itemCount == 0

                    // Fullscreen empty state
                    binding.fullscreenEmpty.isVisible = isListEmpty

                    // Fullscreen error only for initial load error when list is empty.
                    val initialError = refresh as? LoadState.Error
                    binding.fullscreenError.isVisible =
                        initialError != null && productAdapter.itemCount == 0
                    if (initialError != null) {
                        binding.errorText.text = initialError.error.message ?: "Failed to load products."
                    }

                    // Keep list visible if we already have content (even if append errors happen).
                    binding.recyclerView.isVisible =
                        !binding.fullscreenLoading.isVisible &&
                            !binding.fullscreenError.isVisible &&
                            !binding.fullscreenEmpty.isVisible
                }
            }
        }
    }

    private fun setupSearchAndFilters(productAdapter: ProductAdapter) {
        // Search input -> ViewModel state (debounced inside VM)
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString().orEmpty())
                // Pager is recreated via flows; we still explicitly refresh so list updates promptly
                // and SwipeRefreshLayout/LoadState reflect the new query.
                productAdapter.refresh()
            }
        })

        // Category spinner
        val categories = listOf("All", "Electronics", "Clothing", "Home", "Books")
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )
        binding.categorySpinner.adapter = spinnerAdapter
        binding.categorySpinner.setSelection(0)
        binding.categorySpinner.setOnItemSelectedListener { _, _, position, _ ->
            val selected = categories[position]
            viewModel.setCategory(if (selected == "All") null else selected)
            productAdapter.refresh()
        }

        // Price chips (simple preset ranges)
        binding.priceChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            when (checkedId) {
                binding.chipUnder25.id -> {
                    viewModel.setMinPriceCents(null)
                    viewModel.setMaxPriceCents(2500)
                }
                binding.chip25to40.id -> {
                    viewModel.setMinPriceCents(2500)
                    viewModel.setMaxPriceCents(4000)
                }
                binding.chipOver40.id -> {
                    viewModel.setMinPriceCents(4000)
                    viewModel.setMaxPriceCents(null)
                }
                else -> {
                    // Any / none selected
                    viewModel.setMinPriceCents(null)
                    viewModel.setMaxPriceCents(null)
                }
            }
            productAdapter.refresh()
        }
    }

    private fun updateEmptyStateText(query: String, hasActiveFilters: Boolean) {
        val trimmed = query.trim()
        val title = if (trimmed.isNotBlank()) {
            "No results for “$trimmed”."
        } else if (hasActiveFilters) {
            "No products match your filters."
        } else {
            "No products found."
        }

        val subtitle = if (trimmed.isNotBlank() || hasActiveFilters) {
            "Try adjusting your search or filters."
        } else {
            "Pull to refresh to try again."
        }

        binding.emptyTitle.text = title
        binding.emptySubtitle.text = subtitle
    }
}

/**
 * Small helper to avoid verbose AdapterView.OnItemSelectedListener boilerplate.
 */
private fun android.widget.Spinner.setOnItemSelectedListener(
    listener: (parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) -> Unit
) {
    this.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: android.widget.AdapterView<*>,
            view: android.view.View?,
            position: Int,
            id: Long
        ) {
            listener(parent, view, position, id)
        }

        override fun onNothingSelected(parent: android.widget.AdapterView<*>) = Unit
    }
}
