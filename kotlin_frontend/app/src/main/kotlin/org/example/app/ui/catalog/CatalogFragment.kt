package org.example.app.ui.catalog

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.example.app.R
import org.example.app.data.Product
import org.example.app.data.ShopRepository
import java.util.Locale

class CatalogFragment : Fragment(R.layout.fragment_catalog) {

    private var selectedCategoryId: String? = null
    private var searchQuery: String = ""

    private var selectedSortOption: SortOption = SortOption.RELEVANCE

    private lateinit var rvProducts: RecyclerView
    private lateinit var tvEmptyResults: TextView
    private lateinit var adapter: ProductAdapter

    private lateinit var etSearch: TextInputEditText

    // Recent searches UI
    private lateinit var recentSearchesRow: LinearLayout
    private lateinit var recentSearchesScroll: HorizontalScrollView
    private lateinit var recentSearchesContainer: LinearLayout
    private lateinit var btnClearRecentSearches: MaterialButton

    // Catalog filter/sort UI
    private lateinit var sortSpinner: android.widget.Spinner
    private lateinit var categoryContainer: LinearLayout

    // Flag to avoid persisting while we're programmatically restoring state.
    private var isRestoringCatalogPrefs: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSearchRunnable: Runnable? = null

    private enum class SortOption {
        RELEVANCE,
        PRICE_LOW_TO_HIGH,
        PRICE_HIGH_TO_LOW,
        NAME_A_TO_Z
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvProducts = view.findViewById(R.id.rv_products)
        tvEmptyResults = view.findViewById(R.id.tv_empty_results)
        etSearch = view.findViewById(R.id.et_search)

        recentSearchesRow = view.findViewById(R.id.recent_searches_row)
        recentSearchesScroll = view.findViewById(R.id.recent_searches_scroll)
        recentSearchesContainer = view.findViewById(R.id.recent_searches_container)
        btnClearRecentSearches = view.findViewById(R.id.btn_clear_recent_searches)

        categoryContainer = view.findViewById(R.id.category_container)
        sortSpinner = view.findViewById(R.id.spinner_sort)

        rvProducts.layoutManager = LinearLayoutManager(requireContext())

        adapter = ProductAdapter { product ->
            findNavController().navigate(
                R.id.action_catalog_to_productDetails,
                bundleOf("productId" to product.id)
            )
        }
        rvProducts.adapter = adapter

        // Sorting dropdown (kept within existing sidebar panel)
        setupSortDropdown(view)

        // Restore persisted catalog filter/sort before rendering category chips, so UI uses restored values.
        restoreCatalogPreferences()

        // Sidebar category filters
        renderCategoryFilters(categoryContainer)

        view.findViewById<MaterialButton>(R.id.btn_go_to_cart).setOnClickListener {
            findNavController().navigate(R.id.action_catalog_to_cart)
        }

        setupDebouncedSearch()
        setupRecentSearchesUi()

        // Restore last search (if any) to make search feel continuous across restarts.
        // We only pre-fill if user hasn't typed anything already.
        val lastQuery = ShopRepository.getRecentSearches().firstOrNull().orEmpty()
        if (searchQuery.isBlank() && lastQuery.isNotBlank()) {
            searchQuery = lastQuery
            // setText triggers the TextWatcher; this is desired to refresh + record consistently.
            etSearch.setText(lastQuery)
            etSearch.setSelection(lastQuery.length)
        } else {
            refreshProducts()
        }

        // Ensure recent searches are shown immediately on entry (even before any new typing happens).
        renderRecentSearches()
    }

    override fun onDestroyView() {
        // Avoid posting UI updates after Fragment view is destroyed.
        pendingSearchRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingSearchRunnable = null
        super.onDestroyView()
    }

    private fun setupDebouncedSearch() {
        // Debounce to avoid excessive list recomputations on mid-size lists.
        // Note: We do not use coroutines here to keep patterns consistent with this simple app.
        etSearch.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) = Unit

