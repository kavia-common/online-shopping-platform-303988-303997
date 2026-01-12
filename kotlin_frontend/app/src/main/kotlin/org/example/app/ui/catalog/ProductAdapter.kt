package org.example.app.ui.catalog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.example.app.R
import org.example.app.data.Product
import org.example.app.data.ShopRepository

class ProductAdapter(
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    private var items: List<Product> = emptyList()
    private var repo: ShopRepository? = null

    init {
        setHasStableIds(true)
    }

    fun submit(products: List<Product>, repository: ShopRepository) {
        val oldItems = items
        val newItems = products

        repo = repository
        items = newItems

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition].id == newItems[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val o = oldItems[oldItemPosition]
                val n = newItems[newItemPosition]
                // Product is a data class; equals covers full content.
                return o == n
            }
        })

        diff.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long {
        // Use a stable hash to help RecyclerView handle item moves during sorting.
        return items[position].id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val product = items[position]
        holder.bind(product, repo)
    }

    override fun getItemCount(): Int = items.size

    class VH(
        itemView: View,
        private val onClick: (Product) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvMeta: TextView = itemView.findViewById(R.id.tv_meta)
        private val tvPrice: TextView = itemView.findViewById(R.id.tv_price)

        fun bind(product: Product, repo: ShopRepository?) {
            tvName.text = product.name
            tvMeta.text = repo?.getCategoryName(product.categoryId) ?: ""
            tvPrice.text = product.formattedPrice()
            itemView.setOnClickListener { onClick(product) }
        }
    }
}
