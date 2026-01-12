package org.example.app.ui.cart

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.example.app.R
import org.example.app.data.ShopRepository

class CartAdapter : RecyclerView.Adapter<CartAdapter.VH>() {

    private var items: List<ShopRepository.CartItem> = emptyList()

    fun submit(newItems: List<ShopRepository.CartItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvQty: TextView = itemView.findViewById(R.id.tv_qty)
        private val tvLinePrice: TextView = itemView.findViewById(R.id.tv_line_price)

        fun bind(item: ShopRepository.CartItem) {
            tvName.text = item.product.name
            tvQty.text = "Qty: ${item.quantity}"
            tvLinePrice.text = item.formattedLineTotal()
        }
    }
}
