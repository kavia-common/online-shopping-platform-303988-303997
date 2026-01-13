package com.example.kotlinfrontend.ui

import java.util.Calendar
import kotlin.math.min

/**
 * Utilities for payment card input formatting and validation.
 *
 * Security note: This file provides only in-memory formatting/validation helpers; it does not
 * persist or transmit full PAN (card number) or CVC.
 */
object PaymentCardUtils {

    enum class CardBrand(val id: String, val displayName: String, val cvcLength: Int) {
        Visa("visa", "Visa", 3),
        Mastercard("mastercard", "Mastercard", 3),
        Amex("amex", "American Express", 4),
        Discover("discover", "Discover", 3),
        Unknown("unknown", "Card", 3);

        companion object {
            // PUBLIC_INTERFACE
            fun fromId(id: String?): CardBrand {
                /** Maps persisted brand id to enum. Defaults to Unknown. */
                return entries.firstOrNull { it.id == id } ?: Unknown
            }
        }
    }

    data class Expiry(val month: Int, val year2: Int)

    // PUBLIC_INTERFACE
    fun digitsOnly(input: String): String {
        /** Returns only numeric digits from the input string. */
        return input.filter { it.isDigit() }
    }

    // PUBLIC_INTERFACE
    fun detectBrand(cardNumberDigits: String): CardBrand {
        /**
         * Best-effort card brand detection based on IIN/BIN prefixes.
         * This is intentionally simple and not exhaustive.
         */
        if (cardNumberDigits.isBlank()) return CardBrand.Unknown

        // AMEX: 34, 37
        if (cardNumberDigits.startsWith("34") || cardNumberDigits.startsWith("37")) return CardBrand.Amex
        // Visa: 4
        if (cardNumberDigits.startsWith("4")) return CardBrand.Visa

        // Mastercard: 51-55, 2221-2720
        val first2 = cardNumberDigits.take(2).toIntOrNull()
        val first4 = cardNumberDigits.take(4).toIntOrNull()
        if (first2 != null && first2 in 51..55) return CardBrand.Mastercard
        if (first4 != null && first4 in 2221..2720) return CardBrand.Mastercard

        // Discover: 6011, 65, 644-649
        if (cardNumberDigits.startsWith("6011") || cardNumberDigits.startsWith("65")) return CardBrand.Discover
        val first3 = cardNumberDigits.take(3).toIntOrNull()
        if (first3 != null && first3 in 644..649) return CardBrand.Discover

        return CardBrand.Unknown
    }

    // PUBLIC_INTERFACE
    fun formatCardNumber(input: String): String {
        /**
         * Formats the card number for display:
         * - AMEX-like numbers grouped as 4-6-5 (max 15)
         * - Others grouped as 4-4-4-4 (max 19)
         */
        val digits = digitsOnly(input)
        val brand = detectBrand(digits)

        val maxLen = if (brand == CardBrand.Amex) 15 else 19
        val trimmed = digits.take(maxLen)

        return if (brand == CardBrand.Amex) {
            // 4-6-5
            val a = trimmed.take(4)
            val b = trimmed.drop(4).take(6)
            val c = trimmed.drop(10).take(5)
            listOf(a, b, c).filter { it.isNotBlank() }.joinToString(" ")
        } else {
            trimmed.chunked(4).joinToString(" ")
        }
    }

    // PUBLIC_INTERFACE
    fun formatExpiry(input: String): String {
        /**
         * Formats expiry as MM/YY while typing.
         * Accepts digits only and inserts '/' after 2 digits.
         */
        val digits = digitsOnly(input).take(4)
        if (digits.length <= 2) return digits
        return digits.take(2) + "/" + digits.drop(2)
    }

    // PUBLIC_INTERFACE
    fun parseExpiry(formattedOrRaw: String): Expiry? {
        /** Parses expiry input (MM/YY or digits) into month + 2-digit year. */
        val digits = digitsOnly(formattedOrRaw)
        if (digits.length < 4) return null
        val mm = digits.take(2).toIntOrNull() ?: return null
        val yy = digits.drop(2).take(2).toIntOrNull() ?: return null
        return Expiry(month = mm, year2 = yy)
    }

    // PUBLIC_INTERFACE
    fun isExpiryValidAndNotPast(expiry: Expiry, now: Calendar = Calendar.getInstance()): Boolean {
        /**
         * Validates:
         * - month 1..12
         * - not in the past (current month is acceptable)
         *
         * Year is treated as 2000-2099 based on 2-digit year.
         */
        if (expiry.month !in 1..12) return false

        val currentYear2 = now.get(Calendar.YEAR) % 100
        val currentMonth = now.get(Calendar.MONTH) + 1

        return when {
            expiry.year2 > currentYear2 -> true
            expiry.year2 == currentYear2 -> expiry.month >= currentMonth
            else -> false
        }
    }

    // PUBLIC_INTERFACE
    fun luhnValid(cardNumberDigits: String): Boolean {
        /** Returns true if cardNumberDigits passes the Luhn checksum. */
        val digits = cardNumberDigits.filter { it.isDigit() }
        if (digits.length < 12) return false

        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i].code - '0'.code
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    // PUBLIC_INTERFACE
    fun last4(cardNumberDigits: String): String? {
        /** Returns last4 digits if present. */
        val digits = cardNumberDigits.filter { it.isDigit() }
        if (digits.length < 4) return null
        return digits.takeLast(4)
    }

    // PUBLIC_INTERFACE
    fun maxCvcLengthForBrand(brand: CardBrand): Int {
        /** Returns expected CVC length for brand (Amex=4, others=3). */
        return brand.cvcLength
    }

    // PUBLIC_INTERFACE
    fun normalizeZip(zip: String): String {
        /**
         * Normalizes ZIP/postal input for the optional field:
         * - Trim
         * - Keep letters/digits and common separators
         * This is intentionally lenient across countries.
         */
        return zip.trim().filter { it.isLetterOrDigit() || it == '-' || it == ' ' }.take(12)
    }

    // PUBLIC_INTERFACE
    fun isZipPlausible(zip: String): Boolean {
        /** Optional ZIP field: if provided, require at least 3 chars after trimming. */
        val z = normalizeZip(zip)
        if (z.isBlank()) return true
        return z.length >= min(3, z.length)
    }
}
