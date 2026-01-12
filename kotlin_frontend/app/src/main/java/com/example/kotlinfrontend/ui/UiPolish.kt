package com.example.kotlinfrontend.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.View
import androidx.annotation.IntegerRes
import com.example.kotlinfrontend.R

/**
 * UI polish helpers: subtle animations and crossfades.
 *
 * Kept in a small file so durations and behavior are centralized and easy to adjust.
 */

// PUBLIC_INTERFACE
fun Context.animDuration(@IntegerRes resId: Int): Long {
    /** Resolve an integer resource duration (ms) as a Long. */
    return resources.getInteger(resId).toLong()
}

// PUBLIC_INTERFACE
fun crossfadeVisibility(
    view: View,
    show: Boolean,
    durationMs: Long
) {
    /**
     * Crossfade a View in/out without layout thrash.
     *
     * - When showing: sets alpha=0, visible, then fades to 1.
     * - When hiding: fades to 0, then sets GONE.
     *
     * This avoids "full-screen flicker" during paging refresh by only transitioning containers
     * when their visibility actually changes.
     */
    // Cancel any previous animations to prevent stacking.
    view.animate().cancel()

    if (show) {
        if (view.visibility == View.VISIBLE && view.alpha == 1f) return
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setListener(null)
            .start()
    } else {
        if (view.visibility != View.VISIBLE) return
        view.animate()
            .alpha(0f)
            .setDuration(durationMs)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    view.alpha = 1f // reset for next show()
                }
            })
            .start()
    }
}

// PUBLIC_INTERFACE
fun View.subtleAppear(durationMs: Long) {
    /**
     * A subtle appear animation for list items:
     * fade in + slight translate up.
     *
     * Safe to call multiple times; cancels previous animation.
     */
    animate().cancel()
    alpha = 0f
    translationY = 10f
    animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(durationMs)
        .setListener(null)
        .start()
}
