package com.example.kotlinfrontend.data

import android.content.Context
import com.example.kotlinfrontend.model.CartItem
import com.example.kotlinfrontend.model.CartSummary
import com.example.kotlinfrontend.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for cart state and persistence.
 *
 * Designed as a lightweight singleton per-process; state is restored from disk on first use.
 */
class CartRepository(context: Context) {

    private val store = CartStore(context.applicationContext)

    private val _items = MutableStateFlow<List<CartItem>>(store.load())
    val items: StateFlow<List<CartItem>> = _items.asStateFlow()

    // PUBLIC_INTERFACE
    fun addItem(product: Product, qty: Int) {
        /** Add qty for a product. If already in cart, increments quantity. */
        if (qty <= 0) return

        val category = product.category?.trim().orEmpty().ifBlank { "Uncategorized" }
        val current = _items.value

        val idx = current.indexOfFirst { it.productId == product.id }
        val updated = if (idx >= 0) {
            val existing = current[idx]
            val newQty = existing.quantity + qty
            current.toMutableList().apply {
                this[idx] = existing.copy(quantity = newQty)
            }.toList()
        } else {
            current + CartItem(
                productId = product.id,
                name = product.name,
                price = product.price,
                category = category,
                quantity = qty
            )
        }

        setAndPersist(updated)
    }

    // PUBLIC_INTERFACE
    fun updateQty(productId: String, qty: Int) {
        /** Set absolute quantity for a cart item; if qty <= 0 the item is removed. */
        val current = _items.value
        val idx = current.indexOfFirst { it.productId == productId }
        if (idx < 0) return

        val updated = if (qty <= 0) {
            current.filterNot { it.productId == productId }
        } else {
            current.toMutableList().apply {
                this[idx] = this[idx].copy(quantity = qty)
            }.toList()
        }

        setAndPersist(updated)
    }

    // PUBLIC_INTERFACE
    fun removeItem(productId: String) {
        /** Remove a product from the cart. */
        val updated = _items.value.filterNot { it.productId == productId }
        setAndPersist(updated)
    }

    // PUBLIC_INTERFACE
    fun clear() {
        /** Clear cart items. */
        _items.value = emptyList()
        store.clear()
    }

    // PUBLIC_INTERFACE
    fun summary(): CartSummary {
        /** Compute cart totals. */
        val list = _items.value
        val totalQty = list.sumOf { it.quantity }
        val subtotal = list.sumOf { it.subtotal() }
        return CartSummary(
            distinctItems = list.size,
            totalQuantity = totalQty,
            subtotal = subtotal
        )
    }

    // PUBLIC_INTERFACE
    fun groupedByCategory(): Map<String, List<CartItem>> {
        /** Returns a stable grouping by category (sorted by category name). */
        return _items.value
            .groupBy { it.category }
            .toSortedMap(compareBy { it.lowercase() })
    }

    private fun setAndPersist(updated: List<CartItem>) {
        _items.value = updated
        store.save(updated)
    }
}
