package com.example.kotlinfrontend.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import androidx.recyclerview.widget.LinearSnapHelper
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.data.AppRepositories
import com.example.kotlinfrontend.data.FilterPresetStore
import com.example.kotlinfrontend.databinding.ActivityProductListBinding
import com.example.kotlinfrontend.model.FilterPreset
import com.example.kotlinfrontend.model.Product
import com.example.kotlinfrontend.model.ProductFilter
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProductListActivity : ComponentActivity() {

    private fun ensureCartIdentityOrPrompt(onReady: () -> Unit) {
        // Helper used from multiple member functions (including rebuildGroupedSections).
        val cartRepo = AppRepositories.cart(this)
        val email = cartRepo.activeEmail.value
        if (!email.isNullOrBlank()) {
            onReady()
            return
        }
        CartIdentityPrompter.promptForEmail(
            context = this,
            onEmailSaved = { entered ->
                cartRepo.setActiveEmail(entered)
                onReady()
            }
        )
    }

    private fun setupCategoryChipsAndGrouping(productAdapter: ProductAdapter) {
        // Observe categories list from backend and render chips.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.categories.collectLatest { cats ->
                    renderCategoryChips(
                        categories = cats,
                        selected = viewModel.selectedCategory.value,
                        onSelected = { category ->
                            if (suppressUiCallbacks) return@renderCategoryChips
                            categorySectionLoaded.clear()
                            viewModel.selectCategory(category)
                            // Single-category view should refresh paging when switching categories.
                            productAdapter.refresh()
                            // Grouped mode sections need to be rebuilt for new search/filter state.
                            rebuildGroupedSections()
                        }
                    )
                }
            }
        }

        // Keep chips in sync if selection changes via presets/active-filter removals/clear-all.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedCategory.collectLatest { selected ->
                    renderCategoryChips(
                        categories = viewModel.categories.value,
                        selected = selected,
                        onSelected = { category ->
                            if (suppressUiCallbacks) return@renderCategoryChips
                            categorySectionLoaded.clear()
                            viewModel.selectCategory(category)
                            productAdapter.refresh()
                            rebuildGroupedSections()
                        }
                    )
                    toggleGroupedMode(selectedCategory = selected)
                }
            }
        }

        // When query/filter changes, grouped mode content should reflect it.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeQueryParams.collectLatest {
                    if (viewModel.selectedCategory.value == null) {
                        categorySectionLoaded.clear()
                        rebuildGroupedSections()
                    }
                }
            }
        }

        // Initial build (in case categories already loaded fast)
        rebuildGroupedSections()
    }

    private fun toggleGroupedMode(selectedCategory: String?) {
        val grouped = selectedCategory == null
        val durationMs = animDuration(R.integer.anim_crossfade_duration_ms)

        // When grouped, show custom scroll sections; when not grouped, show paged list.
        crossfadeVisibility(binding.groupedContainer, grouped, durationMs)
        crossfadeVisibility(binding.swipeRefresh, !grouped, durationMs)
    }

    private fun renderCategoryChips(
        categories: List<String>,
        selected: String?,
        onSelected: (String?) -> Unit
    ) {
        // RecyclerView-based chip bar for smoother scrolling + snap-to-item.
        if (binding.categoryChipsRecycler.adapter == null) {
            val adapter = CategoryChipAdapter(onSelected = { category ->
                onSelected(category)
            })

            binding.categoryChipsRecycler.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.categoryChipsRecycler.adapter = adapter

            // Spacing (start/end padding + inter-item spacing).
            val startEnd = resources.displayMetrics.density.times(12).toInt()
            val spacing = resources.displayMetrics.density.times(8).toInt()
            binding.categoryChipsRecycler.addItemDecoration(
                HorizontalSpaceItemDecoration(
                    startPaddingPx = startEnd,
                    itemSpacingPx = spacing,
                    endPaddingPx = startEnd
                )
            )

            // Snap chips to align nicely after fling.
            LinearSnapHelper().attachToRecyclerView(binding.categoryChipsRecycler)

            // Keep accessibility and performance stable.
            binding.categoryChipsRecycler.isNestedScrollingEnabled = false
            binding.categoryChipsRecycler.itemAnimator = null
        }

        (binding.categoryChipsRecycler.adapter as? CategoryChipAdapter)
            ?.submitCategories(categories = categories, selected = selected)
    }

    private fun rebuildGroupedSections() {
        // Inflate a scrollable container with per-category section cards.
        binding.groupedContainer.removeAllViews()

        val inflater = layoutInflater
        val container = inflater.inflate(
            R.layout.view_category_sections_container,
            binding.groupedContainer,
            false
        )
        binding.groupedContainer.addView(container)

        val sectionsContainer = container.findViewById<android.widget.LinearLayout>(R.id.sectionsContainer)
        val scrollView = container.findViewById<android.widget.ScrollView>(R.id.groupedScroll)

        val categories = viewModel.categories.value
        if (categories.isEmpty()) {
            // No categories available; show nothing (user can still use paged list by selecting a chip later).
            return
        }

        val cartRepo = AppRepositories.cart(this)

        categories.forEach { category ->
            val sectionView = CategorySectionView(this)
            sectionView.setCartCallbacks(
                cartQtyProvider = { productId ->
                    cartRepo.items.value.firstOrNull { it.productId == productId }?.quantity ?: 0
                },
                onAddToCart = { product: Product, qty: Int ->
                    ensureCartIdentityOrPrompt {
                        cartRepo.addItem(product, qty)
                        Snackbar.make(binding.root, "Added to cart.", Snackbar.LENGTH_SHORT).show()
                        // Force redraw of preview rows so qty text reflects latest state.
                        rebuildGroupedSections()
                    }
                },
                onIncrementInCart = { product: Product ->
                    ensureCartIdentityOrPrompt {
                        cartRepo.addItem(product, 1)
                        Snackbar.make(binding.root, "Updated quantity.", Snackbar.LENGTH_SHORT).show()
                        rebuildGroupedSections()
                    }
                },
                onDecrementInCart = { product: Product ->
                    ensureCartIdentityOrPrompt {
                        val current = cartRepo.items.value.firstOrNull { it.productId == product.id }?.quantity ?: 0
                        val newQty = current - 1
                        cartRepo.updateQty(product.id, newQty)
                        Snackbar.make(
                            binding.root,
                            if (newQty <= 0) "Removed item." else "Updated quantity.",
                            Snackbar.LENGTH_SHORT
                        ).show()
                        rebuildGroupedSections()
                    }
                }
            )

            sectionView.bindHeader(category) {
                // See all: switch to the category-specific paged list
                viewModel.selectCategory(category)
            }
            sectionView.showLoading(false)
            sectionView.showError(null, visible = false)
            sectionView.submitItems(emptyList())

            sectionsContainer.addView(sectionView)

            // Lazy-load when scrolled near this section.
            sectionView.post {
                maybeLoadSectionIfVisible(sectionView, category)
            }
        }

        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val childCount = sectionsContainer.childCount
            for (i in 0 until childCount) {
                val v = sectionsContainer.getChildAt(i) as? CategorySectionView ?: continue
                val categoryTitle = (v.findViewById<android.widget.TextView>(com.example.kotlinfrontend.R.id.categoryTitle))
                    ?.text
                    ?.toString()
                if (!categoryTitle.isNullOrBlank()) {
                    maybeLoadSectionIfVisible(v, categoryTitle)
                }
            }
        }
    }

    private fun maybeLoadSectionIfVisible(sectionView: CategorySectionView, category: String) {
        if (categorySectionLoaded.contains(category)) return

        // Simple visibility heuristic: if section's top is within ~1.5 screens from the current viewport top, load it.
        val scrollParent = sectionView.parent?.parent as? android.widget.ScrollView ?: return
        val scrollY = scrollParent.scrollY
        val height = scrollParent.height
        val sectionTop = sectionView.top

        val loadThreshold = scrollY + (height * 3 / 2)
        if (sectionTop <= loadThreshold) {
            categorySectionLoaded.add(category)
            loadSectionPreview(sectionView, category)
        }
    }

    private fun loadSectionPreview(sectionView: CategorySectionView, category: String) {
        lifecycleScope.launch {
            sectionView.showError(null, visible = false)
            sectionView.showLoading(true)
            try {
                val preview = viewModel.loadCategoryPreview(category = category, previewSize = 6)
                sectionView.submitItems(preview)
            } catch (t: Throwable) {
                sectionView.showError(t.message, visible = true)
            } finally {
                sectionView.showLoading(false)
            }
        }
    }

    private lateinit var binding: ActivityProductListBinding

    private lateinit var viewModel: ProductListViewModel
    private lateinit var orderViewModel: OrderViewModel

    // Backing store for preset CRUD
    private lateinit var presetStore: FilterPresetStore

    private var suppressUiCallbacks = false

    // Grouped mode: lazy-load previews only when section becomes visible.
    private val categorySectionLoaded = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[ProductListViewModel::class.java]
        orderViewModel = ViewModelProvider(this)[OrderViewModel::class.java]
        presetStore = FilterPresetStore(this)

        binding = ActivityProductListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cart integration (persistent, process-death safe + backend synced).
        val cartRepo = AppRepositories.cart(this)

        val productAdapter = ProductAdapter(
            cartQtyProvider = { productId ->
                cartRepo.items.value.firstOrNull { it.productId == productId }?.quantity ?: 0
            }
        ).apply {
            // These callbacks need to reference the adapter instance; using apply avoids "val used in its own initializer".
            setCartCallbacks(
                onAddToCart = { product, qty ->
                    ensureCartIdentityOrPrompt {
                        cartRepo.addItem(product, qty)
                        Snackbar.make(binding.root, "Added to cart.", Snackbar.LENGTH_SHORT).show()
                        this@apply.notifyDataSetChanged()
                    }
                },
                onIncrementInCart = { product ->
                    ensureCartIdentityOrPrompt {
                        cartRepo.addItem(product, 1)
                        Snackbar.make(binding.root, "Updated quantity.", Snackbar.LENGTH_SHORT).show()
                        this@apply.notifyDataSetChanged()
                    }
                },
                onDecrementInCart = { product ->
                    ensureCartIdentityOrPrompt {
                        val current = cartRepo.items.value.firstOrNull { it.productId == product.id }?.quantity ?: 0
                        val newQty = current - 1
                        cartRepo.updateQty(product.id, newQty)
                        Snackbar.make(
                            binding.root,
                            if (newQty <= 0) "Removed item." else "Updated quantity.",
                            Snackbar.LENGTH_SHORT
                        ).show()
                        this@apply.notifyDataSetChanged()
                    }
                }
            )
        }

        val footer = ProductLoadStateAdapter(onRetry = { productAdapter.retry() })
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = productAdapter.withLoadStateFooter(footer)

        // Toolbar: notifications + cart buttons with badges
        binding.toolbar.inflateMenu(R.menu.menu_product_list)

        val notificationsItem = binding.toolbar.menu.findItem(R.id.action_notifications)
        val notificationsBadgeController = NotificationsBadgeController(binding.toolbar, notificationsItem)
        notificationsBadgeController.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        val cartItem = binding.toolbar.menu.findItem(R.id.action_cart)
        val cartBadgeController = CartBadgeController(binding.toolbar, cartItem)
        cartBadgeController.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_notifications -> {
                    startActivity(Intent(this, NotificationsActivity::class.java))
                    true
                }
                R.id.action_cart -> {
                    startActivity(Intent(this, CartActivity::class.java))
                    true
                }
                else -> false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppRepositories.unreadNotificationsCount(this@ProductListActivity).collectLatest { count ->
                    notificationsBadgeController.setCount(count)
                    // Announce badge changes for accessibility (only for meaningful non-zero changes).
                    if (count > 0) {
                        binding.root.announceForAccessibility("You have $count unread notifications.")
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppRepositories.cartItemCount(this@ProductListActivity).collectLatest { count ->
                    cartBadgeController.setCount(count)
                }
            }
        }

        // If identity exists, refresh cart from backend once when screen starts.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cartRepo.activeEmail.collectLatest { email ->
                    if (!email.isNullOrBlank()) {
                        cartRepo.ensureCartLoaded()
                    }
                }
            }
        }

        setupRecyclerAnimations()
        setupSearchAndFilters(productAdapter)
        setupPresetsAndClearAll(productAdapter)
        setupCategoryChipsAndGrouping(productAdapter)

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

        // Legacy category spinner is hidden; category browsing is now via chips.
        binding.legacyCategorySpinnerRow.isVisible = false

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

        // Category chips selection is driven by viewModel.selectedCategory collector.
        // (No-op here; we avoid duplicating state updates.)

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
                viewModel.selectCategory(null)
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
