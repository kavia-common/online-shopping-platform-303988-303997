package com.example.kotlinfrontend.ui

import android.content.Context
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object CartIdentityPrompter {

    // PUBLIC_INTERFACE
    fun promptForEmail(
        context: Context,
        title: String = "Enter email",
        message: String = "We use email as a temporary identity to persist your cart.",
        prefill: String? = null,
        onEmailSaved: (email: String) -> Unit
    ) {
        /** Shows a lightweight dialog prompting user for an email to use as cart identity. */
        val input = EditText(context).apply {
            hint = "you@example.com"
            setText(prefill.orEmpty())
            setSelection(text?.length ?: 0)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val email = input.text?.toString().orEmpty().trim()
                if (email.isNotBlank()) {
                    onEmailSaved(email)
                }
            }
            .show()
    }
}
