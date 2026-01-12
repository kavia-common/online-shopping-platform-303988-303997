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
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.databinding.ActivityOrdersBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OrdersActivity : ComponentActivity() {

    private lateinit var binding: ActivityOrdersBinding
    private lateinit var viewModel: OrderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[OrderViewModel::class.java]

        binding = ActivityOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = "Orders"
        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = OrdersAdapter(
            onPay = { viewModel.markPaid(it) },
            onShip = { viewModel.markShipped(it) },
            onDeliver = { viewModel.markDelivered(it) },
            onCancel = { viewModel.cancel(it) }
        )

        val footer = ProductLoadStateAdapter(onRetry = { adapter.retry() })
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter.withLoadStateFooter(footer)

        binding.retryButton.setOnClickListener { adapter.retry() }
        binding.swipeRefresh.setOnRefreshListener { adapter.refresh() }

        // Data
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.orders.collectLatest { pagingData ->
                    adapter.submitData(pagingData)
                }
            }
        }

        // Load states -> containers
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.loadStateFlow.collectLatest { loadStates ->
                    val refresh = loadStates.refresh

                    binding.swipeRefresh.isRefreshing = refresh is LoadState.Loading

                    val isEmpty =
                        refresh is LoadState.NotLoading &&
                            loadStates.append.endOfPaginationReached &&
                            adapter.itemCount == 0

                    val showFullscreenLoading = refresh is LoadState.Loading && adapter.itemCount == 0
                    val initialError = refresh as? LoadState.Error
                    val showFullscreenError = initialError != null && adapter.itemCount == 0

                    if (initialError != null) {
                        binding.errorText.text = initialError.error.message ?: "Failed to load orders."
                    }

                    val durationMs = animDuration(R.integer.anim_crossfade_duration_ms)
                    crossfadeVisibility(binding.fullscreenLoading, showFullscreenLoading, durationMs)
                    crossfadeVisibility(binding.fullscreenError, showFullscreenError, durationMs)
                    crossfadeVisibility(binding.fullscreenEmpty, isEmpty, durationMs)

                    val showList = !showFullscreenLoading && !showFullscreenError && !isEmpty
                    crossfadeVisibility(binding.swipeRefresh, showList, durationMs)
                }
            }
        }

        // Error snackbars from transitions/create/get
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorMessage.collectLatest { msg ->
                    if (!msg.isNullOrBlank()) {
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }
}
