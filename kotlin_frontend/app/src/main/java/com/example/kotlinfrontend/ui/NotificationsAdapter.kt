package com.example.kotlinfrontend.ui

import android.graphics.Typeface
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.data.NotificationsRepository
import com.example.kotlinfrontend.model.NotificationItem
import com.example.kotlinfrontend.model.NotificationType

/**
 * RecyclerView adapter that renders a sectioned list:
 * - sticky-ish headers via ItemDecoration (see NotificationsSectionHeaderDecoration)
 * - mixed view types (header + notification row)
 *
 * We keep this adapter "dumb": it accepts an already-sectioned list of rows from the Activity.
 */
class NotificationsAdapter(
    private val onClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Header(val bucket: NotificationsRepository.TimeBucket) : Row()
        data class Item(val item: NotificationItem) : Row()
    }

    private val rows = mutableListOf<Row>()

    init {
        setHasStableIds(true)
    }

    // PUBLIC_INTERFACE
    fun submitRows(newRows: List<Row>) {
        /** Replace the full list of rows. For simplicity we use notifyDataSetChanged() (list is small). */
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    // PUBLIC_INTERFACE
    fun getRowAt(adapterPosition: Int): Row? {
        /** Access a row at a given adapter position (used by ItemTouchHelper). */
        if (adapterPosition < 0 || adapterPosition >= rows.size) return null
        return rows[adapterPosition]
    }

    // PUBLIC_INTERFACE
    fun findAdapterPositionForNotificationId(id: String): Int {
        /** Find adapter position of a given notification id, or -1 if not present. */
        return rows.indexOfFirst { it is Row.Item && it.item.id == id }
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemId(position: Int): Long {
        return when (val row = rows[position]) {
            is Row.Header -> ("header:" + row.bucket.name).hashCode().toLong()
            is Row.Item -> row.item.id.hashCode().toLong()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is Row.Header -> VIEW_TYPE_HEADER
            is Row.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val v = inflater.inflate(R.layout.item_notification_section_header, parent, false)
                HeaderVH(v)
            }

            else -> {
                val v = inflater.inflate(R.layout.item_notification, parent, false)
                ItemVH(v, onClick)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderVH).bind(row)
            is Row.Item -> (holder as ItemVH).bind(row.item)
        }
    }

    class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.sectionHeader)

        fun bind(row: Row.Header) {
            val resId = when (row.bucket) {
                NotificationsRepository.TimeBucket.TODAY -> R.string.notifications_section_today
                NotificationsRepository.TimeBucket.THIS_WEEK -> R.string.notifications_section_this_week
                NotificationsRepository.TimeBucket.EARLIER -> R.string.notifications_section_earlier
            }
            title.setText(resId)

            // Accessibility: treat as heading, and avoid redundant talkback when scrolling quickly.
            title.contentDescription = title.text
        }
    }

    class ItemVH(
        itemView: View,
        private val onClick: (NotificationItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val unreadDot: View = itemView.findViewById(R.id.unreadDot)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val message: TextView = itemView.findViewById(R.id.message)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)

        fun bind(item: NotificationItem) {
            title.text = item.title
            message.text = item.message
            timestamp.text = relativeTime(item.createdAtMillis)

            val isUnread = !item.read
            unreadDot.visibility = if (isUnread) View.VISIBLE else View.GONE
            title.setTypeface(null, if (isUnread) Typeface.BOLD else Typeface.NORMAL)

            val iconRes = typeToIconRes(item.type)
            icon.setImageResource(iconRes)
            icon.contentDescription = itemView.context.getString(typeToA11yRes(item.type))

            // Accessibility: include status + title (icon is separate focus for talkback users).
            itemView.contentDescription = if (isUnread) {
                itemView.context.getString(R.string.a11y_notification_unread) + ". " + item.title
            } else {
                itemView.context.getString(R.string.a11y_notification_read) + ". " + item.title
            }

            itemView.setOnClickListener { onClick(item) }
            itemView.subtleAppear(itemView.context.animDuration(R.integer.anim_item_appear_duration_ms))
        }

        private fun relativeTime(createdAtMillis: Long): CharSequence {
            val now = System.currentTimeMillis()
            return DateUtils.getRelativeTimeSpanString(
                createdAtMillis,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
        }

        private fun typeToIconRes(type: NotificationType): Int {
            // Reusing existing icons to avoid adding new drawable assets.
            // If you add dedicated icons later, update this mapping.
            return when (type) {
                NotificationType.ORDER_PLACED -> R.drawable.ic_category_generic
                NotificationType.ORDER_PAID -> R.drawable.ic_payment_card_generic
                NotificationType.SHIPPED -> R.drawable.ic_category_generic
                NotificationType.DELIVERED -> R.drawable.ic_category_generic
                NotificationType.CART_REMINDER -> R.drawable.ic_cart_24
                NotificationType.GENERAL -> R.drawable.ic_notifications_24
            }
        }

        private fun typeToA11yRes(type: NotificationType): Int {
            return when (type) {
                NotificationType.ORDER_PLACED -> R.string.a11y_notification_type_order_placed
                NotificationType.ORDER_PAID -> R.string.a11y_notification_type_order_paid
                NotificationType.SHIPPED -> R.string.a11y_notification_type_shipped
                NotificationType.DELIVERED -> R.string.a11y_notification_type_delivered
                NotificationType.CART_REMINDER -> R.string.a11y_notification_type_cart_reminder
                NotificationType.GENERAL -> R.string.a11y_notification_type_general
            }
        }
    }

    private companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_ITEM = 2
    }
}
