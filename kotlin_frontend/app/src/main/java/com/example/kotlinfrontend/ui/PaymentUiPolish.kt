package com.example.kotlinfrontend.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.example.kotlinfrontend.R
import com.google.android.material.textfield.TextInputLayout

/**
 * Payment UI polish helpers: subtle validation animations + accessibility announcements.
 *
 * These animations are intentionally lightweight to match the Ocean Professional theme.
 */

// PUBLIC_INTERFACE
fun View.shakeForValidation() {
    /** Brief, subtle shake used when a field becomes invalid on blur/submit. */
    animate().cancel()
    val dx = 10f
    translationX = 0f
    animate()
        .translationX(dx)
        .setDuration(50)
        .withEndAction {
            animate()
                .translationX(-dx)
                .setDuration(50)
                .withEndAction {
                    animate()
                        .translationX(dx * 0.6f)
                        .setDuration(45)
                        .withEndAction {
                            animate()
                                .translationX(0f)
                                .setDuration(45)
                                .start()
                        }
                        .start()
                }
                .start()
        }
        .start()
}

// PUBLIC_INTERFACE
fun TextInputLayout.pulseSuccess(context: Context) {
    /**
     * Gentle success cue: brief pulse + a soft success tint for the outline.
     * No-op if the field is currently showing an error.
     */
    if (!error.isNullOrBlank()) return

    // Keep the tint subtle (Ocean secondary/amber acts as a reassuring accent in this theme).
    // Use setBoxStrokeColor(int) for broad MaterialComponents compatibility.
    val accentColor = ContextCompat.getColor(context, R.color.ocean_secondary)
    val originalStrokeColor = boxStrokeColor
    try {
        setBoxStrokeColor(accentColor)
    } catch (_: Throwable) {
        // Defensive: some styles might not allow overriding; animation still gives feedback.
    }

    val target = editText ?: this
    target.animate().cancel()
    target.scaleX = 1f
    target.scaleY = 1f
    target.animate()
        .scaleX(1.02f)
        .scaleY(1.02f)
        .setDuration(120)
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // Restore stroke to avoid leaving a persistent success tint.
                try {
                    setBoxStrokeColor(originalStrokeColor)
                } catch (_: Throwable) {
                    // ignore
                }

                target.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(140)
                    .setListener(null)
                    .start()
            }
        })
        .start()
}

// PUBLIC_INTERFACE
fun View.announceForAccessibilityPolite(message: String) {
    /**
     * Announces a message to accessibility services.
     * Uses TYPE_ANNOUNCEMENT to make sure TalkBack reads it even if focus doesn't change.
     */
    if (message.isBlank()) return
    val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
    event.text.add(message)
    event.className = javaClass.name
    event.packageName = context.packageName
    parent?.requestSendAccessibilityEvent(this, event)
}
