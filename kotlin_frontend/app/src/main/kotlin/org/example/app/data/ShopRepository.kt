package org.example.app.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Singleton repository providing:
 * - Mock catalog/categories
 * - Observable cart state (LiveData) shared across fragments
 * - Lightweight local persistence for cart + recent searches + catalog prefs
 * - Favorites (wishlist) stored locally (SharedPreferences) and exposed via LiveData
 */
object ShopRepository {

    private val categories: List<Category> = listOf(
        Category("c1", "Essentials"),
        Category("c2", "Tech"),
        Category("c3", "Home")
    )

    private val products: List<Product> = listOf(
        Product("p1", "Ocean Tee", "c1", 2499, "Soft cotton tee with a modern cut."),
        Product("p2", "Everyday Hoodie", "c1", 5499, "Warm, minimal hoodie for daily wear."),
        Product("p3", "Wireless Earbuds", "c2", 7999, "Compact earbuds with crisp sound."),
        Product("p4", "Desk Lamp", "c3", 3299, "Warm light, clean silhouette, subtle glow."),
        Product("p5", "Ceramic Mug", "c3", 1599, "Matte white mug — simple and timeless."),
        Product("p6", "USB‑C Cable", "c2", 1299, "Durable braided cable for fast charging.")
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
