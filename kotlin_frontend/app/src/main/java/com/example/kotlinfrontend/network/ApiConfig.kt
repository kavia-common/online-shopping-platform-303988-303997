package com.example.kotlinfrontend.network

/**
 * API configuration for the app.
 *
 * Note: Android emulators cannot reach your machine's localhost via "localhost".
 * If you are running the backend on your development machine and testing on emulator,
 * you typically need to use "http://10.0.2.2:3010" instead.
 */
object ApiConfig {
    const val DEFAULT_BASE_URL = "http://localhost:3010"
}
