package com.example.kotlinfrontend.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.databinding.ActivityCartBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CartActivity : ComponentActivity() {

    private lateinit var binding: ActivityCartBinding
    private lateinit var viewModel: CartViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[CartViewModel::class.java]

        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecycler()
        setupActions()
        bindState()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Cart"
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecycler() {
        val adapter = CartAdapter(
            onIncrement = { item ->
                viewModel.updateQty(item.productId, item.quantity + 1)
                Snackbar.make(binding.root, "Updated quantity.", Snackbar.LENGTH_SHORT).show()
            },
            onDecrement = { item ->
                val newQty = item.quantity - 1
                viewModel.updateQty(item.productId, newQty)
                Snackbar.make(binding.root, if (newQty <= 0) "Removed item." else "Updated quantity.", Snackbar.LENGTH_SHORT).show()
            },
            onRemove = { item ->
                viewModel.removeItem(item.productId)
                Snackbar.make(binding.root, "Removed item.", Snackbar.LENGTH_SHORT).show()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
    }

    private fun setupActions() {
        binding.clearCartButton.setOnClickListener {
            if (viewModel.itemCount.value <= 0) return@setOnClickListener

            MaterialAlertDialogBuilder(this)
                .setTitle("Clear cart?")
                .setMessage("Remove all items from your cart.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    viewModel.clear()
                    Snackbar.make(binding.root, "Cart cleared.", Snackbar.LENGTH_SHORT).show()
                }
                .show()
        }

        binding.checkoutButton.setOnClickListener {
            Snackbar.make(binding.root, "Checkout is not implemented yet.", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun bindState() {
        val adapter = binding.recyclerView.adapter as CartAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.groupedRows.collectLatest { rows ->
                    adapter.submitList(rows)
                    val isEmpty = rows.isEmpty()
                    binding.emptyState.isVisible = isEmpty
                    binding.contentContainer.isVisible = !isEmpty
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.summary.collectLatest { summary ->
                    binding.subtotalValue.text = "$" + String.format("%.2f", summary.subtotal)
                    binding.itemCountValue.text = "${summary.totalQuantity}"
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.itemCount.collectLatest { count ->
                    binding.clearCartButton.isEnabled = count > 0
                    binding.checkoutButton.isEnabled = count > 0
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
