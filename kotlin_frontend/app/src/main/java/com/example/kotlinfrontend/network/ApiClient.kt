package com.example.kotlinfrontend.network

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private fun createRetrofit(baseUrl: String): Retrofit {
        val moshi = Moshi.Builder().build()

        val logging = HttpLoggingInterceptor().apply {
            // Keep BASIC to reduce noise while still being useful for debugging.
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    // PUBLIC_INTERFACE
    fun createProductApi(
        baseUrl: String = ApiConfig.DEFAULT_BASE_URL
    ): ProductApi {
        /** Create a ProductApi instance using the provided baseUrl. */
        return createRetrofit(baseUrl).create(ProductApi::class.java)
    }

    // PUBLIC_INTERFACE
    fun createOrderApi(
        baseUrl: String = ApiConfig.DEFAULT_BASE_URL
    ): OrderApi {
        /** Create an OrderApi instance using the provided baseUrl. */
        return createRetrofit(baseUrl).create(OrderApi::class.java)
    }

    // PUBLIC_INTERFACE
    fun createCartApi(
        baseUrl: String = ApiConfig.DEFAULT_BASE_URL
    ): CartApi {
        /** Create a CartApi instance using the provided baseUrl. */
        return createRetrofit(baseUrl).create(CartApi::class.java)
    }
}
