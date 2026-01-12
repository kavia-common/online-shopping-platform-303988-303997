package org.example.app.ui.product

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import org.example.app.R
import org.example.app.data.ShopRepository

class ProductDetailsFragment : Fragment(R.layout.fragment_product_details) {

    private var productId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        productId = arguments?.getString("productId")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvName = view.findViewById<TextView>(R.id.tv_name)
        val tvCategory = view.findViewById<TextView>(R.id.tv_category)
        val tvPrice = view.findViewById<TextView>(R.id.tv_price)
        val tvDescription = view.findViewById<TextView>(R.id.tv_description)

        val btnAddToCart = view.findViewById<MaterialButton>(R.id.btn_add_to_cart)
        val btnGoToCart = view.findViewById<MaterialButton>(R.id.btn_go_to_cart)

        val p = productId?.let { ShopRepository.findProductById(it) }
        if (p == null) {
            tvName.text = "Product not found"
            tvCategory.text = ""
            tvPrice.text = ""
            tvDescription.text = ""
            btnAddToCart.isEnabled = false
        } else {
            tvName.text = p.name
            tvCategory.text = ShopRepository.getCategoryName(p.categoryId)
            tvPrice.text = p.formattedPrice()
            tvDescription.text = p.description

            btnAddToCart.setOnClickListener {
                ShopRepository.addToCart(p.id, 1)
            }
        }

        btnGoToCart.setOnClickListener {
            findNavController().navigate(R.id.action_productDetails_to_cart)
        }
    }
}
