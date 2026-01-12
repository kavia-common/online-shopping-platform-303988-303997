package org.example.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.Locale

/**
 * Singleton repository providing:
 * - Mock catalog/categories
 * - Observable cart state (LiveData) shared across fragments
 * - Lightweight local persistence for cart + recent searches + catalog prefs
 * - Favorites (wishlist) stored locally (SharedPreferences) and exposed via LiveData
 *
 * Also provides simple in-memory paging for the Catalog screen (no network calls).
 */
object ShopRepository {

    private val categories: List<Category> = listOf(
        Category("c1", "Essentials"),
        Category("c2", "Tech"),
        Category("c3", "Home")
    )

    private val products: List<Product> = listOf(
        Product(
            id = "p1",
            name = "Ocean Tee",
            categoryId = "c1",
            priceCents = 2499,
            description = "Soft cotton tee with a modern cut.",
            imageUrls = listOf("mock://ocean-tee/1", "mock://ocean-tee/2", "mock://ocean-tee/3"),
            longDescription = "A refined everyday tee with a clean silhouette and soft hand-feel. Designed to layer easily or stand on its own.",
            bulletPoints = listOf(
                "100% cotton feel (mock)",
                "Modern fit with comfortable stretch (mock)",
                "Easy-care fabric (mock)"
            ),
            isOnSale = true,
            salePriceCents = 1999,
            discountPercent = null
        ),
        Product(
            id = "p2",
            name = "Everyday Hoodie",
            categoryId = "c1",
            priceCents = 5499,
            description = "Warm, minimal hoodie for daily wear.",
            imageUrls = listOf("mock://hoodie/1", "mock://hoodie/2"),
            longDescription = "A cozy hoodie built for repeat wear. Minimal branding, structured drape, and a soft interior for comfort.",
            bulletPoints = listOf(
                "Brushed interior for warmth (mock)",
                "Reinforced seams (mock)",
                "Roomy hood + clean drawcords (mock)"
            ),
            isOnSale = false
        ),
        Product(
            id = "p3",
            name = "Wireless Earbuds",
            categoryId = "c2",
            priceCents = 7999,
            description = "Compact earbuds with crisp sound.",
            imageUrls = listOf("mock://earbuds/1", "mock://earbuds/2", "mock://earbuds/3"),
            longDescription = "Pocket-ready wireless earbuds with clear audio and a stable fit. Built for commute calls and focused listening.",
            bulletPoints = listOf(
                "Charging case included (mock)",
                "Touch controls (mock)",
                "Noise isolation tips (mock)"
            ),
            isOnSale = true,
            salePriceCents = 6499,
            discountPercent = 19
        ),
        Product(
            id = "p4",
            name = "Desk Lamp",
            categoryId = "c3",
            priceCents = 3299,
            description = "Warm light, clean silhouette, subtle glow.",
            imageUrls = listOf("mock://lamp/1"),
            longDescription = "A compact desk lamp with warm ambient light. Fits small spaces and keeps your setup feeling calm and professional.",
            bulletPoints = listOf(
                "Warm color temperature (mock)",
                "Stable base (mock)",
                "Minimal footprint (mock)"
            ),
            isOnSale = false
        ),
        Product(
            id = "p5",
            name = "Ceramic Mug",
            categoryId = "c3",
            priceCents = 1599,
            description = "Matte white mug — simple and timeless.",
            imageUrls = listOf("mock://mug/1", "mock://mug/2"),
            longDescription = "A clean, matte mug that feels right at home on any desk. Comfortable handle, balanced weight, and a timeless look.",
            bulletPoints = listOf(
                "Ceramic body (mock)",
                "Comfort-grip handle (mock)",
                "Everyday capacity (mock)"
            ),
            isOnSale = true,
            salePriceCents = 1299,
            discountPercent = null
        ),
        Product(
            id = "p6",
            name = "USB‑C Cable",
            categoryId = "c2",
            priceCents = 1299,
            description = "Durable braided cable for fast charging.",
            imageUrls = emptyList(), // Demonstrates placeholder behavior when URLs are absent.
            longDescription = "A durable braided cable designed for daily use. Reliable charging and data transfer in a clean, understated finish.",
            bulletPoints = listOf(
                "Braided outer layer (mock)",
                "Reinforced connector (mock)",
                "Fast charge support (mock)"
            ),
            isOnSale = false
        )
    )

    // productId -> quantity
    private val cartMap = linkedMapOf<String, Int>()

    private val cartState = MutableLiveData<Map<String, Int>>(emptyMap())

