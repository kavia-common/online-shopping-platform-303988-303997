package com.example.kotlinfrontend.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
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
import com.example.kotlinfrontend.data.FilterPresetStore
import com.example.kotlinfrontend.databinding.ActivityProductListBinding
import com.example.kotlinfrontend.model.FilterPreset
import com.example.kotlinfrontend.model.ProductFilter
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProductListActivity : ComponentActivity() {

    private lateinit var binding: ActivityProductListBinding

    private lateinit var viewModel: ProductListViewModel
    private lateinit var orderViewModel: OrderViewModel

    // Backing store for preset CRUD
    private lateinit var presetStore: FilterPresetStore

    // We need these for syncing UI <-> state when applying presets
    private val categories = listOf("All", "Electronics", "Clothing", "Home", "Books")

    private var suppressUiCallbacks = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[ProductListViewModel::class.java]
        orderViewModel = ViewModelProvider(this)[OrderViewModel::class.java]
        presetStore = FilterPresetStore(this)

        binding = ActivityProductListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val productAdapter = ProductAdapter()

        val footer = ProductLoadStateAdapter(onRetry = { productAdapter.retry() })
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = productAdapter.withLoadStateFooter(footer)

        setupRecyclerAnimations()
        setupSearchAndFilters(productAdapter)
        setupPresetsAndClearAll(productAdapter)

        binding.openOrdersButton.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }

        binding.createOrderButton.setOnClickListener {
            // Minimal E2E hook: create order with a fixed sample payload, then jump to Orders.
            orderViewModel.createOrderFromSample()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                orderViewModel.lastCreatedOrderId.collectLatest { id ->
                    if (!id.isNullOrBlank()) {
                        Snackbar.make(binding.root, "Order created: #$id", Snackbar.LENGTH_LONG).show()
                        startActivity(Intent(this@ProductListActivity, OrdersActivity::class.java))
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                orderViewModel.errorMessage.collectLatest { msg ->
                    if (!msg.isNullOrBlank()) {
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                        orderViewModel.clearError()
                    }
                }
            }
        }

        // Pull-to-refresh triggers a paging refresh (re-runs current query+filters; doesn't over-fetch).
        binding.swipeRefresh.setOnRefreshListener {
            productAdapter.refresh()
        }

        binding.retryButton.setOnClickListener {
            productAdapter.retry()
        }

        binding.emptyClearFiltersButton.setOnClickListener {
            // Clear filters only (keep query), then refresh.
            suppressUiCallbacks = true
            try {
                viewModel.clearFilters()
                binding.priceChipGroup.clearCheck()
                binding.categorySpinner.setSelection(0)
            } finally {
                suppressUiCallbacks = false
            }
            productAdapter.refresh()
        }

        // Collect paging data
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.products.collectLatest { pagingData ->
                    productAdapter.submitData(pagingData)
                }
            }
        }

        // Keep empty-state messaging tied to active search/filter inputs + render active chips
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeQueryParams.collectLatest { params ->
                    updateEmptyStateText(query = params.query, hasActiveFilters = params.filter.isActive())
                    renderActiveFilterChips(
                        query = params.query,
                        filter = params.filter,
                        onStateChanged = { productAdapter.refresh() }
                    )
                    // If preset applied programmatically, also ensure controls reflect current state.
                    syncFilterControlsToState(query = params.query, filter = params.filter)
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
                    val showFullscreenLoading = refresh is LoadState.Loading && productAdapter.itemCount == 0

                    // Fullscreen error only for initial load error when list is empty.
                    val initialError = refresh as? LoadState.Error
                    val showFullscreenError = initialError != null && productAdapter.itemCount == 0

                    // Fullscreen empty state
                    val showFullscreenEmpty = isListEmpty

                    if (initialError != null) {
                        binding.errorText.text = initialError.error.message ?: "Failed to load products."
                    }

                    // Crossfade between containers (subtle, avoids flicker during refresh).
                    val durationMs = animDuration(R.integer.anim_crossfade_duration_ms)
                    crossfadeVisibility(binding.fullscreenLoading, showFullscreenLoading, durationMs)
                    crossfadeVisibility(binding.fullscreenError, showFullscreenError, durationMs)
                    crossfadeVisibility(binding.fullscreenEmpty, showFullscreenEmpty, durationMs)

                    // Keep list visible if we already have content (even if append errors happen).
                    val showList =
                        !showFullscreenLoading && !showFullscreenError && !showFullscreenEmpty

                    crossfadeVisibility(binding.swipeRefresh, showList, durationMs)
                }
            }
        }
    }

    private fun setupRecyclerAnimations() {
        // Keep animations subtle and diff/paging friendly.
        (binding.recyclerView.itemAnimator as? DefaultItemAnimator)?.apply {
            supportsChangeAnimations = false // prevents blink on updates
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

    private fun setupPresetsAndClearAll(productAdapter: ProductAdapter) {
        binding.clearAllButton.setOnClickListener {
            // One action to reset everything.
            suppressUiCallbacks = true
            try {
                viewModel.clearAll()
                // UI controls will be synced via collector
                binding.priceChipGroup.clearCheck()
                binding.categorySpinner.setSelection(0)
            } finally {
                suppressUiCallbacks = false
            }
            productAdapter.refresh()
        }

        binding.savePresetButton.setOnClickListener {
            showSavePresetDialog(
                onSave = { presetName ->
                    val params = viewModel.activeQueryParams.value
                    val preset = FilterPreset(
                        name = presetName,
                        query = params.query,
                        filter = params.filter
                    )
                    presetStore.upsert(preset)
                    Snackbar.make(binding.root, "Preset saved.", Snackbar.LENGTH_SHORT).show()
                }
            )
        }

        binding.presetsButton.setOnClickListener {
            showPresetsDialog(
                onApply = { preset ->
                    suppressUiCallbacks = true
                    try {
                        viewModel.applyPreset(query = preset.query, filter = preset.filter)
                        // UI controls will be synced via collector
                    } finally {
                        suppressUiCallbacks = false
                    }
                    productAdapter.refresh()
                },
                onDelete = { preset ->
                    presetStore.deleteByName(preset.name)
                    Snackbar.make(binding.root, "Preset deleted.", Snackbar.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun setupSearchAndFilters(productAdapter: ProductAdapter) {
        // Search input -> ViewModel state (debounced inside VM)
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressUiCallbacks) return
                viewModel.setSearchQuery(s?.toString().orEmpty())
                // Pager is recreated via flows; we still explicitly refresh so list updates promptly
                // and SwipeRefreshLayout/LoadState reflect the new query.
                productAdapter.refresh()
            }
        })

        // Category spinner
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )
        binding.categorySpinner.adapter = spinnerAdapter
        binding.categorySpinner.setSelection(0)
        binding.categorySpinner.setOnItemSelectedListener { _, _, position, _ ->
            if (suppressUiCallbacks) return@setOnItemSelectedListener
            val selected = categories[position]
            viewModel.setCategory(if (selected == "All") null else selected)
            productAdapter.refresh()
        }

        // Price chips (simple preset ranges)
        binding.priceChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (suppressUiCallbacks) return@setOnCheckedStateChangeListener
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

    private fun syncFilterControlsToState(query: String, filter: ProductFilter) {
        // Keep UI consistent with state (especially when applying presets).
        if (!suppressUiCallbacks) return

        // Search
        val current = binding.searchEditText.text?.toString().orEmpty()
        if (current != query) {
            binding.searchEditText.setText(query)
            binding.searchEditText.setSelection(query.length)
        }

        // Category spinner
        val spinnerPos = when (filter.category) {
            null -> 0
            else -> categories.indexOfFirst { it == filter.category }.let { if (it >= 0) it else 0 }
        }
        if (binding.categorySpinner.selectedItemPosition != spinnerPos) {
            binding.categorySpinner.setSelection(spinnerPos)
        }

        // Price chips (map filter min/max into the 3 known options; otherwise clear selection)
        val targetChipId = when {
            filter.minPriceCents == null && filter.maxPriceCents == 2500 -> binding.chipUnder25.id
            filter.minPriceCents == 2500 && filter.maxPriceCents == 4000 -> binding.chip25to40.id
            filter.minPriceCents == 4000 && filter.maxPriceCents == null -> binding.chipOver40.id
            else -> ViewIds.NO_ID
        }
        if (targetChipId == ViewIds.NO_ID) {
            binding.priceChipGroup.clearCheck()
        } else if (binding.priceChipGroup.checkedChipId != targetChipId) {
            binding.priceChipGroup.check(targetChipId)
        }
    }

    private fun renderActiveFilterChips(
        query: String,
        filter: ProductFilter,
        onStateChanged: () -> Unit
    ) {
        // Chips below the search bar that reflect current state and can be removed individually.
        binding.activeFiltersChipGroup.removeAllViews()

        fun addRemovableChip(text: String, onRemove: () -> Unit) {
            val chip = Chip(this, null, 0).apply {
                // Apply our Material-based polish style.
                setChipDrawable(
                    com.google.android.material.chip.ChipDrawable.createFromAttributes(
                        this@ProductListActivity,
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

        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotBlank()) {
            addRemovableChip("Search: “$trimmedQuery”") {
                suppressUiCallbacks = true
                try {
                    viewModel.setSearchQuery("")
                    // UI controls will be synced via collector
                } finally {
                    suppressUiCallbacks = false
                }
                onStateChanged()
            }
        }

        filter.category?.let { category ->
            addRemovableChip("Category: $category") {
                viewModel.setCategory(null)
                onStateChanged()
            }
        }

        if (filter.minPriceCents != null || filter.maxPriceCents != null) {
            val minText = filter.minPriceCents?.let { centsToDollarsText(it) } ?: ""
            val maxText = filter.maxPriceCents?.let { centsToDollarsText(it) } ?: ""
            val label = when {
                filter.minPriceCents == null -> "Price: ≤ $maxText"
                filter.maxPriceCents == null -> "Price: ≥ $minText"
                else -> "Price: $minText–$maxText"
            }
            addRemovableChip(label) {
                viewModel.setMinPriceCents(null)
                viewModel.setMaxPriceCents(null)
                // also clear the price selection UI
                suppressUiCallbacks = true
                try {
                    binding.priceChipGroup.clearCheck()
                } finally {
                    suppressUiCallbacks = false
                }
                onStateChanged()
            }
        }

        binding.activeFiltersChipGroup.isVisible = binding.activeFiltersChipGroup.childCount > 0
    }

    private fun showSavePresetDialog(onSave: (name: String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Preset name"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Save preset")
            .setMessage("Save current search + filters as a preset.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text?.toString().orEmpty().trim()
                if (name.isBlank()) {
                    Snackbar.make(binding.root, "Name can't be empty.", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onSave(name)
            }
            .show()
    }

    private fun showPresetsDialog(
        onApply: (preset: FilterPreset) -> Unit,
        onDelete: (preset: FilterPreset) -> Unit
    ) {
        val presets = presetStore.getAll()
        if (presets.isEmpty()) {
            Snackbar.make(binding.root, "No presets yet. Use “Save preset”.", Snackbar.LENGTH_SHORT).show()
            return
        }

        val presetNames = presets.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Presets")
            .setItems(presetNames) { dialog, which ->
                dialog.dismiss()
                val preset = presets[which]
                showPresetActionDialog(preset, onApply = onApply, onDelete = onDelete)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPresetActionDialog(
        preset: FilterPreset,
        onApply: (preset: FilterPreset) -> Unit,
        onDelete: (preset: FilterPreset) -> Unit
    ) {
        val actions = arrayOf("Apply", "Delete")
        MaterialAlertDialogBuilder(this)
            .setTitle(preset.name)
            .setItems(actions) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> onApply(preset)
                    1 -> confirmDeletePreset(preset, onDelete)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeletePreset(preset: FilterPreset, onDelete: (preset: FilterPreset) -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete preset?")
            .setMessage("Delete “${preset.name}”?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> onDelete(preset) }
            .show()
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

        // Show/hide CTA depending on whether there's anything to clear.
        binding.emptyClearFiltersButton.isVisible = hasActiveFilters
    }

    private object ViewIds {
        const val NO_ID = -1
    }
}



// PUBLIC_INTERFACE
fun centsToDollarsText(cents: Int): String {
    /** Format cents (e.g., 2500) into a dollar string (e.g., "$25.00"). */
    return "$" + String.format("%.2f", cents / 100.0)
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
