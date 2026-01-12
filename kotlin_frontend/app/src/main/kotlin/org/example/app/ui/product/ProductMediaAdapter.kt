package org.example.app.ui.product

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.example.app.R

/**
 * Adapter used for the Product Details media gallery.
 *
 * This app does not download or render remote images (no image-loading library).
 * Instead, each entry is represented by a themed placeholder view.
 */
internal class ProductMediaAdapter(
    private val onItemVisible: (position: Int) -> Unit
) : RecyclerView.Adapter<ProductMediaAdapter.VH>() {

    private var items: List<String> = emptyList()

    fun submit(urls: List<String>) {
        items = urls
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_image, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(position = position, total = items.size)
        // Notify for the first bind; scroll events will update later.
        if (position == 0) onItemVisible(0)
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(position: Int, total: Int) {
            // Accessibility: announce "Image X of Y" even though visual is placeholder.
            itemView.contentDescription = itemView.context.getString(
                R.string.cd_gallery_image_position,
                (position + 1),
                total
            )
        }
    }
}
