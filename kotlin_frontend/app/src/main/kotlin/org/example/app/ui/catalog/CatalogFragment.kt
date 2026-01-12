package org.example.app.ui.catalog

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.example.app.R
import org.example.app.data.ShopRepository

class CatalogFragment : Fragment(R.layout.fragment_catalog) {

    private var selectedCategoryId: String? = null
    private var searchQuery: String = ""

    private lateinit var rvProducts: RecyclerView
    private lateinit var adapter: ProductAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvProducts = view.findViewById(R.id.rv_products)
        rvProducts.layoutManager = LinearLayoutManager(requireContext())

        adapter = ProductAdapter { product ->
            findNavController().navigate(
                R.id.action_catalog_to_productDetails,
                bundleOf("productId" to product.id)
            )
        }
        rvProducts.adapter = adapter

        // Sidebar category filters
        val categoryContainer = view.findViewById<LinearLayout>(R.id.category_container)
        renderCategoryFilters(categoryContainer)

        view.findViewById<MaterialButton>(R.id.btn_go_to_cart).setOnClickListener {
            findNavController().navigate(R.id.action_catalog_to_cart)
        }

        val etSearch = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_search)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim().orEmpty()
                refreshProducts()
            }
        })

        refreshProducts()
    }

    private fun renderCategoryFilters(container: LinearLayout) {
        container.removeAllViews()

        fun addChip(label: String, categoryId: String?) {
            val tv = TextView(requireContext())
            tv.text = label
            tv.setPadding(12)
            tv.background = requireContext().getDrawable(R.drawable.bg_chip_outline)
            tv.setTextColor(requireContext().getColor(R.color.ocean_text))
            tv.textSize = 14f

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 8
            tv.layoutParams = lp

            tv.setOnClickListener {
                selectedCategoryId = categoryId
                refreshProducts()
            }

            container.addView(tv)
        }

        addChip("All", null)
        ShopRepository.getCategories().forEach { c ->
            addChip(c.name, c.id)
        }
    }

    private fun refreshProducts() {
        val items = ShopRepository.getAllProducts()
            .asSequence()
            .filter { selectedCategoryId == null || it.categoryId == selectedCategoryId }
            .filter {
                if (searchQuery.isBlank()) true
                else it.name.contains(searchQuery, ignoreCase = true)
            }
            .toList()

        adapter.submit(items, ShopRepository)
    }
}
