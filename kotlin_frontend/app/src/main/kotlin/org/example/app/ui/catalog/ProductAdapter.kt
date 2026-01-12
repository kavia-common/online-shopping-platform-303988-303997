package org.example.app.ui.catalog

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_FAVORITE_CHANGED)) {
            val product = items[position]
            holder.bindFavorite(product, repo)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount(): Int = items.size

    // Called by CatalogFragment after a favorite toggle to refresh just that row.
    fun notifyFavoriteChanged(productId: String) {
        val idx = items.indexOfFirst { it.id == productId }
        if (idx >= 0) {
            notifyItemChanged(idx, PAYLOAD_FAVORITE_CHANGED)
        }
    }

    class VH(
        itemView: View,
        private val onClick: (Product) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvMeta: TextView = itemView.findViewById(R.id.tv_meta)
        private val tvPrice: TextView = itemView.findViewById(R.id.tv_price)
        private val btnFavorite: ImageButton = itemView.findViewById(R.id.btn_favorite)

        fun bind(product: Product, repo: ShopRepository?) {
            tvName.text = product.name
            tvMeta.text = repo?.getCategoryName(product.categoryId) ?: ""
            tvPrice.text = product.formattedPrice()

            // Keep row click behavior unchanged.
            itemView.setOnClickListener { onClick(product) }

            bindFavorite(product, repo)

            // Favorite toggle: do not propagate click to row.
            btnFavorite.setOnClickListener {
                repo?.toggleFavorite(product.id)
                // We don't call notify here because adapter does not own repo observation.
                // CatalogFragment observes favorites and will refresh this item only.
            }
        }

        fun bindFavorite(product: Product, repo: ShopRepository?) {
            val context = itemView.context
            val isFav = repo?.isFavorite(product.id) == true

            btnFavorite.isSelected = isFav
            btnFavorite.setImageResource(if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)

            // Ocean Professional accent: use secondary (amber) for active; muted text for inactive.
            val tint = if (isFav) {
                context.getColor(R.color.ocean_secondary)
            } else {
                context.getColor(R.color.ocean_muted_text)
            }
            btnFavorite.imageTintList = ColorStateList.valueOf(tint)

            btnFavorite.contentDescription = if (isFav) {
                context.getString(R.string.cd_remove_from_favorites)
            } else {
                context.getString(R.string.cd_add_to_favorites)
            }
        }
    }

    private companion object {
        private const val PAYLOAD_FAVORITE_CHANGED = "payload_favorite_changed"
    }
}
