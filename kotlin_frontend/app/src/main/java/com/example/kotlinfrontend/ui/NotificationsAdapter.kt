package com.example.kotlinfrontend.ui

import android.graphics.Typeface
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.model.NotificationItem
import com.example.kotlinfrontend.model.NotificationType

class NotificationsAdapter(
    private val onClick: (NotificationItem) -> Unit
) : ListAdapter<NotificationItem, NotificationsAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
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

            // Simple icon mapping (reusing existing drawables to keep deps minimal).
            val iconRes = when (item.type) {
                NotificationType.ORDER -> R.drawable.ic_category_generic
                NotificationType.CART -> R.drawable.ic_cart_24
                NotificationType.PROMO -> R.drawable.ic_category_generic
            }
            icon.setImageResource(iconRes)

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
    }

    private object Diff : DiffUtil.ItemCallback<NotificationItem>() {
        override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean =
            oldItem == newItem
    }
}
