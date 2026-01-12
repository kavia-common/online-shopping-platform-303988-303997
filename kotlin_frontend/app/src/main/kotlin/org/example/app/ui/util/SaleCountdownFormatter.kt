package org.example.app.ui.util

/**
 * Utility for formatting a remaining duration into a compact countdown string.
 *
 * This is intentionally small and dependency-free.
 */
object SaleCountdownFormatter {

    /**
     * PUBLIC_INTERFACE
     *
     * Formats remaining milliseconds into:
     * - "H:MM:SS" when >= 1 hour
     * - "M:SS" when < 1 hour
     *
     * Returns "0:00" when [remainingMillis] is null or <= 0.
     */
    // PUBLIC_INTERFACE
    fun formatRemaining(remainingMillis: Long?): String {
        if (remainingMillis == null || remainingMillis <= 0L) return "0:00"

        val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return if (hours > 0L) {
            // H:MM:SS
            "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            // M:SS
            "${minutes}:${seconds.toString().padStart(2, '0')}"
        }
    }
}
