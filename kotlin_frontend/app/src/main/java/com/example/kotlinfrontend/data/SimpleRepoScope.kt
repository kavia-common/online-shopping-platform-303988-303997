package com.example.kotlinfrontend.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Small helper to run best-effort background work without introducing a DI framework.
 *
 * Note: This is intentionally minimal; long-term we'd use WorkManager for guaranteed delivery.
 */
internal object SimpleRepoScope {

    // PUBLIC_INTERFACE
    fun launchIo(context: Context, block: suspend CoroutineScope.() -> Unit): Job {
        /** Launch a fire-and-forget IO coroutine. */
        // Context currently unused; kept for future (e.g., structured cancellation hooks).
        return CoroutineScope(Dispatchers.IO).launch(block = block)
    }
}
