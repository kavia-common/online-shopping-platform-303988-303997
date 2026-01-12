package com.example.kotlinfrontend.ui

import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.example.kotlinfrontend.R

/**
 * Controls the cart badge action view in a Toolbar menu item.
 */
class CartBadgeController(
    private val toolbar: Toolbar,
    private val menuItem: MenuItem
) {

    private val actionView: View =
        menuItem.actionView ?: toolbar.inflateActionView().also { menuItem.actionView = it }

    private val badgeText: TextView = actionView.findViewById(R.id.cartBadge)

    // PUBLIC_INTERFACE
    fun setCount(count: Int) {
        /** Update badge visibility and count text. */
        if (count <= 0) {
            badgeText.visibility = View.GONE
        } else {
            badgeText.visibility = View.VISIBLE
            badgeText.text = if (count > 99) "99+" else count.toString()
        }
    }

    // PUBLIC_INTERFACE
    fun setOnClickListener(listener: () -> Unit) {
        /** Ensure clicking the action view triggers the menu action. */
        actionView.setOnClickListener { listener() }
    }

    /**
     * Toolbar has no public helper for inflating an action view; keep it local.
     */
    private fun Toolbar.inflateActionView(): View {
        val inflater = android.view.LayoutInflater.from(context)
        return inflater.inflate(R.layout.view_cart_badge_action, this, false)
    }
}
