package org.example.app.ui.product

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.example.app.R
import org.example.app.data.Product
import org.example.app.data.ShopRepository
import org.example.app.ui.util.SaleCountdownFormatter

class ProductDetailsFragment : Fragment(R.layout.fragment_product_details) {

    private var productId: String? = null

    private var countdownJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        productId = arguments?.getString("productId")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvGallery = view.findViewById<RecyclerView>(R.id.rv_gallery)
        val galleryDots = view.findViewById<LinearLayout>(R.id.gallery_dots)
        val dot1 = view.findViewById<View>(R.id.dot_1)
        val dot2 = view.findViewById<View>(R.id.dot_2)
        val dot3 = view.findViewById<View>(R.id.dot_3)

        val tvName = view.findViewById<TextView>(R.id.tv_name)
        val tvCategory = view.findViewById<TextView>(R.id.tv_category)

        val tvSaleBadge = view.findViewById<TextView>(R.id.tv_sale_badge)
        val tvSalePrice = view.findViewById<TextView>(R.id.tv_sale_price)
        val tvPrice = view.findViewById<TextView>(R.id.tv_price)
        val tvOriginalPrice = view.findViewById<TextView>(R.id.tv_original_price)
        val tvSaleCountdown = view.findViewById<TextView>(R.id.tv_sale_countdown)

        val tvDescription = view.findViewById<TextView>(R.id.tv_description)
        val tvSpecsTitle = view.findViewById<TextView>(R.id.tv_specs_title)
        val tvSpecs = view.findViewById<TextView>(R.id.tv_specs)

        val btnFavorite = view.findViewById<ImageButton>(R.id.btn_favorite)

        val btnAddToCart = view.findViewById<MaterialButton>(R.id.btn_add_to_cart)
        val btnGoToCart = view.findViewById<MaterialButton>(R.id.btn_go_to_cart)

        // Gallery setup (horizontal, snap-like feel without extra deps).
        val galleryAdapter = ProductMediaAdapter { pos ->
            updateDots(
                dotsContainer = galleryDots,
                dots = listOf(dot1, dot2, dot3),
                selectedIndex = pos
            )
        }
        val lm = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvGallery.layoutManager = lm
        rvGallery.adapter = galleryAdapter

