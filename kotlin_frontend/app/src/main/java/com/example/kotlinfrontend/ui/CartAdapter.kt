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

    init {
        // Enables more consistent RecyclerView animations and better interaction with ItemTouchHelper.
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        // A simple stable hash is sufficient here; productId/category are stable identifiers.
        return when (val row = getItem(position)) {
            is CartRow.Header -> ("header:${row.category}").hashCode().toLong()
            is CartRow.Item -> ("item:${row.item.productId}").hashCode().toLong()
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
            is CartRow.Header -> (holder as HeaderVH).bind(row, shouldHideHeaderAt(position))
            is CartRow.Item -> (holder as ItemVH).bind(row.item)
        }
    }

    /**
     * Auto-hide a header if it would be followed by another header or end-of-list.
     * This keeps section headers consistent when groups become empty after deletions.
     */
    private fun shouldHideHeaderAt(position: Int): Boolean {
        if (position < 0 || position >= itemCount) return true
        val current = getItem(position)
        if (current !is CartRow.Header) return true

        val nextPos = position + 1
        if (nextPos >= itemCount) return true
        return getItem(nextPos) is CartRow.Header
    }

    /**
     * Returns the adapter position for the cart item (CartRow.Item) with the given productId.
     * Used by ItemTouchHelper to map swipes to an item.
     */
    fun findAdapterPositionForProduct(productId: String): Int {
        val current = currentList
        for (i in current.indices) {
            val row = current[i]
            if (row is CartRow.Item && row.item.productId == productId) return i
        }
        return RecyclerView.NO_POSITION
    }

    /**
     * Returns the CartItem at adapter position if the row is an item row.
     */
    fun getCartItemAt(adapterPosition: Int): CartItem? {
        if (adapterPosition < 0 || adapterPosition >= itemCount) return null
        val row = getItem(adapterPosition)
        return (row as? CartRow.Item)?.item
    }

    class HeaderVH(private val binding: ItemCartHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: CartRow.Header, hide: Boolean) {
            binding.root.visibility = if (hide) android.view.View.GONE else android.view.View.VISIBLE
            binding.categoryTitle.text = row.category
        }
    }

    class ItemVH(
        private val binding: ItemCartRowBinding,
        private val onIncrement: (CartItem) -> Unit,
        private val onDecrement: (CartItem) -> Unit,
        private val onRemove: (CartItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var lastBoundProductId: String? = null

        fun bind(item: CartItem) {
            binding.title.text = item.name
            binding.unitPrice.text = "$" + String.format("%.2f", item.price)
            binding.qtyText.text = item.quantity.toString()
            binding.itemSubtotal.text = "$" + String.format("%.2f", item.subtotal())

            // Accessibility: provide richer descriptions that include item name.
            binding.incrementButton.contentDescription = "Increase quantity for ${item.name}"
            binding.decrementButton.contentDescription = "Decrease quantity for ${item.name}"
            binding.removeButton.contentDescription = "Remove ${item.name} from cart"

            binding.incrementButton.setOnClickListener { onIncrement(item) }
            binding.decrementButton.setOnClickListener { onDecrement(item) }
            binding.removeButton.setOnClickListener { onRemove(item) }

            // Subtle per-item appear animation (avoid replaying on every rebind for same item).
            if (lastBoundProductId != item.productId) {
                lastBoundProductId = item.productId
                val duration = itemView.context.animDuration(com.example.kotlinfrontend.R.integer.anim_item_appear_duration_ms)
                itemView.subtleAppear(duration)
            }
        }
    }
}