    // Favorites
    private val favoriteIds: MutableSet<String> = linkedSetOf()
    private val favoritesState = MutableLiveData<Set<String>>(emptySet())

    // Local persistence (initialized from Application context).
    private var localStore: LocalStore? = null
    private var isInitialized: Boolean = false

    // Recent searches (most-recent-first), kept in-memory and persisted.
    private val recentSearches: MutableList<String> = mutableListOf()

    /**
     * Catalog UI state that we persist so filters/sort survive app restarts and navigation.
     *
     * Note: Search query persistence is already covered by recent searches.
     */
    data class CatalogPreferences(
        val selectedCategoryId: String?,
        val sortKey: String,
        val favoritesOnly: Boolean
    )

    /**
     * Query parameters that define a catalog "session". When this snapshot changes, paging resets.
     */
    data class CatalogQuery(
        val selectedCategoryId: String?,
        val searchQuery: String,
        val sortKey: String,
        val favoritesOnly: Boolean
    )

    /**
     * A single page result for the catalog. This is intentionally simple for mock data.
     */
    data class PagedProducts(
        val items: List<Product>,
        val isEndReached: Boolean,
        val totalCount: Int
    )

    // Paging state for catalog (kept in repository so Fragment can remain thin).
    private var lastCatalogQuery: CatalogQuery? = null
    private var lastCatalogFilteredSorted: List<Product> = emptyList()
    private var currentCatalogPageIndex: Int = 0 // 0 means "no pages loaded yet"

    private val pagingHandler = Handler(Looper.getMainLooper())

    // PUBLIC_INTERFACE
    fun getCategories(): List<Category> = categories

    // PUBLIC_INTERFACE
    fun getAllProducts(): List<Product> = products

    // PUBLIC_INTERFACE
    fun findProductById(productId: String): Product? = products.firstOrNull { it.id == productId }

    // PUBLIC_INTERFACE
    fun getCategoryName(categoryId: String): String {
        return categories.firstOrNull { it.id == categoryId }?.name ?: "Unknown"
    }

    /**
     * Initializes the repository with an Application context and restores persisted state.
     *
     * Important: This should be called once early (e.g., MainActivity.onCreate) so
     * restoration happens before Fragments start observing LiveData.
     */
    // PUBLIC_INTERFACE
    fun initialize(context: Context) {
        if (isInitialized) return

        localStore = LocalStore(context.applicationContext)

        // Restore cart BEFORE any observers are likely registered.
        val restoredCart = localStore?.readCart().orEmpty()
        cartMap.clear()
        cartMap.putAll(restoredCart)
        cartState.value = cartMap.toMap()

        // Restore recent searches for catalog recall.
        recentSearches.clear()
        recentSearches.addAll(localStore?.readRecentSearches().orEmpty())

        // Restore favorites.
        favoriteIds.clear()
        favoriteIds.addAll(localStore?.readFavoriteProductIds().orEmpty())
        favoritesState.value = favoriteIds.toSet()

        isInitialized = true
    }

    // PUBLIC_INTERFACE
    fun cartLiveData(): LiveData<Map<String, Int>> = cartState

    // PUBLIC_INTERFACE
    fun addToCart(productId: String, quantityToAdd: Int = 1) {
        val existing = cartMap[productId] ?: 0
        cartMap[productId] = (existing + quantityToAdd).coerceAtLeast(0)
        publishCart()
    }

    // PUBLIC_INTERFACE
    fun setQuantity(productId: String, quantity: Int) {
        if (quantity <= 0) {
            cartMap.remove(productId)
        } else {
            cartMap[productId] = quantity
        }
        publishCart()
    }

    // PUBLIC_INTERFACE
    fun clearCart() {
        cartMap.clear()
        publishCart()
    }

    /**
     * Returns the current cart line items (computed from the internal map).
     */
    // PUBLIC_INTERFACE
    fun cartItems(): List<CartItem> {
        return cartMap.mapNotNull { (productId, qty) ->
            val p = findProductById(productId) ?: return@mapNotNull null
            CartItem(p, qty)
        }
    }

    // PUBLIC_INTERFACE
    fun cartTotalCents(): Int {
        return cartItems().sumOf { it.product.priceCents * it.quantity }
    }

    /**
     * Returns recent search queries (most-recent-first).
     */
    // PUBLIC_INTERFACE
    fun getRecentSearches(): List<String> = recentSearches.toList()

