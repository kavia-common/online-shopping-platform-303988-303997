package com.example.kotlinfrontend.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.databinding.ItemProductBinding
import com.example.kotlinfrontend.model.Product

class CategoryPreviewAdapter :
    ListAdapter<Product, CategoryPreviewAdapter.ProductViewHolder>(DIFF) {

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

        fun bind(item: Product) {
            binding.title.text = item.title
            binding.description.text = item.description.orEmpty()
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

        holder.itemView.subtleAppear(
            durationMs = holder.itemView.context.resources.getInteger(
                com.example.kotlinfrontend.R.integer.anim_item_appear_duration_ms
            ).toLong()
        )
    }
}
