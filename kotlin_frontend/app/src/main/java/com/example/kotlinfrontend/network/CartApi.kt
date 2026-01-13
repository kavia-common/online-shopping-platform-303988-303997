package com.example.kotlinfrontend.network

import com.example.kotlinfrontend.network.dto.CartDto
import com.example.kotlinfrontend.network.dto.CartItemMutationRequestDto
import com.example.kotlinfrontend.network.dto.CartUpdateQuantityRequestDto
import com.example.kotlinfrontend.network.dto.CouponRemoveRequestDto
import com.example.kotlinfrontend.network.dto.CouponRequestDto
import com.example.kotlinfrontend.network.dto.CouponResultDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Retrofit API for backend cart operations.
 *
 * Coupon note:
 * - This client uses the backend's compatibility routes under /api/carts/coupon/{validate,apply,remove}.
 * - Those routes accept the Android payload shape and delegate to CouponService.
 */
interface CartApi {

    @GET("/api/carts/current")
    suspend fun getCurrentCart(
        @Query("email") email: String?
    ): CartDto

    @POST("/api/carts/items")
    suspend fun addItem(
        @Query("email") email: String?,
        @Body body: CartItemMutationRequestDto
    ): CartDto

    @PUT("/api/carts/items")
    suspend fun updateQuantity(
        @Query("email") email: String?,
        @Body body: CartUpdateQuantityRequestDto
    ): CartDto

    @DELETE("/api/carts/items")
    suspend fun removeItem(
        @Query("email") email: String?,
        @Query("productId") productId: String
    ): CartDto

    @DELETE("/api/carts")
    suspend fun clearCart(
        @Query("email") email: String?
    ): CartDto

    @POST("/api/carts/coupon")
    suspend fun applyCoupon(
        @Query("email") email: String?,
        @Body body: com.example.kotlinfrontend.network.dto.CouponRequestDto
    ): CartDto

    @DELETE("/api/carts/coupon")
    suspend fun removeCoupon(
        @Query("email") email: String?
    ): CartDto

    /**
     * Validate a coupon using compatibility endpoint.
     *
     * Endpoint: POST /api/carts/coupon/validate
     * Body: { code, email, items? }
     * Response: normalized coupon result usable by UI
     */
    @POST("/api/carts/coupon/validate")
    suspend fun validateCoupon(
        @Body body: CouponRequestDto
    ): CouponResultDto

    /**
     * Apply a coupon using compatibility endpoint.
     *
     * Endpoint: POST /api/carts/coupon/apply
     * Body: { code, email, items? }
     * Response: normalized coupon result usable by UI
     */
    @POST("/api/carts/coupon/apply")
    suspend fun applyCouponRules(
        @Body body: CouponRequestDto
    ): CouponResultDto

    /**
     * Remove coupon using compatibility endpoint.
     *
     * Endpoint: POST /api/carts/coupon/remove
     * Body: { email }
     * Response: normalized coupon result usable by UI
     */
    @POST("/api/carts/coupon/remove")
    suspend fun removeCouponRules(
        @Body body: CouponRemoveRequestDto
    ): CouponResultDto
}
