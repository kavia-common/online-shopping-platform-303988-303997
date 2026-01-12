package com.example.kotlinfrontend.data

import java.util.Locale

/**
 * UI-oriented coupon feedback.
 */
data class CouponUxFeedback(
    val type: Type,
    val message: String
) {
    enum class Type {
        NONE,
        APPLIED,
        INVALID_RULE,
        NETWORK
    }
}

/**
 * Maps backend / repository coupon validation messages into friendlier UI copy.
 *
 * The backend message formats may evolve; this mapper uses conservative substring matching and
 * falls back to the original message when unknown.
 */
object CouponErrorMessageMapper {

    // PUBLIC_INTERFACE
    fun map(rawMessage: String?): String {
        /** Map a raw backend message into friendly user-facing text. */
        val msg = rawMessage?.trim().orEmpty()
        if (msg.isBlank()) return "This coupon can't be applied."

        val lower = msg.lowercase(Locale.US)

        // Min subtotal / minimum order
        if (lower.contains("min subtotal") ||
            lower.contains("minimum subtotal") ||
            lower.contains("minimum order") ||
            lower.contains("min order")
        ) {
            return "This coupon requires a higher cart subtotal. Add a few more items and try again."
        }

        // Category / not applicable
        if (lower.contains("category") && (lower.contains("not") || lower.contains("ineligible") || lower.contains("applicable").not())) {
            return "This coupon isn't eligible for the items in your cart."
        }
        if (lower.contains("not applicable") || lower.contains("not eligible") || lower.contains("ineligible")) {
            return "This coupon isn't eligible for your cart."
        }

        // Usage / exhausted
        if (lower.contains("usage") && (lower.contains("exhaust") || lower.contains("limit") || lower.contains("used"))) {
            return "This coupon has reached its usage limit."
        }
        if (lower.contains("exhausted")) {
            return "This coupon has reached its usage limit."
        }

        // Expired
        if (lower.contains("expired")) {
            return "This coupon has expired."
        }

        // Not found / invalid
        if (lower.contains("not found") || lower.contains("invalid coupon") || lower.contains("invalid code")) {
            return "That coupon code isn't recognized. Double-check the spelling and try again."
        }

        // Generic fallback
        return msg
    }
}