    /**
     * Records a search query into the recent list (de-duplicated, capped, most-recent-first)
     * and persists it.
     */
    // PUBLIC_INTERFACE
    fun recordSearchQuery(query: String, maxItems: Int = 5) {
        val normalized = query.trim()
        if (normalized.isBlank()) return

        // De-duplicate (case-insensitive) while keeping the most recent version.
        val existingIndex = recentSearches.indexOfFirst { it.equals(normalized, ignoreCase = true) }
        if (existingIndex >= 0) {
            recentSearches.removeAt(existingIndex)
        }
        recentSearches.add(0, normalized)

        // Cap list.
        while (recentSearches.size > maxItems) {
            recentSearches.removeAt(recentSearches.lastIndex)
        }

        localStore?.writeRecentSearches(recentSearches)
    }

    /**
     * Clears all recent searches and persists the empty list.
     */
    // PUBLIC_INTERFACE
    fun clearRecentSearches() {
        recentSearches.clear()
        localStore?.writeRecentSearches(recentSearches)
    }

    /**
     * Returns true when [productId] is currently in the favorites set.
     *
     * This is a synchronous check intended for adapters/binding.
     */
    // PUBLIC_INTERFACE
    fun isFavorite(productId: String): Boolean = favoriteIds.contains(productId)

    /**
     * Toggles favorite state for the given product ID (add/remove), persists, and updates observers.
     */
    // PUBLIC_INTERFACE
    fun toggleFavorite(productId: String) {
        if (productId.isBlank()) return

        if (favoriteIds.contains(productId)) {
            favoriteIds.remove(productId)
        } else {
            favoriteIds.add(productId)
        }
        publishFavorites()
    }

    /**
     * Returns a snapshot of favorite IDs.
     */
    // PUBLIC_INTERFACE
    fun getFavorites(): Set<String> = favoriteIds.toSet()

    /**
     * Observe favorites as a set of product IDs. UI can map IDs -> products as needed.
     */
    // PUBLIC_INTERFACE
    fun observeFavorites(): LiveData<Set<String>> = favoritesState

    /**
     * Loads persisted catalog filter/sort preferences.
     *
     * If nothing has been stored yet, returns defaults (All categories + RELEVANCE sort + favoritesOnly=false).
     */
    // PUBLIC_INTERFACE
    fun loadCatalogPreferences(defaultSortKey: String = "RELEVANCE"): CatalogPreferences {
        val store = localStore
        val categoryId = store?.readCatalogSelectedCategoryId()
        val sortKey = store?.readCatalogSortKey() ?: defaultSortKey
        val favoritesOnly = store?.readCatalogFavoritesOnly() ?: false
        return CatalogPreferences(
            selectedCategoryId = categoryId,
            sortKey = sortKey,
            favoritesOnly = favoritesOnly
        )
    }

    /**
     * Persists catalog filter/sort preferences.
     *
     * This is expected to be called whenever the user changes category, sort order, or favorites-only filter.
     */
    // PUBLIC_INTERFACE
    fun saveCatalogPreferences(prefs: CatalogPreferences) {
        localStore?.writeCatalogSelectedCategoryId(prefs.selectedCategoryId)
        localStore?.writeCatalogSortKey(prefs.sortKey)
        localStore?.writeCatalogFavoritesOnly(prefs.favoritesOnly)
    }

    /**
     * Returns a catalog page for the given [query], resetting internal paging state when the query changes.
     *
     * Notes:
     * - This uses current in-memory mock products only (no network / no API calls).
     * - Sorting is applied BEFORE paging so item order stays stable across pages.
     * - The callback is invoked on the main thread.
     */
    // PUBLIC_INTERFACE
    fun getCatalogFirstPage(
        query: CatalogQuery,
        pageSize: Int,
        simulatedDelayMs: Long = 200L,
        callback: (PagedProducts) -> Unit
    ) {
        // New query => reset paging.
        val normalizedQuery = query.copy(searchQuery = query.searchQuery.trim())
        lastCatalogQuery = normalizedQuery
        currentCatalogPageIndex = 0

        // Build a fully filtered+sorted list once; then slice pages from it.
        lastCatalogFilteredSorted = buildCatalogFilteredSorted(normalizedQuery)

        pagingHandler.postDelayed(
            {
                callback(producePage(pageIndex = 1, pageSize = pageSize))
            },
            simulatedDelayMs
        )
    }

