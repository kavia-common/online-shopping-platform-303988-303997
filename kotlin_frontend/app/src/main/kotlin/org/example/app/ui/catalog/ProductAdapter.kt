package org.example.app.ui.catalog

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.example.app.R
import org.example.app.data.Product
import org.example.app.data.ShopRepository
import org.example.app.ui.util.SaleCountdownFormatter

class ProductAdapter(
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<Product> = emptyList()
    private var repo: ShopRepository? = null

    private var showLoadingFooter: Boolean = false

    // Shared, lifecycle-aware ticker for countdowns in visible rows.
    // We start it when RecyclerView attaches and stop when it detaches.
    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

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
        if (holder is ProductVH) {
            if (payloads.contains(PAYLOAD_FAVORITE_CHANGED)) {
                val product = items[position]
                holder.bindFavorite(product, repo)
                return
            }
            if (payloads.contains(PAYLOAD_COUNTDOWN_TICK)) {
                // Only update countdown text to keep per-second work minimal.
                if (position < items.size) {
                    val product = items[position]
                    holder.bindCountdownTick(product)
                }
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        startCountdownTicker()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        stopCountdownTicker()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getItemCount(): Int = items.size + if (showLoadingFooter) 1 else 0

    // Called by CatalogFragment after a favorite toggle to refresh just that row.
    fun notifyFavoriteChanged(productId: String) {
        val idx = items.indexOfFirst { it.id == productId }
        if (idx >= 0) {
            notifyItemChanged(idx, PAYLOAD_FAVORITE_CHANGED)
        }
    }

    private fun startCountdownTicker() {
        if (countdownRunnable != null) return
        val r = object : Runnable {
            override fun run() {
                // Update only visible product rows (skip footer).
                // notifyItemRangeChanged with payload lets VH update only countdown text.
                if (items.isNotEmpty()) {
                    notifyItemRangeChanged(0, items.size, PAYLOAD_COUNTDOWN_TICK)
                }
                countdownHandler.postDelayed(this, 1000L)
            }
        }
        countdownRunnable = r
        countdownHandler.post(r)
    }

    private fun stopCountdownTicker() {
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        countdownRunnable = null
    }

    private fun isReducedMotionEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        // Prefer Android's accessibility hint when available.
        if (am?.isEnabled == true && am.isTouchExplorationEnabled) return true

        // Also respect the "remove animations" global setting.
        return try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Throwable) {
            false
        }
    }

    class ProductVH(
        itemView: View,
        private val onClick: (Product) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvMeta: TextView = itemView.findViewById(R.id.tv_meta)

        private val tvSaleBadge: TextView = itemView.findViewById(R.id.tv_sale_badge)
        private val tvSaleCountdown: TextView = itemView.findViewById(R.id.tv_sale_countdown)
        private val tvSalePrice: TextView = itemView.findViewById(R.id.tv_sale_price)
        private val tvPrice: TextView = itemView.findViewById(R.id.tv_price)
        private val tvOriginalPrice: TextView = itemView.findViewById(R.id.tv_original_price)

        private val imageDots: LinearLayout = itemView.findViewById(R.id.image_dots)

        private val btnFavorite: ImageButton = itemView.findViewById(R.id.btn_favorite)

        private var hasAnimatedSaleBadge: Boolean = false
        private var hasAnimatedSalePrice: Boolean = false

        private fun isReducedMotionEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am?.isEnabled == true && am.isTouchExplorationEnabled) return true

            return try {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            } catch (_: Throwable) {
                false
            }
        }

        fun bind(product: Product, repo: ShopRepository?) {
            tvName.text = product.name
            tvMeta.text = repo?.getCategoryName(product.categoryId) ?: ""

            bindSaleUi(product, animateOnFirstBind = true)

            // Multi-image hint: we do not load images; this is a subtle indicator that more media exists.
            val showDots = product.hasMultipleImages()
            imageDots.visibility = if (showDots) View.VISIBLE else View.GONE
            imageDots.contentDescription = if (showDots) {
                itemView.context.getString(R.string.cd_multiple_images, product.imageUrls.size)
            } else {
                null
            }

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

        fun bindCountdownTick(product: Product) {
            // No animations on tick updates.
            bindSaleCountdownOnly(product)
        }

        private fun bindSaleUi(product: Product, animateOnFirstBind: Boolean) {
            val context = itemView.context

            val saleCents = product.effectiveSalePriceCents()
            val isOnSale = product.isOnSale && saleCents != null

            // Badge
            if (isOnSale) {
                val pct = product.effectiveDiscountPercent()
                tvSaleBadge.text = if (pct != null) {
                    context.getString(R.string.sale_badge_percent, pct)
                } else {
                    context.getString(R.string.sale_badge)
                }
                tvSaleBadge.visibility = View.VISIBLE

                // Subtle badge appearance animation (fade+scale), respects reduced motion.
                if (animateOnFirstBind && !hasAnimatedSaleBadge && !isReducedMotionEnabled(context)) {
                    hasAnimatedSaleBadge = true
                    tvSaleBadge.alpha = 0f
                    tvSaleBadge.scaleX = 0.92f
                    tvSaleBadge.scaleY = 0.92f
                    tvSaleBadge.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(160L)
                        .start()
                } else {
                    tvSaleBadge.alpha = 1f
                    tvSaleBadge.scaleX = 1f
                    tvSaleBadge.scaleY = 1f
                }
            } else {
                tvSaleBadge.visibility = View.GONE
                tvSaleCountdown.visibility = View.GONE
                hasAnimatedSaleBadge = false
                hasAnimatedSalePrice = false
            }

            // Prices
            if (isOnSale) {
                tvSalePrice.visibility = View.VISIBLE
                tvSalePrice.text = product.formattedPriceFromCents(saleCents!!)

                tvPrice.text = product.formattedPriceFromCents(product.priceCents)
                tvPrice.paintFlags = tvPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvPrice.setTextColor(context.getColor(R.color.ocean_muted_text))

                tvOriginalPrice.visibility = View.GONE

                // Small pulse emphasis for sale price on first render, respects reduced motion.
                if (animateOnFirstBind && !hasAnimatedSalePrice && !isReducedMotionEnabled(context)) {
                    hasAnimatedSalePrice = true
                    tvSalePrice.scaleX = 1f
                    tvSalePrice.scaleY = 1f
                    tvSalePrice.animate()
                        .scaleX(1.04f)
                        .scaleY(1.04f)
                        .setDuration(140L)
                        .withEndAction {
                            tvSalePrice.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
                        }
                        .start()
                } else {
                    tvSalePrice.scaleX = 1f
                    tvSalePrice.scaleY = 1f
                }
            } else {
                tvSalePrice.visibility = View.GONE

                tvPrice.text = product.formattedPrice()
                tvPrice.paintFlags = tvPrice.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvPrice.setTextColor(context.getColor(R.color.ocean_price))

                tvOriginalPrice.visibility = View.GONE
            }

            bindSaleCountdownOnly(product)

            // Accessibility: make sure screen readers read the effective price meaningfully.
            tvPrice.contentDescription = if (isOnSale) {
                context.getString(
                    R.string.cd_price_on_sale,
                    tvSalePrice.text,
                    tvPrice.text
                )
            } else {
                context.getString(R.string.cd_price_regular, tvPrice.text)
            }
        }

        private fun bindSaleCountdownOnly(product: Product) {
            val context = itemView.context
            val remaining = product.remainingSaleMillis()
            val showCountdown = remaining != null && remaining > 0L

            if (showCountdown) {
                // Compact countdown in catalog; keep it subtle to avoid layout jumps.
                tvSaleCountdown.visibility = View.VISIBLE
                tvSaleCountdown.text = context.getString(
                    R.string.sale_ends_in_compact,
                    SaleCountdownFormatter.formatRemaining(remaining)
                )
                tvSaleCountdown.contentDescription = context.getString(
                    R.string.cd_sale_ends_in,
                    SaleCountdownFormatter.formatRemaining(remaining)
                )
            } else {
                tvSaleCountdown.visibility = View.GONE
                tvSaleCountdown.text = ""
                tvSaleCountdown.contentDescription = null
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
        private const val PAYLOAD_COUNTDOWN_TICK = "payload_countdown_tick"

        private const val TYPE_PRODUCT = 1
        private const val TYPE_LOADING = 2

        private const val LOADING_STABLE_ID = Long.MIN_VALUE + 7L
    }
}
