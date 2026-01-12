package com.example.kotlinfrontend.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.databinding.ItemCartHeaderBinding
import com.example.kotlinfrontend.databinding.ItemCartRowBinding
import com.example.kotlinfrontend.model.CartItem

class CartAdapter(
    private val onIncrement: (CartItem) -> Unit,
    private val onDecrement: (CartItem) -> Unit,
    private val onRemove: (CartItem) -> Unit
) : ListAdapter<CartRow, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_ITEM = 2

        private val DIFF = object : DiffUtil.ItemCallback<CartRow>() {
            override fun areItemsTheSame(oldItem: CartRow, newItem: CartRow): Boolean {
                return when {
                    oldItem is CartRow.Header && newItem is CartRow.Header ->
                        oldItem.category == newItem.category

                    oldItem is CartRow.Item && newItem is CartRow.Item ->
                        oldItem.item.productId == newItem.item.productId

                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: CartRow, newItem: CartRow): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is CartRow.Header -> VIEW_TYPE_HEADER
            is CartRow.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderVH(ItemCartHeaderBinding.inflate(inflater, parent, false))
            else -> ItemVH(ItemCartRowBinding.inflate(inflater, parent, false), onIncrement, onDecrement, onRemove)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is CartRow.Header -> (holder as HeaderVH).bind(row)
            is CartRow.Item -> (holder as ItemVH).bind(row.item)
        }
    }

    class HeaderVH(private val binding: ItemCartHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: CartRow.Header) {
            binding.categoryTitle.text = row.category
        }
    }

    class ItemVH(
        private val binding: ItemCartRowBinding,
        private val onIncrement: (CartItem) -> Unit,
        private val onDecrement: (CartItem) -> Unit,
        private val onRemove: (CartItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartItem) {
            binding.title.text = item.name
            binding.unitPrice.text = "$" + String.format("%.2f", item.price)
            binding.qtyText.text = item.quantity.toString()
            binding.itemSubtotal.text = "$" + String.format("%.2f", item.subtotal())

            binding.incrementButton.setOnClickListener { onIncrement(item) }
            binding.decrementButton.setOnClickListener { onDecrement(item) }
            binding.removeButton.setOnClickListener { onRemove(item) }
        }
    }
}
