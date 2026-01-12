package com.example.kotlinfrontend.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.databinding.ItemSavedCouponBinding

class SavedCouponsAdapter(
    private val onSelect: (String) -> Unit,
    private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<SavedCouponsAdapter.VH>() {

    private var items: List<String> = emptyList()

    fun submitList(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSavedCouponBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val binding: ItemSavedCouponBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(code: String) {
            binding.couponCodeText.text = code
            binding.root.setOnClickListener { onSelect(code) }
            binding.removeSavedCouponButton.setOnClickListener { onRemove(code) }
        }
    }
}
