package com.example.kotlinfrontend.network

import com.example.kotlinfrontend.network.dto.CartDto
import com.example.kotlinfrontend.network.dto.CartItemMutationRequestDto
import com.example.kotlinfrontend.network.dto.CartUpdateQuantityRequestDto
import com.example.kotlinfrontend.network.dto.CouponApplyRequestDto
import com.example.kotlinfrontend.network.dto.CouponRemoveRequestDto
import com.example.kotlinfrontend.network.dto.CouponValidationResponseDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Retrofit API for backend cart operations.
 *
 * Assumptions (per task instructions):
 * - REST endpoints exist under /api/carts
 * - Cart is scoped to "current user" identified by email/userId (for now we use email)
 *
 * If your backend uses headers instead of query parameters for identity, update these methods accordingly.
 */
interface CartApi {

    /**
     * Fetch current cart for the user.
     *
     * We pass identity as query param for simplicity and backward compatibility with common REST patterns.
     */
    @GET("/api/carts/current")
    suspend fun getCurrentCart(
        @Query("email") email: String?
    ): CartDto

    /**
     * Add an item to the cart. Backend may merge quantities or override; we reconcile from response.
     */
    @POST("/api/carts/items")
    suspend fun addItem(
        @Query("email") email: String?,
        @Body body: CartItemMutationRequestDto
    ): CartDto

    /**
     * Update an item's quantity.
     */
    @PUT("/api/carts/items")
    suspend fun updateQuantity(
        @Query("email") email: String?,
        @Body body: CartUpdateQuantityRequestDto
    ): CartDto

    /**
     * Remove an item from cart by productId.
     */
    @DELETE("/api/carts/items")
    suspend fun removeItem(
        @Query("email") email: String?,
        @Query("productId") productId: String
    ): CartDto

    /**
     * Clear entire cart.
     */
    @DELETE("/api/carts")
    suspend fun clearCart(
        @Query("email") email: String?
    ): CartDto

    /**
     * Apply a coupon to the current cart.
     *
     * Backend may now enforce coupon rules using the provided line items.
     *
     * Endpoint: POST /api/carts/coupon?email=...
     */
    @POST("/api/carts/coupon")
    suspend fun applyCoupon(
        @Query("email") email: String?,
        @Body body: CouponApplyRequestDto
    ): CartDto

    /**
     * Remove coupon from current cart.
     *
     * Preferred endpoint: DELETE /api/carts/coupon?email=...
     * Some backends may optionally accept a body; Retrofit's @DELETE does not support a body,
     * so we provide both variants.
     */
    @DELETE("/api/carts/coupon")
    suspend fun removeCoupon(
        @Query("email") email: String?
    ): CartDto

    /**
     * Remove coupon with an optional request body.
     *
     * Used when backend requires line items in remove request to re-check constraints (rare),
     * or when it expects coupon code in body for removal.
     */
    @HTTP(method = "DELETE", path = "/api/carts/coupon", hasBody = true)
    suspend fun removeCouponWithBody(
        @Query("email") email: String?,
        @Body body: CouponRemoveRequestDto
    ): CartDto

    /**
     * Validate a coupon code.
     *
     * Backend may now enforce coupon rules using the provided line items.
     *
     * Endpoint: POST /api/coupons/validate?email=...
     */
    @POST("/api/coupons/validate")
    suspend fun validateCoupon(
        @Query("email") email: String?,
        @Body body: CouponApplyRequestDto
    ): CouponValidationResponseDto
}
