package com.example.kotlinfrontend.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kotlinfrontend.databinding.ViewCategorySectionBinding
import com.example.kotlinfrontend.model.Product

class CategorySectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ViewCategorySectionBinding.inflate(LayoutInflater.from(context), this, true)

    private var adapter: CategoryPreviewAdapter? = null
    private var onSeeAll: (() -> Unit)? = null

    init {
        binding.productsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.seeAllButton.setOnClickListener { onSeeAll?.invoke() }
    }

    fun setCartCallbacks(
        cartQtyProvider: ((productId: String) -> Int)?,
        onAddToCart: ((product: Product, qty: Int) -> Unit)?,
        onIncrementInCart: ((product: Product) -> Unit)?,
        onDecrementInCart: ((product: Product) -> Unit)?
    ) {
        val newAdapter = CategoryPreviewAdapter(
            cartQtyProvider = cartQtyProvider,
            onAddToCart = onAddToCart,
            onIncrementInCart = onIncrementInCart,
            onDecrementInCart = onDecrementInCart
        )
        adapter = newAdapter
        binding.productsRecyclerView.adapter = newAdapter
    }

    fun bindHeader(category: String, onSeeAllClick: () -> Unit) {
        binding.categoryTitle.text = category

        // Icon is optional via mapping; unknown categories fall back to generic.
        val iconRes = CategoryIconMapper.iconResForCategory(category)
        binding.categoryIcon.setImageResource(iconRes)
        binding.categoryIcon.contentDescription = CategoryIconMapper.contentDescriptionForCategory(category)

        onSeeAll = onSeeAllClick
    }

    fun showLoading(loading: Boolean) {
        binding.sectionProgress.isVisible = loading
    }

    fun showError(message: String?, visible: Boolean) {
        binding.sectionErrorText.isVisible = visible
        binding.sectionErrorText.text = message ?: "Failed to load"
    }

    fun submitItems(items: List<Product>) {
        adapter?.submitList(items)
        binding.emptyHint.isVisible = items.isEmpty()
    }
}
