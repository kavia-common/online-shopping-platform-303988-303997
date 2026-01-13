package com.example.kotlinfrontend.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.kotlinfrontend.data.AppRepositories
import com.example.kotlinfrontend.databinding.ActivityOrderConfirmationBinding

/**
 * Order confirmation screen shown after successful checkout.
 *
 * In demo mode, this triggers simulated order lifecycle notifications.
 */
class OrderConfirmationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderConfirmationBinding
    private lateinit var repos: AppRepositories

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOrderConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repos = AppRepositories(applicationContext)

        // Demo hook: when user reaches confirmation screen, treat as "order placed".
        repos.demoNotificationsEngine.onOrderPlacedHook()

        binding.continueShoppingButton.setOnClickListener {
            finish()
        }
    }
}
