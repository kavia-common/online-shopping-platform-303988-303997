package com.example.kotlinfrontend.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.databinding.ItemOrderBinding
import com.example.kotlinfrontend.model.Order
import com.example.kotlinfrontend.model.OrderStatus

class OrdersAdapter(
    private val onPay: (orderId: String) -> Unit,
    private val onShip: (orderId: String) -> Unit,
    private val onDeliver: (orderId: String) -> Unit,
    private val onCancel: (orderId: String) -> Unit
) : PagingDataAdapter<Order, OrdersAdapter.OrderViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Order>() {
            override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean = oldItem == newItem
        }
    }

    class OrderViewHolder(
        private val binding: ItemOrderBinding,
        private val onPay: (orderId: String) -> Unit,
        private val onShip: (orderId: String) -> Unit,
        private val onDeliver: (orderId: String) -> Unit,
        private val onCancel: (orderId: String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Order?) {
            if (item == null) return

            binding.orderId.text = "Order #${item.id}"
            binding.orderMeta.text = buildString {
                if (!item.email.isNullOrBlank()) append(item.email)
                if (!item.createdAt.isNullOrBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(item.createdAt)
                }
            }.ifBlank { "—" }

            binding.statusChip.text = item.statusLabel()

            // Minimal color cue via existing theme colors (no new resources).
            val statusColor = when (item.status) {
                OrderStatus.CREATED -> R.color.ocean_primary
                OrderStatus.PAID -> R.color.ocean_secondary
                OrderStatus.SHIPPED -> R.color.ocean_primary
                OrderStatus.DELIVERED -> R.color.ocean_secondary
                OrderStatus.CANCELLED -> R.color.ocean_error
                OrderStatus.UNKNOWN -> R.color.ocean_text_muted
            }
            binding.statusChip.setTextColor(itemView.context.getColor(statusColor))

            val actionsEnabled = item.id != "unknown"

            // Show a minimal set of transition buttons; hide those that don't make sense.
            binding.payButton.isVisible = item.status == OrderStatus.CREATED
            binding.shipButton.isVisible = item.status == OrderStatus.PAID
            binding.deliverButton.isVisible = item.status == OrderStatus.SHIPPED
            binding.cancelButton.isVisible = item.status == OrderStatus.CREATED || item.status == OrderStatus.PAID

            binding.payButton.isEnabled = actionsEnabled
            binding.shipButton.isEnabled = actionsEnabled
            binding.deliverButton.isEnabled = actionsEnabled
            binding.cancelButton.isEnabled = actionsEnabled

            binding.payButton.setOnClickListener { onPay(item.id) }
            binding.shipButton.setOnClickListener { onShip(item.id) }
            binding.deliverButton.setOnClickListener { onDeliver(item.id) }
            binding.cancelButton.setOnClickListener { onCancel(item.id) }

            // Simple totals (if backend provides).
            binding.totalAmount.text = item.totalAmount?.let { "$" + String.format("%.2f", it) } ?: "—"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding, onPay, onShip, onDeliver, onCancel)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)

        if (item != null) {
            holder.itemView.subtleAppear(
                durationMs = holder.itemView.context.resources.getInteger(
                    com.example.kotlinfrontend.R.integer.anim_item_appear_duration_ms
                ).toLong()
            )
        }
    }
}
