package com.example.kotlinfrontend.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kotlinfrontend.data.AppRepositories
import com.example.kotlinfrontend.model.CartItem
import com.example.kotlinfrontend.model.CartSummary
import com.example.kotlinfrontend.model.Product
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for cart UI.
 */
class CartViewModel(application: Application) : AndroidViewModel(application) {

    private val cartRepo = AppRepositories.cart(application)

    val items: StateFlow<List<CartItem>> =
        cartRepo.items.stateIn(viewModelScope, SharingStarted.Eagerly, cartRepo.items.value)

    /**
     * Flattened list model for the RecyclerView: category headers + item rows.
     */
    val groupedRows: StateFlow<List<CartRow>> =
        cartRepo.items
            .map { items ->
                val grouped = items.groupBy { it.category }
                    .toSortedMap(compareBy { it.lowercase() })

                val rows = mutableListOf<CartRow>()
                grouped.forEach { (category, list) ->
                    rows.add(CartRow.Header(category))
                    // Stable sort within category
                    list.sortedBy { it.name.lowercase() }.forEach { item ->
                        rows.add(CartRow.Item(item))
                    }
                }
                rows
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val summary: StateFlow<CartSummary> =
        cartRepo.items
            .map {
                cartRepo.summary()
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, cartRepo.summary())

    val itemCount: StateFlow<Int> =
        cartRepo.items
            .map { list -> list.sumOf { it.quantity } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, cartRepo.items.value.sumOf { it.quantity })

    // PUBLIC_INTERFACE
    fun addItem(product: Product, qty: Int) {
        /** Add qty for a product to cart. */
        cartRepo.addItem(product, qty)
    }

    // PUBLIC_INTERFACE
    fun removeItem(productId: String) {
        /** Remove product from cart. */
        cartRepo.removeItem(productId)
    }

    // PUBLIC_INTERFACE
    fun updateQty(productId: String, qty: Int) {
        /** Set product qty; if qty <= 0 item will be removed. */
        cartRepo.updateQty(productId, qty)
    }

    // PUBLIC_INTERFACE
    fun clear() {
        /** Clear the cart. */
        cartRepo.clear()
    }
}

/**
 * UI model for sectioned RecyclerView.
 */
sealed class CartRow {
    data class Header(val category: String) : CartRow()
    data class Item(val item: CartItem) : CartRow()
}
