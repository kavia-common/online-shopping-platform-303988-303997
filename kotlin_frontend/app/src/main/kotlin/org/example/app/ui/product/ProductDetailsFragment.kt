package org.example.app.ui.product

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import org.example.app.R
import org.example.app.data.Product
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
        val btnFavorite = view.findViewById<ImageButton>(R.id.btn_favorite)

        val btnAddToCart = view.findViewById<MaterialButton>(R.id.btn_add_to_cart)
        val btnGoToCart = view.findViewById<MaterialButton>(R.id.btn_go_to_cart)

        val p = productId?.let { ShopRepository.findProductById(it) }
        if (p == null) {
            tvName.text = "Product not found"
            tvCategory.text = ""
            tvPrice.text = ""
            tvDescription.text = ""
            btnAddToCart.isEnabled = false
            btnFavorite.isEnabled = false
        } else {
            bindProduct(p, tvName, tvCategory, tvPrice, tvDescription)

            btnAddToCart.setOnClickListener {
                ShopRepository.addToCart(p.id, 1)
            }

            btnFavorite.setOnClickListener {
                ShopRepository.toggleFavorite(p.id)
                // UI will update via favorites observer below.
            }

            // Keep favorite icon in sync if changed elsewhere (e.g., from Catalog).
            ShopRepository.observeFavorites().observe(viewLifecycleOwner) {
                updateFavoriteUi(btnFavorite, p)
            }
            // Initial state
            updateFavoriteUi(btnFavorite, p)
        }

        btnGoToCart.setOnClickListener {
            findNavController().navigate(R.id.action_productDetails_to_cart)
        }
    }

    private fun bindProduct(
        p: Product,
        tvName: TextView,
        tvCategory: TextView,
        tvPrice: TextView,
        tvDescription: TextView
    ) {
        tvName.text = p.name
        tvCategory.text = ShopRepository.getCategoryName(p.categoryId)
        tvPrice.text = p.formattedPrice()
        tvDescription.text = p.description
    }

    private fun updateFavoriteUi(btnFavorite: ImageButton, product: Product) {
        val context = btnFavorite.context
        val isFav = ShopRepository.isFavorite(product.id)

        btnFavorite.isSelected = isFav
        btnFavorite.setImageResource(if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)

        val tint = if (isFav) {
            context.getColor(R.color.ocean_secondary)
        } else {
            context.getColor(R.color.ocean_muted_text)
        }
        btnFavorite.imageTintList = ColorStateList.valueOf(tint)

        btnFavorite.contentDescription = if (isFav) {
            context.getString(R.string.cd_remove_from_favorites)
        } else {
            context.getString(R.string.cd_add_to_favorites)
        }
    }
}
