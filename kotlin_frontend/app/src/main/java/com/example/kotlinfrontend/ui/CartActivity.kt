package com.example.kotlinfrontend.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import com.example.kotlinfrontend.data.CartErrorEvent
import com.example.kotlinfrontend.data.CouponUxFeedback
import com.example.kotlinfrontend.data.CouponValidationState
import com.example.kotlinfrontend.databinding.ActivityCartBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class CartActivity : ComponentActivity() {

    private lateinit var binding: ActivityCartBinding
    private lateinit var viewModel: CartViewModel

    private var suggestionsAdapter: ArrayAdapter<String>? = null

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
        setupCouponSuggestions()
        bindState()
        bindErrorsAndIdentity()
        bindCouponUi()
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
            ): Boolean = false

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
                        viewModel.updateQty(undo.productId, undo.previousQuantity)
                        announceForA11y(binding.root, "Restored ${undo.name}.")
                    }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
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

        binding.browseProductsButton.setOnClickListener { finish() }

        // Coupon actions
        binding.applyCouponButton.setOnClickListener {
            val code = binding.couponCodeEditText.text?.toString().orEmpty()
            viewModel.applyCoupon(code)
        }
        binding.removeCouponButton.setOnClickListener {
            viewModel.removeCoupon()
        }

        binding.manageSavedCouponsButton.setOnClickListener {
            showManageSavedCouponsDialog()
        }

        binding.activeCouponChip.setOnCloseIconClickListener {
            viewModel.removeCoupon()
        }

        binding.couponInputLayout.setEndIconOnClickListener {
            // Show dropdown suggestions even if the user hasn't typed yet.
            (binding.couponCodeEditText as? AutoCompleteTextView)?.showDropDown()
        }
    }

    private fun setupCouponSuggestions() {
        val actv = binding.couponCodeEditText as AutoCompleteTextView
        suggestionsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        actv.setAdapter(suggestionsAdapter)
        actv.threshold = 0

        // Selecting a suggestion fills input and attempts validation.
        actv.setOnItemClickListener { _, _, position, _ ->
            val item = suggestionsAdapter?.getItem(position) ?: return@setOnItemClickListener
            actv.setText(item, false)
            actv.setSelection(item.length)
            viewModel.applyCoupon(item)
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
                    crossfadeVisibility(binding.emptyState, show = isEmpty, durationMs = crossfadeDuration)
                    crossfadeVisibility(binding.contentContainer, show = !isEmpty, durationMs = crossfadeDuration)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totals.collectLatest { totals ->
                    binding.subtotalValue.text = "$" + String.format(Locale.US, "%.2f", totals.subtotal)
                    binding.totalValue.text = "$" + String.format(Locale.US, "%.2f", totals.total)

                    val hasDiscount = totals.discount > 0.00001
                    binding.discountRow.isVisible = hasDiscount
                    binding.discountValue.text = "-$" + String.format(Locale.US, "%.2f", totals.discount)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.summary.collectLatest { summary ->
                    binding.itemCountValue.text = "${summary.totalQuantity}"
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.itemCount.collectLatest { count ->
                    binding.clearCartButton.isEnabled = count > 0
                    binding.checkoutButton.isEnabled = count > 0
                    binding.applyCouponButton.isEnabled = count > 0
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.couponSuggestions.collectLatest { list ->
                    suggestionsAdapter?.clear()
                    suggestionsAdapter?.addAll(list)
                    suggestionsAdapter?.notifyDataSetChanged()
                }
            }
        }
    }

    private fun bindCouponUi() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.coupon.collectLatest { coupon ->
                    val hasCoupon = coupon != null
                    binding.appliedCouponRow.isVisible = hasCoupon
                    binding.removeCouponButton.isVisible = hasCoupon

                    if (hasCoupon) {
                        val desc = coupon?.description?.takeIf { it.isNotBlank() }
                        val label = if (desc != null) {
                            "${coupon.code} • $desc"
                        } else {
                            coupon?.code.orEmpty()
                        }
                        binding.activeCouponChip.text = label
                        // Preserve user typed text on invalid; but on confirmed coupon state, keep it in sync.
                        if (binding.couponCodeEditText.text?.toString() != coupon?.code) {
                            binding.couponCodeEditText.setText(coupon?.code.orEmpty())
                            binding.couponCodeEditText.setSelection(binding.couponCodeEditText.text?.length ?: 0)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.couponValidationState.collectLatest { state ->
                    when (state) {
                        is CouponValidationState.None -> {
                            binding.couponInputLayout.error = null
                        }

                        is CouponValidationState.Valid -> {
                            binding.couponInputLayout.error = null
                            // No snackbar here; keep it subtle and use helper text.
                            announceForA11y(binding.root, "Discount applied.")
                        }

                        is CouponValidationState.PendingServerValidation -> {
                            // No inline error; keep it subtle.
                            binding.couponInputLayout.error = null
                        }

                        is CouponValidationState.Invalid -> {
                            // Inline error only; keep typed code.
                            binding.couponInputLayout.error = state.message
                            announceForA11y(binding.root, "Coupon invalid.")
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lastCouponUxFeedback.collectLatest { fb ->
                    when (fb.type) {
                        CouponUxFeedback.Type.NONE -> {
                            binding.couponHelperText.isVisible = false
                            binding.couponHelperText.text = ""
                        }

                        CouponUxFeedback.Type.APPLIED -> {
                            // Show a helpful summary including discount amount if available.
                            val discount = viewModel.totals.value.discount
                            val discountText =
                                if (discount > 0.00001) "Saved $" + String.format(Locale.US, "%.2f", discount) else "Coupon applied"
                            binding.couponHelperText.isVisible = true
                            binding.couponHelperText.text = discountText
                        }

                        CouponUxFeedback.Type.INVALID_RULE -> {
                            binding.couponHelperText.isVisible = true
                            binding.couponHelperText.text = fb.message
                        }

                        CouponUxFeedback.Type.NETWORK -> {
                            // Network should still be snackbars; keep helper hidden.
                            binding.couponHelperText.isVisible = false
                        }
                    }
                }
            }
        }
    }

    private fun bindErrorsAndIdentity() {
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorEvents.collectLatest { event ->
                    // Snackbars are reserved for transient network/server operational failures.
                    val snack = Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)

                    val canRetry = when (event.operation) {
                        CartErrorEvent.Operation.GET_CART -> true
                        CartErrorEvent.Operation.APPLY_COUPON -> true
                        CartErrorEvent.Operation.REMOVE_COUPON -> true
                        else -> false
                    }

                    if (canRetry) {
                        snack.setAction("Retry") {
                            when (event.operation) {
                                CartErrorEvent.Operation.GET_CART -> viewModel.ensureCartLoaded()
                                CartErrorEvent.Operation.APPLY_COUPON -> {
                                    val code = binding.couponCodeEditText.text?.toString().orEmpty()
                                    viewModel.applyCoupon(code)
                                }

                                CartErrorEvent.Operation.REMOVE_COUPON -> viewModel.removeCoupon()
                                else -> Unit
                            }
                        }
                    }
                    snack.show()
                }
            }
        }
    }

    private fun showManageSavedCouponsDialog() {
        val ctx = this
        val rv = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
        }

        val adapter = SavedCouponsAdapter(
            onSelect = { code ->
                binding.couponCodeEditText.setText(code)
                (binding.couponCodeEditText as? AutoCompleteTextView)?.dismissDropDown()
                viewModel.applyCoupon(code)
            },
            onRemove = { code ->
                viewModel.removeSavedCoupon(code)
            }
        )
        rv.adapter = adapter

        // Initial list + keep updated while dialog open via a single snapshot at creation.
        adapter.submitList(viewModel.savedCoupons.value)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("Saved coupons")
            .setView(rv)
            .setNegativeButton("Close", null)
            .setNeutralButton("Clear all") { _, _ ->
                viewModel.clearSavedCoupons()
            }
            .show()

        // Refresh list when dialog is shown (in case it changed between click and show).
        dialog.setOnShowListener {
            adapter.submitList(viewModel.savedCoupons.value)
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
