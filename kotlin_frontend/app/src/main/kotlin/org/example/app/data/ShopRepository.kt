package org.example.app.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Singleton in-memory repository providing:
 * - Mock catalog/categories
 * - Observable cart state (LiveData) shared across fragments
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

    fun getCategories(): List<Category> = categories

    fun getAllProducts(): List<Product> = products

    fun findProductById(productId: String): Product? = products.firstOrNull { it.id == productId }

    fun getCategoryName(categoryId: String): String {
        return categories.firstOrNull { it.id == categoryId }?.name ?: "Unknown"
    }

    fun cartLiveData(): LiveData<Map<String, Int>> = cartState

    fun addToCart(productId: String, quantityToAdd: Int = 1) {
        val existing = cartMap[productId] ?: 0
        cartMap[productId] = (existing + quantityToAdd).coerceAtLeast(0)
        cartState.value = cartMap.toMap()
    }

    fun setQuantity(productId: String, quantity: Int) {
        if (quantity <= 0) {
            cartMap.remove(productId)
        } else {
            cartMap[productId] = quantity
        }
        cartState.value = cartMap.toMap()
    }

    fun clearCart() {
        cartMap.clear()
        cartState.value = emptyMap()
    }

    fun cartItems(): List<CartItem> {
        return cartMap.mapNotNull { (productId, qty) ->
            val p = findProductById(productId) ?: return@mapNotNull null
            CartItem(p, qty)
        }
    }

    fun cartTotalCents(): Int {
        return cartItems().sumOf { it.product.priceCents * it.quantity }
    }

    data class CartItem(
        val product: Product,
        val quantity: Int
    ) {
        fun lineTotalCents(): Int = product.priceCents * quantity
        fun formattedLineTotal(): String = "$" + String.format("%.2f", lineTotalCents() / 100.0)
    }
}
