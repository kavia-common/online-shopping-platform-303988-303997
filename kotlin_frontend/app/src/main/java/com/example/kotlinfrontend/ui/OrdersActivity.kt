package com.example.kotlinfrontend.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.databinding.ActivityOrdersBinding
import com.example.kotlinfrontend.model.OrderStatus
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class OrdersActivity : ComponentActivity() {

    private lateinit var binding: ActivityOrdersBinding
    private lateinit var viewModel: OrderViewModel

    private var suppressUiCallbacks = false

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
        setupRecyclerAnimations()

        // Actions
        binding.retryButton.setOnClickListener { adapter.retry() }
        binding.swipeRefresh.setOnRefreshListener { adapter.refresh() }

        binding.emptyClearFiltersButton.setOnClickListener {
            suppressUiCallbacks = true
            try {
                viewModel.clearFilters()
                binding.statusChipGroup.clearCheck()
                binding.emailEditText.setText("")
                setDateButtonLabels(from = null, to = null)
            } finally {
                suppressUiCallbacks = false
            }
            adapter.refresh()
        }

        binding.clearAllButton.setOnClickListener {
            suppressUiCallbacks = true
            try {
                viewModel.clearFilters()
                binding.statusChipGroup.clearCheck()
                binding.emailEditText.setText("")
                setDateButtonLabels(from = null, to = null)
            } finally {
                suppressUiCallbacks = false
            }
            adapter.refresh()
        }

        setupFilterControls(adapter)

        // Data
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.orders.collectLatest { pagingData ->
                    adapter.submitData(pagingData)
                }
            }
        }

        // Render active filter chips + keep date buttons in sync
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeQueryParams.collectLatest { params ->
                    renderActiveFilterChips(
                        status = params.status,
                        email = params.email,
                        from = params.from,
                        to = params.to,
                        onStateChanged = { adapter.refresh() }
                    )
                    if (suppressUiCallbacks) {
                        // Only sync UI fields when we're in programmatic update mode.
                        syncControlsToState(status = params.status, email = params.email, from = params.from, to = params.to)
                    }
                    updateEmptyStateText(params)
                }
            }
        }

        // Load states -> containers (mirrors product list to avoid refresh flicker)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.loadStateFlow.collectLatest { loadStates ->
                    val refresh = loadStates.refresh

                    // SwipeRefreshLayout spinner should follow refresh state.
                    binding.swipeRefresh.isRefreshing = refresh is LoadState.Loading

                    // Inline "updating" when refresh loads but list already has content.
                    binding.inlineProgressContainer.isVisible =
                        refresh is LoadState.Loading && adapter.itemCount > 0

                    val isListEmpty =
                        refresh is LoadState.NotLoading &&
                            loadStates.append.endOfPaginationReached &&
                            adapter.itemCount == 0

                    // Fullscreen loading only for initial load when list empty.
                    val showFullscreenLoading = refresh is LoadState.Loading && adapter.itemCount == 0

                    // Fullscreen error only for initial load error when list empty.
                    val initialError = refresh as? LoadState.Error
                    val showFullscreenError = initialError != null && adapter.itemCount == 0

                    // Fullscreen empty when refresh is not loading and list has no items.
                    val showFullscreenEmpty = isListEmpty

                    if (initialError != null) {
                        binding.errorText.text = initialError.error.message ?: "Failed to load orders."
                    }

                    val durationMs = animDuration(R.integer.anim_crossfade_duration_ms)
                    crossfadeVisibility(binding.fullscreenLoading, showFullscreenLoading, durationMs)
                    crossfadeVisibility(binding.fullscreenError, showFullscreenError, durationMs)
                    crossfadeVisibility(binding.fullscreenEmpty, showFullscreenEmpty, durationMs)

                    // Keep list visible if we already have content (even if refresh is loading / append error etc.)
                    val showList = !showFullscreenLoading && !showFullscreenError && !showFullscreenEmpty
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

    private fun setupRecyclerAnimations() {
        (binding.recyclerView.itemAnimator as? DefaultItemAnimator)?.apply {
            supportsChangeAnimations = false
            addDuration = resources.getInteger(R.integer.anim_item_appear_duration_ms).toLong()
            removeDuration = 120L
            moveDuration = 120L
            changeDuration = 120L
        } ?: run {
            binding.recyclerView.itemAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = false
                addDuration = resources.getInteger(R.integer.anim_item_appear_duration_ms).toLong()
                removeDuration = 120L
                moveDuration = 120L
                changeDuration = 120L
            }
        }
    }

    private fun setupFilterControls(adapter: OrdersAdapter) {
        // Status chips
        binding.statusChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressUiCallbacks) return@setOnCheckedStateChangeListener
            val checkedId = checkedIds.firstOrNull()
            val status = when (checkedId) {
                binding.chipStatusPending.id -> OrderStatus.CREATED
                binding.chipStatusPaid.id -> OrderStatus.PAID
                binding.chipStatusShipped.id -> OrderStatus.SHIPPED
                binding.chipStatusDelivered.id -> OrderStatus.DELIVERED
                binding.chipStatusCancelled.id -> OrderStatus.CANCELLED
                else -> null // Any / none
            }
            viewModel.setStatusFilter(status)
            adapter.refresh()
        }

        // Email text (debounced in VM)
        binding.emailEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressUiCallbacks) return
                viewModel.setEmailFilter(s?.toString().orEmpty())
                adapter.refresh()
            }
        })

        // Date buttons -> DatePickerDialog
        binding.dateFromButton.setOnClickListener {
            showDatePicker(
                title = "From date",
                onPicked = { date ->
                    viewModel.setDateFrom(date)
                    adapter.refresh()
                }
            )
        }

        binding.dateToButton.setOnClickListener {
            showDatePicker(
                title = "To date",
                onPicked = { date ->
                    viewModel.setDateTo(date)
                    adapter.refresh()
                }
            )
        }
    }

    private fun syncControlsToState(status: OrderStatus?, email: String?, from: String?, to: String?) {
        // Email
        val currentEmail = binding.emailEditText.text?.toString().orEmpty()
        val desiredEmail = email.orEmpty()
        if (currentEmail != desiredEmail) {
            binding.emailEditText.setText(desiredEmail)
            binding.emailEditText.setSelection(desiredEmail.length)
        }

        // Status chip group
        val targetChipId = when (status) {
            null -> ViewIds.NO_ID
            OrderStatus.CREATED -> binding.chipStatusPending.id
            OrderStatus.PAID -> binding.chipStatusPaid.id
            OrderStatus.SHIPPED -> binding.chipStatusShipped.id
            OrderStatus.DELIVERED -> binding.chipStatusDelivered.id
            OrderStatus.CANCELLED -> binding.chipStatusCancelled.id
            OrderStatus.UNKNOWN -> ViewIds.NO_ID
        }
        if (targetChipId == ViewIds.NO_ID) {
            binding.statusChipGroup.clearCheck()
        } else if (binding.statusChipGroup.checkedChipId != targetChipId) {
            binding.statusChipGroup.check(targetChipId)
        }

        setDateButtonLabels(from = from, to = to)
    }

    private fun setDateButtonLabels(from: String?, to: String?) {
        binding.dateFromButton.text = from?.let { "From: $it" } ?: "From"
        binding.dateToButton.text = to?.let { "To: $it" } ?: "To"
    }

    private fun updateEmptyStateText(params: OrderViewModel.QueryParams) {
        val hasFilters = params.hasActiveFilters()
        val title = if (hasFilters) "No orders match your filters." else "No orders yet."
        val subtitle = if (hasFilters) {
            "Try adjusting or clearing filters."
        } else {
            "Create an order from the Products screen."
        }

        binding.emptyTitle.text = title
        binding.emptySubtitle.text = subtitle
        binding.emptyClearFiltersButton.isVisible = hasFilters
    }

    private fun renderActiveFilterChips(
        status: OrderStatus?,
        email: String?,
        from: String?,
        to: String?,
        onStateChanged: () -> Unit
    ) {
        binding.activeFiltersChipGroup.removeAllViews()

        fun addRemovableChip(text: String, onRemove: () -> Unit) {
            val chip = Chip(this, null, 0).apply {
                setChipDrawable(
                    com.google.android.material.chip.ChipDrawable.createFromAttributes(
                        this@OrdersActivity,
                        null,
                        0,
                        R.style.Widget_KotlinFrontend_Chip_Filter
                    )
                )
                this.text = text
                isCloseIconVisible = true
                setOnCloseIconClickListener { onRemove() }
            }
            binding.activeFiltersChipGroup.addView(chip)
        }

        status?.let {
            addRemovableChip("Status: ${statusLabelForChip(it)}") {
                suppressUiCallbacks = true
                try {
                    viewModel.setStatusFilter(null)
                    binding.statusChipGroup.clearCheck()
                } finally {
                    suppressUiCallbacks = false
                }
                onStateChanged()
            }
        }

        if (!email.isNullOrBlank()) {
            addRemovableChip("Email: $email") {
                suppressUiCallbacks = true
                try {
                    viewModel.setEmailFilter("")
                    binding.emailEditText.setText("")
                } finally {
                    suppressUiCallbacks = false
                }
                onStateChanged()
            }
        }

        if (!from.isNullOrBlank() || !to.isNullOrBlank()) {
            val label = when {
                !from.isNullOrBlank() && !to.isNullOrBlank() -> "Date: $from–$to"
                !from.isNullOrBlank() -> "Date: from $from"
                else -> "Date: to $to"
            }
            addRemovableChip(label) {
                suppressUiCallbacks = true
                try {
                    viewModel.setDateFrom(null)
                    viewModel.setDateTo(null)
                    setDateButtonLabels(from = null, to = null)
                } finally {
                    suppressUiCallbacks = false
                }
                onStateChanged()
            }
        }

        binding.activeFiltersChipGroup.isVisible = binding.activeFiltersChipGroup.childCount > 0
    }

    private fun statusLabelForChip(status: OrderStatus): String {
        return when (status) {
            OrderStatus.CREATED -> "Pending"
            OrderStatus.PAID -> "Paid"
            OrderStatus.SHIPPED -> "Shipped"
            OrderStatus.DELIVERED -> "Delivered"
            OrderStatus.CANCELLED -> "Cancelled"
            OrderStatus.UNKNOWN -> "Unknown"
        }
    }

    private fun showDatePicker(title: String, onPicked: (yyyyMmDd: String) -> Unit) {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        // Simple DatePickerDialog (keeps dependencies minimal; matches request).
        val dialog = DatePickerDialog(
            this,
            { _, y, m, d ->
                val yyyyMmDd = String.format(Locale.US, "%04d-%02d-%02d", y, (m + 1), d)
                onPicked(yyyyMmDd)
            },
            year,
            month,
            day
        )
        dialog.setTitle(title)
        dialog.show()
    }

    private object ViewIds {
        const val NO_ID = -1
    }
}
