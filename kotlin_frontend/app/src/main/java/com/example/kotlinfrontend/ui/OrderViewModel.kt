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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OrderViewModel : ViewModel() {

    private val repository = OrderRepository()

    private val pageSize = 20

    // Filter states (kept separate so we can debounce only the email query).
    private val _status = MutableStateFlow<OrderStatus?>(null)
    private val _emailQuery = MutableStateFlow("")
    private val _dateFrom = MutableStateFlow<String?>(null) // yyyy-MM-dd (simple and backend-friendly)
    private val _dateTo = MutableStateFlow<String?>(null)   // yyyy-MM-dd

    val status: StateFlow<OrderStatus?> = _status.asStateFlow()
    val emailQuery: StateFlow<String> = _emailQuery.asStateFlow()
    val dateFrom: StateFlow<String?> = _dateFrom.asStateFlow()
    val dateTo: StateFlow<String?> = _dateTo.asStateFlow()

    // Debounce only email typing; other filters apply immediately.
    private val debouncedEmail = _emailQuery
        .debounce(300)
        .distinctUntilChanged()

    private val queryParams: Flow<QueryParams> = combine(
        _status,
        debouncedEmail,
        _dateFrom,
        _dateTo
    ) { status, email, from, to ->
        QueryParams(
            status = status,
            email = email.trim().ifBlank { null },
            from = from?.trim()?.ifBlank { null },
            to = to?.trim()?.ifBlank { null }
        )
    }.distinctUntilChanged()

    /**
     * Exposed to the Activity for chip rendering and empty-state messaging.
     */
    val activeQueryParams: StateFlow<QueryParams> =
        queryParams.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            QueryParams(status = null, email = null, from = null, to = null)
        )

    val orders: Flow<PagingData<Order>> =
        queryParams
            .flatMapLatest { params ->
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
                            filters = OrderRepository.OrderFilters(
                                status = params.status,
                                email = params.email,
                                from = params.from,
                                to = params.to
                            )
                        )
                    }
                ).flow
            }
            .cachedIn(viewModelScope)

    private val _lastCreatedOrderId = MutableStateFlow<String?>(null)
    val lastCreatedOrderId: StateFlow<String?> = _lastCreatedOrderId.asStateFlow()

    private val _singleOrder = MutableStateFlow<Order?>(null)
    val singleOrder: StateFlow<Order?> = _singleOrder.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // PUBLIC_INTERFACE
    fun setStatusFilter(status: OrderStatus?) {
        /** Update status filter for order history paging. */
        _status.value = status
    }

    // PUBLIC_INTERFACE
    fun setEmailFilter(email: String) {
        /** Update email query filter (debounced). */
        _emailQuery.value = email
    }

    // PUBLIC_INTERFACE
    fun setDateFrom(from: String?) {
        /** Update the "from" date (expected yyyy-MM-dd). */
        _dateFrom.value = from?.trim()?.ifBlank { null }
    }

    // PUBLIC_INTERFACE
    fun setDateTo(to: String?) {
        /** Update the "to" date (expected yyyy-MM-dd). */
        _dateTo.value = to?.trim()?.ifBlank { null }
    }

    // PUBLIC_INTERFACE
    fun clearFilters() {
        /** Clear all filters (status/email/date) but does not affect any other state. */
        _status.value = null
        _emailQuery.value = ""
        _dateFrom.value = null
        _dateTo.value = null
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

    data class QueryParams(
        val status: OrderStatus?,
        val email: String?,
        val from: String?,
        val to: String?
    ) {
        fun hasActiveFilters(): Boolean =
            status != null || !email.isNullOrBlank() || !from.isNullOrBlank() || !to.isNullOrBlank()
    }
}
