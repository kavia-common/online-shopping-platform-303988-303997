package com.example.kotlinfrontend.network

import com.example.kotlinfrontend.network.dto.NotificationsDtos
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface for notifications backend.
 *
 * Backend placeholder:
 * - GET /api/notifications
 * - POST /api/notifications/read
 * - POST /api/notifications/readAll
 *
 * Repository will gracefully fall back to local-only storage if these endpoints are unavailable.
 */
interface NotificationApi {

    @GET("/api/notifications")
    suspend fun getNotifications(): List<NotificationsDtos.NotificationDto>

    @POST("/api/notifications/read")
    suspend fun markRead(@Body body: NotificationsDtos.MarkReadRequest): Unit

    @POST("/api/notifications/readAll")
    suspend fun markAllRead(): Unit
}
