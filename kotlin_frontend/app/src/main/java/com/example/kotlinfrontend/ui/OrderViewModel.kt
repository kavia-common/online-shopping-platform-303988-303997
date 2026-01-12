package com.example.kotlinfrontend.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.kotlinfrontend.data.OrderPagingSource
import com.example.kotlinfrontend.data.OrderRepository
import com.example.kotlinfrontend.model.Order
import com.example.kotlinfrontend.model.OrderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class OrderViewModel : ViewModel() {

    private val repository = OrderRepository()

    private val pageSize = 20

    private val _filters = MutableStateFlow(OrderRepository.OrderFilters())
    val filters: StateFlow<OrderRepository.OrderFilters> = _filters.asStateFlow()

    private val _lastCreatedOrderId = MutableStateFlow<String?>(null)
    val lastCreatedOrderId: StateFlow<String?> = _lastCreatedOrderId.asStateFlow()

    private val _singleOrder = MutableStateFlow<Order?>(null)
    val singleOrder: StateFlow<Order?> = _singleOrder.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val orders: Flow<PagingData<Order>> =
        _filters
            .flatMapLatest { active ->
                Pager(
                    config = PagingConfig(
                        pageSize = pageSize,
                        initialLoadSize = pageSize * 2,
                        prefetchDistance = 6,
                        enablePlaceholders = false
                    ),
                    pagingSourceFactory = {
                        OrderPagingSource(
                            repository = repository,
                            pageSize = pageSize,
                            filters = active
                        )
                    }
                ).flow
            }
            .cachedIn(viewModelScope)

    // PUBLIC_INTERFACE
    fun setStatusFilter(status: OrderStatus?) {
        /** Update status filter for order history paging. */
        _filters.value = _filters.value.copy(status = status)
    }

    // PUBLIC_INTERFACE
    fun setEmailFilter(email: String?) {
        /** Update email filter for order history paging. */
        _filters.value = _filters.value.copy(email = email?.trim()?.ifBlank { null })
    }

    // PUBLIC_INTERFACE
    fun setDateRange(from: String?, to: String?) {
        /** Update date range filter (ISO-8601 strings if backend supports). */
        _filters.value = _filters.value.copy(
            from = from?.trim()?.ifBlank { null },
            to = to?.trim()?.ifBlank { null }
        )
    }

    // PUBLIC_INTERFACE
    fun clearError() {
        /** Clear transient error message. */
        _errorMessage.value = null
    }

    // PUBLIC_INTERFACE
    fun createOrderFromSample() {
        /**
         * Minimal E2E flow: creates an order using a fixed sample payload.
         *
         * If your backend validates productIds strictly, replace these ids with real product ids.
         */
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                val created = repository.createOrder(
                    email = "guest@example.com",
                    items = listOf(
                        "sample-product-1" to 1,
                        "sample-product-2" to 2
                    )
                )
                _lastCreatedOrderId.value = created.id
            } catch (t: Throwable) {
                _errorMessage.value = t.message ?: "Failed to create order."
            }
        }
    }

    // PUBLIC_INTERFACE
    fun getOrder(id: String) {
        /** Fetch a single order and publish to [singleOrder]. */
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                _singleOrder.value = repository.getOrderById(id)
            } catch (t: Throwable) {
                _errorMessage.value = t.message ?: "Failed to load order."
            }
        }
    }

    // PUBLIC_INTERFACE
    fun markPaid(id: String) {
        /** Transition: PAID. */
        transition(id) { repository.markPaid(id) }
    }

    // PUBLIC_INTERFACE
    fun markShipped(id: String) {
        /** Transition: SHIPPED. */
        transition(id) { repository.markShipped(id) }
    }

    // PUBLIC_INTERFACE
    fun markDelivered(id: String) {
        /** Transition: DELIVERED. */
        transition(id) { repository.markDelivered(id) }
    }

    // PUBLIC_INTERFACE
    fun cancel(id: String) {
        /** Transition: CANCELLED. */
        transition(id) { repository.cancel(id) }
    }

    private fun transition(id: String, block: suspend () -> Order) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                val updated = block()
                // If user is viewing a single order, keep it in sync.
                if (_singleOrder.value?.id == id) {
                    _singleOrder.value = updated
                }
            } catch (t: Throwable) {
                _errorMessage.value = t.message ?: "Order update failed."
            }
        }
    }
}
