package org.example.app.ui.catalog

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.example.app.R
import org.example.app.data.Product
import org.example.app.data.ShopRepository

class ProductAdapter(
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<Product> = emptyList()
    private var repo: ShopRepository? = null

    private var showLoadingFooter: Boolean = false

    init {
        setHasStableIds(true)
    }

    /**
     * Replace the whole list (used when query/filters/sort change and paging resets).
     */
    fun submit(products: List<Product>, repository: ShopRepository) {
        submitInternal(products = products, repository = repository, loadingFooter = showLoadingFooter)
    }

    /**
     * Replace the whole list and optionally toggle the loading footer.
     */
    fun submitWithFooter(products: List<Product>, repository: ShopRepository, showLoadingFooter: Boolean) {
        submitInternal(products = products, repository = repository, loadingFooter = showLoadingFooter)
    }

    /**
     * Append items (used when fetching next page). This keeps DiffUtil animations smooth.
     */
    fun append(more: List<Product>, repository: ShopRepository) {
        if (more.isEmpty()) {
            repo = repository
            return
        }
        val combined = items + more
        submitInternal(products = combined, repository = repository, loadingFooter = showLoadingFooter)
    }

    /**
     * Show/hide the lightweight loading row at the bottom.
     */
    fun setLoadingFooterVisible(visible: Boolean) {
        if (showLoadingFooter == visible) return
        showLoadingFooter = visible
        // Footer-only change is simplest via notify; item list itself is unchanged.
        if (visible) {
            notifyItemInserted(itemCount)
        } else {
            // When hiding, footer was at last position.
            notifyItemRemoved(itemCount)
        }
    }

    private fun submitInternal(products: List<Product>, repository: ShopRepository, loadingFooter: Boolean) {
        val oldItems = items
        val oldFooter = showLoadingFooter

        repo = repository
        items = products
        showLoadingFooter = loadingFooter

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size + if (oldFooter) 1 else 0
            override fun getNewListSize(): Int = items.size + if (showLoadingFooter) 1 else 0

            private fun oldViewType(position: Int): Int {
                return if (oldFooter && position == oldItems.size) TYPE_LOADING else TYPE_PRODUCT
            }

            private fun newViewType(position: Int): Int {
                return if (showLoadingFooter && position == items.size) TYPE_LOADING else TYPE_PRODUCT
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldType = oldViewType(oldItemPosition)
                val newType = newViewType(newItemPosition)
                if (oldType != newType) return false
                if (oldType == TYPE_LOADING) return true

                val o = oldItems[oldItemPosition]
                val n = items[newItemPosition]
                return o.id == n.id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldType = oldViewType(oldItemPosition)
                val newType = newViewType(newItemPosition)
                if (oldType != newType) return false
                if (oldType == TYPE_LOADING) return true

                val o = oldItems[oldItemPosition]
                val n = items[newItemPosition]
                // Product is a data class; equals covers full content.
                return o == n
            }
        })

        diff.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long {
        val viewType = getItemViewType(position)
        return if (viewType == TYPE_LOADING) {
            LOADING_STABLE_ID
        } else {
            // Use a stable hash to help RecyclerView handle item moves during sorting.
            items[position].id.hashCode().toLong()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (showLoadingFooter && position == items.size) TYPE_LOADING else TYPE_PRODUCT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_LOADING -> {
                val v = inflater.inflate(R.layout.item_catalog_loading, parent, false)
                LoadingVH(v)
            }

            else -> {
                val v = inflater.inflate(R.layout.item_product, parent, false)
                ProductVH(v, onClick)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ProductVH -> {
                val product = items[position]
                holder.bind(product, repo)
            }

            is LoadingVH -> holder.bind()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (holder is ProductVH && payloads.contains(PAYLOAD_FAVORITE_CHANGED)) {
            val product = items[position]
            holder.bindFavorite(product, repo)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount(): Int = items.size + if (showLoadingFooter) 1 else 0

    // Called by CatalogFragment after a favorite toggle to refresh just that row.
    fun notifyFavoriteChanged(productId: String) {
        val idx = items.indexOfFirst { it.id == productId }
        if (idx >= 0) {
            notifyItemChanged(idx, PAYLOAD_FAVORITE_CHANGED)
        }
    }

    class ProductVH(
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

    class LoadingVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val progress: ProgressBar = itemView.findViewById(R.id.progress)

        fun bind() {
            // No-op; just keep visible.
            progress.visibility = View.VISIBLE
        }
    }

    private companion object {
        private const val PAYLOAD_FAVORITE_CHANGED = "payload_favorite_changed"

        private const val TYPE_PRODUCT = 1
        private const val TYPE_LOADING = 2

        private const val LOADING_STABLE_ID = Long.MIN_VALUE + 7L
    }
}
