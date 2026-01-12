package org.example.app.ui.cart

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.example.app.R
import org.example.app.data.ShopRepository

class CartFragment : Fragment(R.layout.fragment_cart) {

    private lateinit var rvCart: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvTotal: TextView
    private lateinit var btnCheckout: MaterialButton

    private val adapter = CartAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvCart = view.findViewById(R.id.rv_cart)
        tvEmpty = view.findViewById(R.id.tv_empty)
        tvTotal = view.findViewById(R.id.tv_total)
        btnCheckout = view.findViewById(R.id.btn_checkout)

        rvCart.layoutManager = LinearLayoutManager(requireContext())
        rvCart.adapter = adapter

        // Observe global cart state and update UI.
        ShopRepository.cartLiveData().observe(viewLifecycleOwner) {
            val items = ShopRepository.cartItems()
            adapter.submit(items)

            val isEmpty = items.isEmpty()
            tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE

            val totalCents = ShopRepository.cartTotalCents()
            tvTotal.text = "$" + String.format("%.2f", totalCents / 100.0)

            btnCheckout.isEnabled = !isEmpty
        }

        btnCheckout.setOnClickListener {
            // Mock checkout: clear cart.
            ShopRepository.clearCart()
        }
    }
}
