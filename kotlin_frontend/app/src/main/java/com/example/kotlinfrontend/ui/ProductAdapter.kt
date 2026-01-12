package com.example.kotlinfrontend.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.databinding.ItemProductBinding
import com.example.kotlinfrontend.model.Product

class ProductAdapter :
    PagingDataAdapter<Product, ProductAdapter.ProductViewHolder>(DIFF) {

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

        fun bind(item: Product?) {
            if (item == null) return
            binding.title.text = item.title
            binding.description.text = item.description
            binding.price.text = item.priceText()
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
        holder.bind(item)

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