                override fun afterTextChanged(s: android.text.Editable?) {
                    searchQuery = s?.toString()?.trim().orEmpty()

                    pendingSearchRunnable?.let { mainHandler.removeCallbacks(it) }
                    pendingSearchRunnable = Runnable {
                        // Ensure we only refresh when the view is still attached.
                        if (view != null && isAdded) {
                            refreshProducts()

                            // Persist the query as a "recent search" after debounce (i.e., on "search execution").
                            ShopRepository.recordSearchQuery(searchQuery, maxItems = 5)

                            // Update the visible chips after persistence (so new searches appear immediately).
                            renderRecentSearches()
                        }
                    }
                    mainHandler.postDelayed(pendingSearchRunnable!!, 250L)
                }
            }
        )
    }

    private fun setupSortDropdown(root: View) {
        val spinner = root.findViewById<android.widget.Spinner>(R.id.spinner_sort)

        val labels = listOf(
            getString(R.string.sort_relevance),
            getString(R.string.sort_price_low_to_high),
            getString(R.string.sort_price_high_to_low),
            getString(R.string.sort_name_a_to_z)
        )

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = spinnerAdapter

        // Default is relevance. This may be overridden by restoreCatalogPreferences().
        spinner.setSelection(0, false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                selectedView: View?,
                position: Int,
                id: Long
            ) {
                selectedSortOption = when (position) {
                    1 -> SortOption.PRICE_LOW_TO_HIGH
                    2 -> SortOption.PRICE_HIGH_TO_LOW
                    3 -> SortOption.NAME_A_TO_Z
                    else -> SortOption.RELEVANCE
                }

                if (!isRestoringCatalogPrefs) {
                    persistCatalogPreferences()
                }

                refreshProducts()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun renderCategoryFilters(container: LinearLayout) {
        container.removeAllViews()

        fun addChip(label: String, categoryId: String?) {
            val tv = TextView(requireContext())
            tv.text = label
            tv.setPadding(12)
            tv.background = requireContext().getDrawable(R.drawable.bg_chip_outline)
            tv.setTextColor(requireContext().getColor(R.color.ocean_text))
            tv.textSize = 14f

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 8
            tv.layoutParams = lp

            tv.setOnClickListener {
                selectedCategoryId = categoryId
                if (!isRestoringCatalogPrefs) {
                    persistCatalogPreferences()
                }
                refreshProducts()
            }

            container.addView(tv)
        }

        addChip(getString(R.string.category_all), null)
        ShopRepository.getCategories().forEach { c ->
            addChip(c.name, c.id)
        }
    }

    private fun refreshProducts() {
        val baseItems = ShopRepository.getAllProducts()
        val filtered = applyFilters(baseItems)
        val sorted = applySorting(filtered)

        adapter.submit(sorted, ShopRepository)

        val showEmpty = sorted.isEmpty()
        tvEmptyResults.visibility = if (showEmpty) View.VISIBLE else View.GONE
        rvProducts.visibility = if (showEmpty) View.INVISIBLE else View.VISIBLE
    }

    private fun sortOptionToSpinnerPosition(option: SortOption): Int {
        return when (option) {
            SortOption.RELEVANCE -> 0
            SortOption.PRICE_LOW_TO_HIGH -> 1
            SortOption.PRICE_HIGH_TO_LOW -> 2
            SortOption.NAME_A_TO_Z -> 3
        }
    }

    private fun sortKeyToSortOption(sortKey: String): SortOption {
        return when (sortKey.trim().uppercase(Locale.US)) {
            "PRICE_LOW_TO_HIGH" -> SortOption.PRICE_LOW_TO_HIGH
            "PRICE_HIGH_TO_LOW" -> SortOption.PRICE_HIGH_TO_LOW
            "NAME_A_TO_Z" -> SortOption.NAME_A_TO_Z
            else -> SortOption.RELEVANCE
        }
    }

    private fun persistCatalogPreferences() {
        ShopRepository.saveCatalogPreferences(
            ShopRepository.CatalogPreferences(
                selectedCategoryId = selectedCategoryId,
                sortKey = selectedSortOption.name
            )
        )
    }

    private fun restoreCatalogPreferences() {
        isRestoringCatalogPrefs = true
        try {
            val prefs = ShopRepository.loadCatalogPreferences(defaultSortKey = SortOption.RELEVANCE.name)

            selectedCategoryId = prefs.selectedCategoryId
            selectedSortOption = sortKeyToSortOption(prefs.sortKey)

            // Keep the sort control in sync with persisted value.
            // setSelection(..., false) avoids an extra selection animation; listener will still be invoked on some
            // platform versions, so we guard with isRestoringCatalogPrefs to avoid re-persisting.
            val position = sortOptionToSpinnerPosition(selectedSortOption)
            sortSpinner.setSelection(position, false)
        } finally {
            isRestoringCatalogPrefs = false
        }
    }

    private fun applyFilters(products: List<Product>): List<Product> {
        val query = searchQuery.trim()
        val hasQuery = query.isNotBlank()

        return products.asSequence()
            .filter { selectedCategoryId == null || it.categoryId == selectedCategoryId }
            .filter { product ->
                if (!hasQuery) return@filter true
                matchesSearch(product, query)
            }
            .toList()
    }

    private fun applySorting(products: List<Product>): List<Product> {
        return when (selectedSortOption) {
            SortOption.RELEVANCE -> products.sortedWith(
                compareByDescending<Product> { relevanceScore(it) }.thenBy { it.name.lowercase(Locale.US) }
            )

            SortOption.PRICE_LOW_TO_HIGH -> products.sortedBy { it.priceCents }
            SortOption.PRICE_HIGH_TO_LOW -> products.sortedByDescending { it.priceCents }
            SortOption.NAME_A_TO_Z -> products.sortedBy { it.name.lowercase(Locale.US) }
        }
    }

    private fun matchesSearch(product: Product, query: String): Boolean {
        val q = query.lowercase(Locale.US)
        val productName = product.name.lowercase(Locale.US)
        val categoryName = ShopRepository.getCategoryName(product.categoryId).lowercase(Locale.US)

        // Match name OR category keywords (case-insensitive)
        return productName.contains(q) || categoryName.contains(q)
    }

    /**
     * A small "relevance" heuristic for in-memory sorting:
     *  - Prefer startsWith match on product name
     *  - Then contains match on product name
     *  - Then contains match on category name
     *  - If no query, keep original repo order by returning 0 (stable downstream tie-breakers)
     */
    private fun relevanceScore(product: Product): Int {
        val q = searchQuery.trim()
        if (q.isBlank()) return 0

        val queryLower = q.lowercase(Locale.US)
        val nameLower = product.name.lowercase(Locale.US)
        val categoryLower = ShopRepository.getCategoryName(product.categoryId).lowercase(Locale.US)

        return when {
            nameLower.startsWith(queryLower) -> 3
            nameLower.contains(queryLower) -> 2
            categoryLower.contains(queryLower) -> 1
            else -> 0
        }
    }

    private fun setupRecentSearchesUi() {
        btnClearRecentSearches.setOnClickListener {
            // Immediate clear is acceptable per requirements.
            ShopRepository.clearRecentSearches()
            renderRecentSearches()
        }
    }

    /**
     * Renders persisted recent searches as a horizontally scrollable row of "chips".
     * Tapping a chip sets the search text; existing debounce logic will execute the search + persist.
     */
    private fun renderRecentSearches() {
        val items = ShopRepository.getRecentSearches()
        val show = items.isNotEmpty()

        recentSearchesRow.visibility = if (show) View.VISIBLE else View.GONE
        recentSearchesScroll.visibility = if (show) View.VISIBLE else View.GONE

        recentSearchesContainer.removeAllViews()
        if (!show) return

        items.forEachIndexed { index, query ->
            val chip = TextView(requireContext()).apply {
                text = query
                background = requireContext().getDrawable(R.drawable.bg_chip_outline)
                setTextColor(requireContext().getColor(R.color.ocean_text))
                textSize = 14f

                // Accessibility: ensure 48dp touch target (height) while keeping visual padding.
                // bg_chip_outline already provides 12dp/8dp padding; minHeight ensures target size.
                minHeight = dpToPx(48)

                // Improve usability/feedback: make sure stateful click feedback is shown.
                isClickable = true
                isFocusable = true

                contentDescription = getString(R.string.cd_recent_search_chip, query)

                setOnClickListener {
                    applyRecentSearch(query)
                }
            }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // 8dp grid spacing between chips.
                if (index > 0) marginStart = dpToPx(8)
            }
            chip.layoutParams = lp

            recentSearchesContainer.addView(chip)
        }
    }

    private fun applyRecentSearch(query: String) {
        // Setting text triggers TextWatcher => debounce => refreshProducts + recordSearchQuery.
        etSearch.setText(query)
        etSearch.setSelection(query.length)
        etSearch.requestFocus()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
