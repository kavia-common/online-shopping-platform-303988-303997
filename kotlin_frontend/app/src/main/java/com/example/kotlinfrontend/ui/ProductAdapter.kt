package com.example.kotlinfrontend.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.databinding.ItemProductBinding
import com.example.kotlinfrontend.model.Product

class ProductAdapter(
    private val cartQtyProvider: ((productId: String) -> Int)? = null,
    private var onAddToCart: ((product: Product, qty: Int) -> Unit)? = null,
    private var onIncrementInCart: ((product: Product) -> Unit)? = null,
    private var onDecrementInCart: ((product: Product) -> Unit)? = null
) :
    PagingDataAdapter<Product, ProductAdapter.ProductViewHolder>(DIFF) {

    // PUBLIC_INTERFACE
    fun setCartCallbacks(
        onAddToCart: ((product: Product, qty: Int) -> Unit)?,
        onIncrementInCart: ((product: Product) -> Unit)?,
        onDecrementInCart: ((product: Product) -> Unit)?
    ) {
        /** Update cart callbacks (useful when the adapter instance needs to reference itself via apply {}). */
        this.onAddToCart = onAddToCart
        this.onIncrementInCart = onIncrementInCart
        this.onDecrementInCart = onDecrementInCart
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean =
                oldItem == newItem
        }
    }

    class ProductViewHolder(
        private val binding: ItemProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: Product?,
            cartQtyProvider: ((productId: String) -> Int)?,
            onAddToCart: ((product: Product, qty: Int) -> Unit)?,
            onIncrementInCart: ((product: Product) -> Unit)?,
            onDecrementInCart: ((product: Product) -> Unit)?
        ) {
            if (item == null) return

            binding.title.text = item.title
            binding.description.text = item.description.orEmpty()
            binding.price.text = item.priceText()

            val currentQty = cartQtyProvider?.invoke(item.id) ?: 0
            binding.qtyText.text = currentQty.toString()

            binding.addToCartButton.setOnClickListener {
                onAddToCart?.invoke(item, 1)
            }
            binding.incrementButton.setOnClickListener {
                onIncrementInCart?.invoke(item)
            }
            binding.decrementButton.setOnClickListener {
                onDecrementInCart?.invoke(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(
            item = item,
            cartQtyProvider = cartQtyProvider,
            onAddToCart = onAddToCart,
            onIncrementInCart = onIncrementInCart,
            onDecrementInCart = onDecrementInCart
        )

        // Subtle appear for newly bound items (kept lightweight; avoids heavy animators).
        if (item != null) {
            holder.itemView.subtleAppear(
                durationMs = holder.itemView.context.resources.getInteger(
                    com.example.kotlinfrontend.R.integer.anim_item_appear_duration_ms
                ).toLong()
            )
        }
    }
}
