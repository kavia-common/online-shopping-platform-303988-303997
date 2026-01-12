package com.example.kotlinfrontend.network

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // PUBLIC_INTERFACE
    fun createProductApi(
        baseUrl: String = ApiConfig.DEFAULT_BASE_URL
    ): ProductApi {
        /** Create a ProductApi instance using the provided baseUrl. */
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

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(ProductApi::class.java)
    }
}