    /**
     * Loads the next page for the most recent query.
     *
     * If [query] differs from the last query, this behaves like a reset and returns page 1.
     * The callback is invoked on the main thread.
     */
    // PUBLIC_INTERFACE
    fun getCatalogNextPage(
        query: CatalogQuery,
        pageSize: Int,
        simulatedDelayMs: Long = 200L,
        callback: (PagedProducts) -> Unit
    ) {
        val normalizedQuery = query.copy(searchQuery = query.searchQuery.trim())
        val last = lastCatalogQuery

        if (last == null || last != normalizedQuery) {
            // Query changed; treat as first page.
            getCatalogFirstPage(
                query = normalizedQuery,
                pageSize = pageSize,
                simulatedDelayMs = simulatedDelayMs,
                callback = callback
            )
            return
        }

        // If already at end, immediately return an empty page (caller can stop asking).
        val currentEndIndex = currentCatalogPageIndex * pageSize
        if (currentEndIndex >= lastCatalogFilteredSorted.size) {
            pagingHandler.post {
                callback(
                    PagedProducts(
                        items = emptyList(),
                        isEndReached = true,
                        totalCount = lastCatalogFilteredSorted.size
                    )
                )
            }
            return
        }

        pagingHandler.postDelayed(
            {
                callback(producePage(pageIndex = currentCatalogPageIndex + 1, pageSize = pageSize))
            },
            simulatedDelayMs
        )
    }

    private fun producePage(pageIndex: Int, pageSize: Int): PagedProducts {
        val safePageSize = pageSize.coerceAtLeast(1)
        val startExclusive = 0
        val total = lastCatalogFilteredSorted.size

        val start = ((pageIndex - 1) * safePageSize).coerceAtLeast(startExclusive)
        val end = (start + safePageSize).coerceAtMost(total)

        val pageItems = if (start < end) lastCatalogFilteredSorted.subList(start, end) else emptyList()
        currentCatalogPageIndex = pageIndex

        val isEnd = end >= total
        return PagedProducts(
            items = pageItems.toList(),
            isEndReached = isEnd,
            totalCount = total
        )
    }

    private fun buildCatalogFilteredSorted(query: CatalogQuery): List<Product> {
        val favoritesSnapshot = getFavorites()
        val q = query.searchQuery.trim()
        val hasQuery = q.isNotBlank()
        val qLower = q.lowercase(Locale.US)

        val filtered = products.asSequence()
            .filter { query.selectedCategoryId == null || it.categoryId == query.selectedCategoryId }
            .filter { product ->
                if (!query.favoritesOnly) return@filter true
                favoritesSnapshot.contains(product.id)
            }
            .filter { product ->
                if (!hasQuery) return@filter true
                val nameLower = product.name.lowercase(Locale.US)
                val categoryLower = getCategoryName(product.categoryId).lowercase(Locale.US)
                nameLower.contains(qLower) || categoryLower.contains(qLower)
            }
            .toList()

        // Sort key mirrors CatalogFragment's SortOption names.
        return when (query.sortKey.trim().uppercase(Locale.US)) {
            "PRICE_LOW_TO_HIGH" -> filtered.sortedBy { it.priceCents }
            "PRICE_HIGH_TO_LOW" -> filtered.sortedByDescending { it.priceCents }
            "NAME_A_TO_Z" -> filtered.sortedBy { it.name.lowercase(Locale.US) }
            else -> {
                // Relevance heuristic consistent with CatalogFragment.relevanceScore(...).
                filtered.sortedWith(
                    compareByDescending<Product> { relevanceScore(product = it, queryLower = qLower) }
                        .thenBy { it.name.lowercase(Locale.US) }
                )
            }
        }
    }

    private fun relevanceScore(product: Product, queryLower: String): Int {
        if (queryLower.isBlank()) return 0
        val nameLower = product.name.lowercase(Locale.US)
        val categoryLower = getCategoryName(product.categoryId).lowercase(Locale.US)
        return when {
            nameLower.startsWith(queryLower) -> 3
            nameLower.contains(queryLower) -> 2
            categoryLower.contains(queryLower) -> 1
            else -> 0
        }
    }

    private fun publishCart() {
        cartState.value = cartMap.toMap()
        // Persist every mutation. If initialize() hasn't been called yet, this is a no-op.
        localStore?.writeCart(cartMap)
    }

    private fun publishFavorites() {
        favoritesState.value = favoriteIds.toSet()
        // Persist every mutation. If initialize() hasn't been called yet, this is a no-op.
        localStore?.writeFavoriteProductIds(favoriteIds)
    }

    data class CartItem(
        val product: Product,
        val quantity: Int
    ) {
        fun lineTotalCents(): Int = product.priceCents * quantity
        fun formattedLineTotal(): String = "$" + String.format("%.2f", lineTotalCents() / 100.0)
    }
}
