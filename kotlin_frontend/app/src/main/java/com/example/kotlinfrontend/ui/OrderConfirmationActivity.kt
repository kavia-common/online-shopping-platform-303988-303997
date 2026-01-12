package com.example.kotlinfrontend.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.databinding.ActivityOrderConfirmationBinding

/**
 * Simple order confirmation screen shown after successful checkout.
 */
class OrderConfirmationActivity : ComponentActivity() {

    private lateinit var binding: ActivityOrderConfirmationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOrderConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = "Order confirmed"
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val orderId = intent.getStringExtra(CheckoutNav.EXTRA_ORDER_ID).orEmpty()
        val status = intent.getStringExtra(CheckoutNav.EXTRA_ORDER_STATUS).orEmpty().ifBlank { "Pending" }
        val total = intent.getDoubleExtra(CheckoutNav.EXTRA_ORDER_TOTAL, 0.0)

        binding.orderIdValue.text = orderId.ifBlank { "—" }
        binding.orderStatusValue.text = status
        binding.orderTotalValue.text = "$" + String.format("%.2f", total)

        binding.viewOrdersButton.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }

        // Set result so CartActivity can show a success snackbar when user navigates back.
        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(CheckoutNav.RESULT_EXTRA_ORDER_PLACED, true)
                putExtra(CheckoutNav.RESULT_EXTRA_ORDER_ID, orderId)
            }
        )
    }
}
