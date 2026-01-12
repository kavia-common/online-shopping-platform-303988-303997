package com.example.kotlinfrontend.network

import com.example.kotlinfrontend.network.dto.OrderCreateRequestDto
import com.example.kotlinfrontend.network.dto.OrderDto
import com.example.kotlinfrontend.network.dto.PageResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API for backend order lifecycle.
 *
 * Paths are assumed to be under /api/orders, consistent with the rest of this app's usage of /api.
 */
interface OrderApi {

    @POST("/api/orders")
    suspend fun createOrder(
        @Body body: OrderCreateRequestDto
    ): OrderDto

    @GET("/api/orders/{id}")
    suspend fun getOrderById(
        @Path("id") id: String
    ): OrderDto

    /**
     * Paged order list with optional filters.
     *
     * Common optional filters supported by many backends:
     * - status
     * - email
     * - from/to (date range). We use from/to as ISO-8601 strings if supported.
     */
    @GET("/api/orders")
    suspend fun listOrders(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String? = "createdAt,desc",
        @Query("status") status: String? = null,
        @Query("email") email: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): PageResponseDto<OrderDto>

    /**
     * Transition endpoints. These are modeled as POSTs. If your backend uses PUT/PATCH,
     * update accordingly.
     */
    @POST("/api/orders/{id}/pay")
    suspend fun markPaid(
        @Path("id") id: String
    ): OrderDto

    @POST("/api/orders/{id}/ship")
    suspend fun markShipped(
        @Path("id") id: String
    ): OrderDto

    @POST("/api/orders/{id}/deliver")
    suspend fun markDelivered(
        @Path("id") id: String
    ): OrderDto

    @POST("/api/orders/{id}/cancel")
    suspend fun cancel(
        @Path("id") id: String
    ): OrderDto
}