        rvGallery.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val first = lm.findFirstVisibleItemPosition()
                if (first >= 0) {
                    updateDots(
                        dotsContainer = galleryDots,
                        dots = listOf(dot1, dot2, dot3),
                        selectedIndex = first
                    )
                }
            }
        })

        val p = productId?.let { ShopRepository.findProductById(it) }
        if (p == null) {
            tvName.text = getString(R.string.product_not_found)
            tvCategory.text = ""
            tvPrice.text = ""
            tvDescription.text = ""
            tvSpecsTitle.visibility = View.GONE
            tvSpecs.visibility = View.GONE
            btnAddToCart.isEnabled = false
            btnFavorite.isEnabled = false
            tvSaleBadge.visibility = View.GONE
            tvSalePrice.visibility = View.GONE
            tvSaleCountdown.visibility = View.GONE

            // Gallery placeholder: show a single "image" even when product is missing.
            galleryAdapter.submit(listOf("mock://missing/1"))
            galleryDots.visibility = View.GONE
        } else {
            bindProduct(
                p = p,
                tvName = tvName,
                tvCategory = tvCategory,
                tvSaleBadge = tvSaleBadge,
                tvSalePrice = tvSalePrice,
                tvPrice = tvPrice,
                tvOriginalPrice = tvOriginalPrice,
                tvSaleCountdown = tvSaleCountdown,
                tvDescription = tvDescription,
                tvSpecsTitle = tvSpecsTitle,
                tvSpecs = tvSpecs,
                galleryAdapter = galleryAdapter,
                galleryDots = galleryDots,
                dots = listOf(dot1, dot2, dot3)
            )

            btnAddToCart.setOnClickListener {
                ShopRepository.addToCart(p.id, 1)
            }

            btnFavorite.setOnClickListener {
                ShopRepository.toggleFavorite(p.id)
                // UI will update via favorites observer below.
            }

            // Keep favorite icon in sync if changed elsewhere (e.g., from Catalog).
            ShopRepository.observeFavorites().observe(viewLifecycleOwner) {
                updateFavoriteUi(btnFavorite, p)
            }
            // Initial state
            updateFavoriteUi(btnFavorite, p)

            // Start/stop countdown updates automatically with view lifecycle.
            startCountdownIfNeeded(p, tvSaleCountdown)
        }

        btnGoToCart.setOnClickListener {
            findNavController().navigate(R.id.action_productDetails_to_cart)
        }
    }

    override fun onPause() {
        super.onPause()
        // Ensure countdown doesn't keep running while screen is not active.
        stopCountdown()
    }

    override fun onDestroyView() {
        stopCountdown()
        super.onDestroyView()
    }

    private fun bindProduct(
        p: Product,
        tvName: TextView,
        tvCategory: TextView,
        tvSaleBadge: TextView,
        tvSalePrice: TextView,
        tvPrice: TextView,
        tvOriginalPrice: TextView,
        tvSaleCountdown: TextView,
        tvDescription: TextView,
        tvSpecsTitle: TextView,
        tvSpecs: TextView,
        galleryAdapter: ProductMediaAdapter,
        galleryDots: LinearLayout,
        dots: List<View>
    ) {
        val context = tvName.context

        tvName.text = p.name
        tvCategory.text = ShopRepository.getCategoryName(p.categoryId)

        // Gallery: if empty, show a single placeholder so layout remains stable.
        val urls = if (p.imageUrls.isNotEmpty()) p.imageUrls else listOf("mock://placeholder/1")
        galleryAdapter.submit(urls)

        // Dots: show only if multiple images exist.
        val showDots = p.hasMultipleImages()
        galleryDots.visibility = if (showDots) View.VISIBLE else View.GONE
        if (showDots) {
            galleryDots.contentDescription = context.getString(R.string.cd_multiple_images, p.imageUrls.size)
            updateDots(galleryDots, dots, selectedIndex = 0)
        }

        // Sale UI
        val saleCents = p.effectiveSalePriceCents()
        val isOnSale = p.isOnSale && saleCents != null

        if (isOnSale) {
            val pct = p.effectiveDiscountPercent()
            tvSaleBadge.text = if (pct != null) {
                context.getString(R.string.sale_badge_percent, pct)
            } else {
                context.getString(R.string.sale_badge)
            }
            tvSaleBadge.visibility = View.VISIBLE

            tvSalePrice.visibility = View.VISIBLE
            tvSalePrice.text = p.formattedPriceFromCents(saleCents!!)

            tvPrice.text = p.formattedPriceFromCents(p.priceCents)
            tvPrice.paintFlags = tvPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            tvPrice.setTextColor(context.getColor(R.color.ocean_muted_text))

            tvOriginalPrice.visibility = View.GONE

            // Subtle animations for badge + sale price (first render only), respects reduced motion.
            if (!isReducedMotionEnabled(context)) {
                tvSaleBadge.alpha = 0f
                tvSaleBadge.scaleX = 0.92f
                tvSaleBadge.scaleY = 0.92f
                tvSaleBadge.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160L)
                    .start()

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
                tvSaleBadge.alpha = 1f
                tvSaleBadge.scaleX = 1f
                tvSaleBadge.scaleY = 1f
                tvSalePrice.scaleX = 1f
                tvSalePrice.scaleY = 1f
            }

            // Accessibility: announce sale semantics clearly.
            tvPrice.contentDescription = context.getString(
                R.string.cd_price_on_sale,
                tvSalePrice.text,
                tvPrice.text
            )
        } else {
            tvSaleBadge.visibility = View.GONE
            tvSalePrice.visibility = View.GONE
            tvSaleCountdown.visibility = View.GONE

            tvPrice.text = p.formattedPrice()
            tvPrice.paintFlags = tvPrice.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            tvPrice.setTextColor(context.getColor(R.color.ocean_price))

            tvOriginalPrice.visibility = View.GONE

            tvPrice.contentDescription = context.getString(R.string.cd_price_regular, tvPrice.text)
        }

        // Rich description: long description fallback to short.
        tvDescription.text = p.effectiveLongDescription()

        // Bullet points/specs
        val bullets = p.bulletPoints.filter { it.isNotBlank() }
        if (bullets.isEmpty()) {
            tvSpecsTitle.visibility = View.GONE
            tvSpecs.visibility = View.GONE
        } else {
            tvSpecsTitle.visibility = View.VISIBLE
            tvSpecs.visibility = View.VISIBLE
            tvSpecs.text = bullets.joinToString(separator = "\n") { "• $it" }
            tvSpecs.contentDescription = context.getString(R.string.cd_product_specs)
        }
    }

    private fun startCountdownIfNeeded(product: Product, tvSaleCountdown: TextView) {
        stopCountdown()

        if (!product.hasSaleCountdown()) {
            tvSaleCountdown.visibility = View.GONE
            tvSaleCountdown.text = ""
            tvSaleCountdown.contentDescription = null
            return
        }

        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val remaining = product.remainingSaleMillis() ?: 0L
                if (remaining <= 0L) {
                    tvSaleCountdown.visibility = View.GONE
                    tvSaleCountdown.text = ""
                    tvSaleCountdown.contentDescription = null
                    break
                }

                val formatted = SaleCountdownFormatter.formatRemaining(remaining)
                tvSaleCountdown.visibility = View.VISIBLE
                tvSaleCountdown.text = tvSaleCountdown.context.getString(R.string.sale_ends_in, formatted)
                tvSaleCountdown.contentDescription = tvSaleCountdown.context.getString(R.string.cd_sale_ends_in, formatted)

                // Coalesced coroutine timer; lifecycleScope cancels on destroy, and we also stop onPause.
                delay(1000L)
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun isReducedMotionEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am?.isEnabled == true && am.isTouchExplorationEnabled) return true

        return try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Throwable) {
            false
        }
    }

    private fun updateDots(dotsContainer: LinearLayout, dots: List<View>, selectedIndex: Int) {
        // We support up to 3 dots for this demo UI; show count hints via contentDescription.
        val max = dots.size.coerceAtMost(3)
        for (i in 0 until max) {
            val dot = dots[i]
            dot.background = dot.context.getDrawable(
                if (i == selectedIndex.coerceIn(0, max - 1)) R.drawable.bg_pager_dot_selected
                else R.drawable.bg_pager_dot
            )
        }
        dotsContainer.contentDescription = dotsContainer.context.getString(R.string.cd_gallery_dots)
    }

    private fun updateFavoriteUi(btnFavorite: ImageButton, product: Product) {
        val context = btnFavorite.context
        val isFav = ShopRepository.isFavorite(product.id)

        btnFavorite.isSelected = isFav
        btnFavorite.setImageResource(if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)

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
