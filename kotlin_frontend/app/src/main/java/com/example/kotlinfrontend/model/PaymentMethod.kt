package com.example.kotlinfrontend.model

/**
 * Represents the payment method chosen during checkout.
 *
 * This app currently uses a simulated payment processing step. The selected method is still
 * attached to the order create payload as optional metadata to enable future backend support.
 */
sealed class PaymentMethod(
    open val id: String,
    open val displayName: String,
    open val subtitle: String? = null
) {

    data class Card(
        override val id: String = ID,
        override val displayName: String = "Credit / Debit Card",
        override val subtitle: String? = "Placeholder"
    ) : PaymentMethod(id, displayName, subtitle) {
        companion object {
            const val ID = "card"
        }
    }

    data class CashOnDelivery(
        override val id: String = ID,
        override val displayName: String = "Cash on Delivery",
        override val subtitle: String? = "Pay when the order arrives"
    ) : PaymentMethod(id, displayName, subtitle) {
        companion object {
            const val ID = "cod"
        }
    }

    data class OnlineWallet(
        override val id: String = ID,
        override val displayName: String = "Online Wallet",
        override val subtitle: String? = "Generic wallet option"
    ) : PaymentMethod(id, displayName, subtitle) {
        companion object {
            const val ID = "wallet"
        }
    }

    companion object {
        /**
         * Returns supported methods in a stable order for UI.
         */
        // PUBLIC_INTERFACE
        fun supported(): List<PaymentMethod> {
            /** Supported payment methods for checkout selection UI. */
            return listOf(Card(), OnlineWallet(), CashOnDelivery())
        }

        /**
         * Decode persisted id -> PaymentMethod instance.
         */
        // PUBLIC_INTERFACE
        fun fromId(id: String?): PaymentMethod? {
            /** Converts persisted payment method id to a PaymentMethod. */
            return when (id) {
                Card.ID -> Card()
                CashOnDelivery.ID -> CashOnDelivery()
                OnlineWallet.ID -> OnlineWallet()
                else -> null
            }
        }
    }
}
