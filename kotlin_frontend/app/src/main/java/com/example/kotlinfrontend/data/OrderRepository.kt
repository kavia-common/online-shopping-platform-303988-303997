package com.example.kotlinfrontend.data

import com.example.kotlinfrontend.model.Order
import com.example.kotlinfrontend.model.OrderItem
import com.example.kotlinfrontend.model.OrderStatus
import com.example.kotlinfrontend.network.ApiClient
import com.example.kotlinfrontend.network.OrderApi
import com.example.kotlinfrontend.network.dto.OrderCreateItemRequestDto
import com.example.kotlinfrontend.network.dto.OrderCreateRequestDto
import com.example.kotlinfrontend.network.dto.OrderDto
import com.example.kotlinfrontend.network.dto.OrderItemDto

class OrderRepository(
    private val api: OrderApi = ApiClient.createOrderApi()
) {

    data class OrderFilters(
        val status: OrderStatus? = null,
        val email: String? = null,
        val from: String? = null,
        val to: String? = null
    )

    // PUBLIC_INTERFACE
    suspend fun createOrder(email: String?, items: List<Pair<String, Int>>, couponCode: String? = null): Order {
        /** Create an order with (productId, quantity) items and optional couponCode. */
        val body = OrderCreateRequestDto(
            email = email?.trim()?.ifBlank { null },
            couponCode = couponCode?.trim()?.ifBlank { null },
            items = items.map { (productId, quantity) ->
                OrderCreateItemRequestDto(productId = productId, quantity = quantity)
            }
        )
        return api.createOrder(body).toDomain()
    }

    // PUBLIC_INTERFACE
    suspend fun getOrderById(id: String): Order {
        /** Fetch an order by id. */
        return api.getOrderById(id).toDomain()
    }

    // PUBLIC_INTERFACE
    suspend fun fetchOrdersPage(
        pageIndex: Int,
        pageSize: Int,
        filters: OrderFilters
    ): PageResult<Order> {
        /** Fetch a page of order history (Spring Data Page). */
        val dtoPage = api.listOrders(
            page = pageIndex,
            size = pageSize,
            sort = "createdAt,desc",
            status = filters.status?.takeUnless { it == OrderStatus.UNKNOWN }?.name,
            email = filters.email?.trim()?.ifBlank { null },
            from = filters.from?.trim()?.ifBlank { null },
            to = filters.to?.trim()?.ifBlank { null }
        )

        val items = dtoPage.content.map { it.toDomain() }
        val totalCount = dtoPage.totalElements.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return PageResult(items = items, totalCount = totalCount)
    }

    // PUBLIC_INTERFACE
    suspend fun markPaid(id: String): Order {
        /** Transition order to PAID. */
        return api.markPaid(id).toDomain()
    }

    // PUBLIC_INTERFACE
    suspend fun markShipped(id: String): Order {
        /** Transition order to SHIPPED. */
        return api.markShipped(id).toDomain()
    }

    // PUBLIC_INTERFACE
    suspend fun markDelivered(id: String): Order {
        /** Transition order to DELIVERED. */
        return api.markDelivered(id).toDomain()
    }

    // PUBLIC_INTERFACE
    suspend fun cancel(id: String): Order {
        /** Transition order to CANCELLED. */
        return api.cancel(id).toDomain()
    }
}

data class PageResult<T>(
    val items: List<T>,
    val totalCount: Int
)

private fun OrderDto.toDomain(): Order {
    val safeId = (id ?: "").ifBlank { "unknown" }
    return Order(
        id = safeId,
        email = email,
        status = OrderStatus.fromApi(status),
        items = items.map { it.toDomain() },
        totalAmount = totalAmount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

private fun OrderItemDto.toDomain(): OrderItem {
    return OrderItem(
        productId = productId,
        productName = productName,
        unitPrice = unitPrice,
        quantity = quantity
    )
}
