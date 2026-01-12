package com.example.kotlinfrontend.network

import com.example.kotlinfrontend.network.dto.PageResponseDto
import com.example.kotlinfrontend.network.dto.ProductCreateRequestDto
import com.example.kotlinfrontend.network.dto.ProductDto
import com.example.kotlinfrontend.network.dto.ProductUpdateRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ProductApi {

    @GET("/api/products")
    suspend fun getProducts(
        @Query("page") page: Int,
        @Query("size") size: Int,
        /**
         * sort format is Spring's: "field,asc" or "field,desc"
         */
        @Query("sort") sort: String? = null,

        @Query("query") query: String? = null,
        @Query("category") category: String? = null,
        @Query("minPrice") minPrice: Double? = null,
        @Query("maxPrice") maxPrice: Double? = null
    ): PageResponseDto<ProductDto>

    @GET("/api/products/{id}")
    suspend fun getProductById(
        @Path("id") id: String
    ): ProductDto

    @POST("/api/products")
    suspend fun createProduct(
        @Body body: ProductCreateRequestDto
    ): ProductDto

    @PUT("/api/products/{id}")
    suspend fun updateProduct(
        @Path("id") id: String,
        @Body body: ProductUpdateRequestDto
    ): ProductDto

    @DELETE("/api/products/{id}")
    suspend fun deleteProduct(
        @Path("id") id: String
    )
}
