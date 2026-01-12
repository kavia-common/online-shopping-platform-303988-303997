package com.example.kotlinfrontend.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.databinding.ActivityCartBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CartActivity : ComponentActivity() {

    private lateinit var binding: ActivityCartBinding
    private lateinit var viewModel: CartViewModel

    // Tracks currently-swiped deletion so undo can restore original quantity.
    private var pendingUndo: PendingUndo? = null

    data class PendingUndo(
        val productId: String,
        val name: String,
        val previousQuantity: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[CartViewModel::class.java]

        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecycler()
        setupActions()
        bindState()
        bindErrorsAndIdentity()
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
                Snackbar.make(
                    binding.root,
                    if (newQty <= 0) "Removed item." else "Updated quantity.",
                    Snackbar.LENGTH_SHORT
                ).show()
            },
            onRemove = { item ->
                viewModel.removeItem(item.productId)
                Snackbar.make(binding.root, "Removed item.", Snackbar.LENGTH_SHORT).show()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Use default animator; disable change animations to avoid flicker on qty updates.
        (binding.recyclerView.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false

        attachSwipeToDelete(binding.recyclerView, adapter)
    }

    private fun attachSwipeToDelete(recyclerView: RecyclerView, adapter: CartAdapter) {
        val touchCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                // Only item rows are swipeable; headers should not swipe.
                val pos = vh.bindingAdapterPosition
                return if (adapter.getCartItemAt(pos) != null) {
                    super.getSwipeDirs(rv, vh)
                } else {
                    0
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                val item = adapter.getCartItemAt(pos)
                if (item == null) {
                    adapter.notifyItemChanged(pos)
                    return
                }

                // Remove from repo (maintains existing contracts + badge behavior via existing flows).
                pendingUndo = PendingUndo(
                    productId = item.productId,
                    name = item.name,
                    previousQuantity = item.quantity
                )

                viewModel.removeItem(item.productId)

                announceForA11y(binding.root, "Removed ${item.name}. Undo available.")

                Snackbar.make(binding.root, "Removed ${item.name}", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        val undo = pendingUndo ?: return@setAction
                        // Restore previous quantity for the same productId.
                        viewModel.updateQty(undo.productId, undo.previousQuantity)
                        announceForA11y(binding.root, "Restored ${undo.name}.")
                    }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            // If it timed out or was dismissed, clear pending state.
                            pendingUndo = null
                        }
                    })
                    .show()
            }
        }

        ItemTouchHelper(touchCallback).attachToRecyclerView(recyclerView)
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
            // If identity email is missing, prompt here to align with CartIdentity flow.
            val email = viewModel.activeEmail.value
            if (email.isNullOrBlank()) {
                CartIdentityPrompter.promptForEmail(
                    context = this@CartActivity,
                    title = "Enter email to checkout",
                    message = "We use email as a temporary identity for your cart and order.",
                    prefill = null,
                    onEmailSaved = { entered ->
                        viewModel.setActiveEmail(entered)
                        openCheckout()
                    }
                )
            } else {
                openCheckout()
            }
        }

        binding.browseProductsButton.setOnClickListener {
            // Navigate back to product list (simple and consistent with current nav approach).
            finish()
        }
    }

    private fun openCheckout() {
        startActivityForResult(Intent(this, CheckoutActivity::class.java), REQUEST_CHECKOUT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CHECKOUT && resultCode == Activity.RESULT_OK) {
            val placed = data?.getBooleanExtra(CheckoutNav.RESULT_EXTRA_ORDER_PLACED, false) == true
            val orderId = data?.getStringExtra(CheckoutNav.RESULT_EXTRA_ORDER_ID)
            if (placed) {
                val msg = if (!orderId.isNullOrBlank()) "Order placed: $orderId" else "Order placed."
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                    .setAction("View orders") {
                        startActivity(Intent(this, OrdersActivity::class.java))
                    }
                    .show()
            }
        }
    }

    private companion object {
        private const val REQUEST_CHECKOUT = 1001
    }

    private fun bindState() {
        val adapter = binding.recyclerView.adapter as CartAdapter
        val crossfadeDuration = animDuration(R.integer.anim_crossfade_duration_ms)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.groupedRows.collectLatest { rows ->
                    adapter.submitList(rows)

                    val isEmpty = rows.isEmpty()
                    // Crossfade between empty and content to avoid full-screen flicker during refreshes.
                    crossfadeVisibility(binding.emptyState, show = isEmpty, durationMs = crossfadeDuration)
                    crossfadeVisibility(binding.contentContainer, show = !isEmpty, durationMs = crossfadeDuration)
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

    private fun bindErrorsAndIdentity() {
        // Prompt for identity if missing; otherwise load from backend.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeEmail.collectLatest { email ->
                    if (email.isNullOrBlank()) {
                        CartIdentityPrompter.promptForEmail(
                            context = this@CartActivity,
                            onEmailSaved = { entered ->
                                viewModel.setActiveEmail(entered)
                            }
                        )
                    } else {
                        viewModel.ensureCartLoaded()
                    }
                }
            }
        }

        // Show transient network errors (offline fallback).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorEvents.collectLatest { event ->
                    Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                        .setAction("Retry") {
                            // Retry is intentionally simple: user action is re-triggered by UI;
                            // for load errors, we can re-ensure cart is loaded.
                            if (event.operation == com.example.kotlinfrontend.data.CartErrorEvent.Operation.GET_CART) {
                                viewModel.ensureCartLoaded()
                            }
                        }
                        .show()
                }
            }
        }
    }

    private fun announceForA11y(view: View, message: String) {
        view.announceForAccessibility(message)
        view.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
