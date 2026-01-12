package com.example.kotlinfrontend.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.data.CouponValidationState
import com.example.kotlinfrontend.databinding.ActivityCheckoutBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Checkout screen.
 *
 * Captures customer identity + shipping address (placeholders for now) and submits an order to backend.
 * Coupon support:
 * - Apply/remove coupon before placing order; totals update reactively.
 */
class CheckoutActivity : ComponentActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private lateinit var viewModel: CheckoutViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(
            this,
            CheckoutViewModelFactory(this, application)
        )[CheckoutViewModel::class.java]

        setupToolbar()
        setupFormListeners()
        bindState()

        viewModel.prefillEmailIfAvailable()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Checkout"
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupFormListeners() {
        binding.customerNameEditText.addTextChangedListener(SimpleTextWatcher { viewModel.updateCustomerName(it) })
        binding.emailEditText.addTextChangedListener(SimpleTextWatcher { viewModel.updateEmail(it) })
        binding.addressLine1EditText.addTextChangedListener(SimpleTextWatcher { viewModel.updateAddressLine1(it) })
        binding.cityEditText.addTextChangedListener(SimpleTextWatcher { viewModel.updateCity(it) })
        binding.stateEditText.addTextChangedListener(SimpleTextWatcher { viewModel.updateState(it) })
        binding.postalCodeEditText.addTextChangedListener(SimpleTextWatcher { viewModel.updatePostalCode(it) })
        binding.countryEditText.addTextChangedListener(SimpleTextWatcher { viewModel.updateCountry(it) })
        binding.noteEditText.addTextChangedListener(SimpleTextWatcher { viewModel.updateNote(it) })

        binding.checkoutCouponCodeEditText.addTextChangedListener(SimpleTextWatcher { viewModel.updateCouponInput(it) })
        binding.checkoutApplyCouponButton.setOnClickListener { viewModel.applyCouponFromCheckout() }
        binding.checkoutRemoveCouponButton.setOnClickListener { viewModel.removeCoupon() }

        binding.mockPaymentSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setMockPaymentSuccess(isChecked)
        }

        binding.placeOrderButton.setOnClickListener {
            viewModel.placeOrder()
        }
    }

    private fun bindState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.form.collectLatest { form ->
                    if (binding.customerNameEditText.text?.toString() != form.customerName) {
                        binding.customerNameEditText.setText(form.customerName)
                        binding.customerNameEditText.setSelection(form.customerName.length)
                    }
                    if (binding.emailEditText.text?.toString() != form.email) {
                        binding.emailEditText.setText(form.email)
                        binding.emailEditText.setSelection(form.email.length)
                    }
                    if (binding.addressLine1EditText.text?.toString() != form.addressLine1) {
                        binding.addressLine1EditText.setText(form.addressLine1)
                        binding.addressLine1EditText.setSelection(form.addressLine1.length)
                    }
                    if (binding.cityEditText.text?.toString() != form.city) {
                        binding.cityEditText.setText(form.city)
                        binding.cityEditText.setSelection(form.city.length)
                    }
                    if (binding.stateEditText.text?.toString() != form.state) {
                        binding.stateEditText.setText(form.state)
                        binding.stateEditText.setSelection(form.state.length)
                    }
                    if (binding.postalCodeEditText.text?.toString() != form.postalCode) {
                        binding.postalCodeEditText.setText(form.postalCode)
                        binding.postalCodeEditText.setSelection(form.postalCode.length)
                    }
                    if (binding.countryEditText.text?.toString() != form.country) {
                        binding.countryEditText.setText(form.country)
                        binding.countryEditText.setSelection(form.country.length)
                    }
                    if (binding.noteEditText.text?.toString() != form.note) {
                        binding.noteEditText.setText(form.note)
                        binding.noteEditText.setSelection(form.note.length)
                    }

                    if (binding.checkoutCouponCodeEditText.text?.toString() != form.couponInput) {
                        binding.checkoutCouponCodeEditText.setText(form.couponInput)
                        binding.checkoutCouponCodeEditText.setSelection(form.couponInput.length)
                    }

                    if (binding.mockPaymentSwitch.isChecked != form.mockPaymentSuccess) {
                        binding.mockPaymentSwitch.isChecked = form.mockPaymentSuccess
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cartItems.collectLatest { items ->
                    binding.itemsCountValue.text = items.sumOf { it.quantity }.toString()
                    binding.emptyCartWarning.isVisible = items.isEmpty()
                    binding.placeOrderButton.isEnabled = items.isNotEmpty()
                    binding.checkoutApplyCouponButton.isEnabled = items.isNotEmpty()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totals.collectLatest { totals ->
                    binding.orderSubtotalValue.text = "$" + String.format(Locale.US, "%.2f", totals.subtotal)
                    binding.orderTotalValue.text = "$" + String.format(Locale.US, "%.2f", totals.total)

                    val hasDiscount = totals.discount > 0.00001
                    binding.checkoutDiscountRow.isVisible = hasDiscount
                    binding.orderDiscountValue.text = "-$" + String.format(Locale.US, "%.2f", totals.discount)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.coupon.collectLatest { coupon ->
                    val hasCoupon = coupon != null
                    binding.checkoutAppliedCouponRow.isVisible = hasCoupon
                    if (hasCoupon) {
                        val desc = coupon?.description?.takeIf { it.isNotBlank() }
                        val label = if (desc != null) {
                            "Coupon: ${coupon.code} • $desc"
                        } else {
                            "Coupon: ${coupon?.code}"
                        }
                        binding.checkoutAppliedCouponText.text = label
                    } else {
                        binding.checkoutAppliedCouponText.text = "Coupon: -"
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.couponValidationState.collectLatest { state ->
                    when (state) {
                        is CouponValidationState.None -> binding.checkoutCouponInputLayout.error = null
                        is CouponValidationState.Valid -> {
                            binding.checkoutCouponInputLayout.error = null
                            Snackbar.make(binding.root, "Discount applied.", Snackbar.LENGTH_SHORT).show()
                        }
                        is CouponValidationState.PendingServerValidation -> binding.checkoutCouponInputLayout.error = null
                        is CouponValidationState.Invalid -> {
                            binding.checkoutCouponInputLayout.error = state.message
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fieldErrors.collectLatest { errors ->
                    binding.customerNameInputLayout.error = errors.customerName
                    binding.emailInputLayout.error = errors.email
                    binding.addressLine1InputLayout.error = errors.addressLine1
                    binding.cityInputLayout.error = errors.city
                    binding.stateInputLayout.error = errors.state
                    binding.postalCodeInputLayout.error = errors.postalCode
                    binding.countryInputLayout.error = errors.country
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.submitState.collectLatest { state ->
                    val isLoading = state is CheckoutViewModel.SubmitState.Loading
                    setLoading(isLoading)

                    when (state) {
                        is CheckoutViewModel.SubmitState.Success -> {
                            val intent = Intent(this@CheckoutActivity, OrderConfirmationActivity::class.java).apply {
                                putExtra(CheckoutNav.EXTRA_ORDER_ID, state.orderId)
                                putExtra(CheckoutNav.EXTRA_ORDER_STATUS, "Pending")
                                putExtra(CheckoutNav.EXTRA_ORDER_TOTAL, state.total)
                            }
                            startActivity(intent)
                            finish()
                        }

                        is CheckoutViewModel.SubmitState.Error -> {
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG)
                                .setAction("Retry") { viewModel.placeOrder() }
                                .show()
                        }

                        CheckoutViewModel.SubmitState.Idle,
                        CheckoutViewModel.SubmitState.Loading -> Unit
                    }
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.placeOrderButton.isEnabled = !loading && !(binding.emptyCartWarning.isVisible)
        binding.progressBar.isVisible = loading
        binding.formContainer.alpha = if (loading) 0.6f else 1.0f
        setAllEnabled(binding.formContainer, enabled = !loading)
        binding.placeOrderButton.isEnabled = !loading && !binding.emptyCartWarning.isVisible
    }

    private fun setAllEnabled(root: View, enabled: Boolean) {
        root.isEnabled = enabled
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                setAllEnabled(root.getChildAt(i), enabled)
            }
        }
    }
}
