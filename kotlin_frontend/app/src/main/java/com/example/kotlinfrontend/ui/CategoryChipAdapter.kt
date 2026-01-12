package com.example.kotlinfrontend.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.databinding.ItemCategoryChipBinding

/**
 * Adapter rendering category chips (including a leading icon).
 *
 * Selection state is kept externally and passed in via [submitCategories].
 */
class CategoryChipAdapter(
    private val onSelected: (String?) -> Unit
) : ListAdapter<CategoryChipAdapter.Item, CategoryChipAdapter.VH>(DIFF) {

    data class Item(
        val id: String,
        val label: String,
        val categoryValue: String?,
        val selected: Boolean
    )

    class VH(
        private val binding: ItemCategoryChipBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item, onSelected: (String?) -> Unit) {
            binding.chip.text = item.label
            binding.chip.isChecked = item.selected

            val iconRes = CategoryIconMapper.iconResForCategory(item.categoryValue)
            binding.chip.chipIcon = binding.chip.context.getDrawable(iconRes)
            binding.chip.isChipIconVisible = true
            binding.chip.chipIconSize = dpToPx(binding.chip, 18f)
            binding.chip.chipIconTint = binding.chip.context.getColorStateList(
                R.color.kf_chip_choice_icon_tint
            )

            // Accessibility:
            // Some Material versions do not expose chipIconContentDescription; use overall contentDescription.
            val categoryLabel = item.label.ifBlank { "Category" }
            binding.chip.contentDescription = "$categoryLabel category"

            binding.chip.setOnClickListener {
                onSelected(item.categoryValue)
            }
        }

        private fun dpToPx(view: android.view.View, dp: Float): Float {
            return dp * view.resources.displayMetrics.density
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCategoryChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), onSelected)
    }

    /**
     * PUBLIC_INTERFACE
     *
     * Create and submit the adapter list based on the incoming categories and current selection.
     */
    // PUBLIC_INTERFACE
    fun submitCategories(categories: List<String>, selected: String?) {
        val items = buildList {
            add(
                Item(
                    id = "all_grouped",
                    label = "All (grouped)",
                    categoryValue = null,
                    selected = selected == null
                )
            )
            categories.forEach { category ->
                add(
                    Item(
                        id = category,
                        label = category,
                        categoryValue = category,
                        selected = selected == category
                    )
                )
            }
        }
        submitList(items)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem == newItem
        }
    }
}
