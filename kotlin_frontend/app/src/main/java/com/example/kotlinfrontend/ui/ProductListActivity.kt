package com.example.kotlinfrontend.ui

import android.os.Bundle
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

        // Pull-to-refresh triggers a paging refresh (doesn't over-fetch; reuses PagingSource logic).
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

        // Manage loading/error/empty states based on LoadState
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                productAdapter.loadStateFlow.collectLatest { loadStates ->
                    val refresh = loadStates.refresh

                    // SwipeRefreshLayout spinner should follow refresh state.
                    binding.swipeRefresh.isRefreshing = refresh is LoadState.Loading

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
}
