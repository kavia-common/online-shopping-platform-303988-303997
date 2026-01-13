package com.example.kotlinfrontend.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.data.AppRepositories
import com.example.kotlinfrontend.databinding.ActivityNotificationsBinding
import com.example.kotlinfrontend.model.NotificationDeeplink
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationsActivity : ComponentActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var adapter: NotificationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repo = AppRepositories.notifications(this)

        binding.toolbar.title = getString(R.string.notifications_title)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_notifications)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_mark_all_read -> {
                    repo.markAllRead()
                    binding.root.announceForAccessibility(getString(R.string.a11y_mark_all_read))
                    true
                }
                else -> false
            }
        }

        adapter = NotificationsAdapter(onClick = { item ->
            // Optimistic read mark.
            repo.markRead(item.id)
            binding.root.announceForAccessibility(getString(R.string.a11y_notification_marked_read))

            // Navigate when applicable.
            when (item.deeplink) {
                NotificationDeeplink.ORDERS -> startActivity(Intent(this, OrdersActivity::class.java))
                NotificationDeeplink.CART -> startActivity(Intent(this, CartActivity::class.java))
                NotificationDeeplink.NONE -> {
                    // No navigation.
                }
            }
        })

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        setupRecyclerAnimations()
        setupStickyHeaders()
        setupSwipeActions(repo)

        binding.swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                repo.fetchLatest()
                binding.swipeRefresh.isRefreshing = false
            }
        }

        // Seed demo content if empty so UI is useful even without backend.
        repo.seedDemoIfEmpty()

        // Observe list and convert to sectioned rows.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.items.collectLatest { list ->
                    val rows = buildRows(repo, list)
                    adapter.submitRows(rows)

                    // Empty state driven by actual items (ignore headers).
                    binding.emptyState.isVisible = list.isEmpty()
                }
            }
        }

        // Initial backend refresh (best-effort).
        lifecycleScope.launch {
            try {
                binding.swipeRefresh.isRefreshing = true
                repo.fetchLatest()
            } catch (t: Throwable) {
                Snackbar.make(binding.root, t.message ?: "Failed to refresh notifications.", Snackbar.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun buildRows(
        repo: com.example.kotlinfrontend.data.NotificationsRepository,
        items: List<com.example.kotlinfrontend.model.NotificationItem>
    ): List<NotificationsAdapter.Row> {
        val grouped = repo.groupForUi(items)
        val rows = ArrayList<NotificationsAdapter.Row>(items.size + grouped.size)
        for ((bucket, list) in grouped) {
            rows.add(NotificationsAdapter.Row.Header(bucket))
            list.forEach { rows.add(NotificationsAdapter.Row.Item(it)) }
        }
        return rows
    }

    private fun setupStickyHeaders() {
        // Add sticky header decoration (time buckets).
        binding.recyclerView.addItemDecoration(
            NotificationsSectionHeaderDecoration(
                isHeader = { pos ->
                    adapter.getRowAt(pos) is NotificationsAdapter.Row.Header
                },
                createHeaderView = { parent ->
                    layoutInflater.inflate(R.layout.item_notification_section_header, parent, false)
                },
                bindHeaderView = { header, headerPos ->
                    val row = adapter.getRowAt(headerPos) as? NotificationsAdapter.Row.Header ?: return@NotificationsSectionHeaderDecoration
                    val tv = header.findViewById<android.widget.TextView>(R.id.sectionHeader)
                    val resId = when (row.bucket) {
                        com.example.kotlinfrontend.data.NotificationsRepository.TimeBucket.TODAY -> R.string.notifications_section_today
                        com.example.kotlinfrontend.data.NotificationsRepository.TimeBucket.THIS_WEEK -> R.string.notifications_section_this_week
                        com.example.kotlinfrontend.data.NotificationsRepository.TimeBucket.EARLIER -> R.string.notifications_section_earlier
                    }
                    tv.setText(resId)
                    tv.contentDescription = tv.text
                }
            )
        )
    }

    private fun setupSwipeActions(repo: com.example.kotlinfrontend.data.NotificationsRepository) {
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                // Disable swipe on headers.
                val pos = viewHolder.bindingAdapterPosition
                val row = adapter.getRowAt(pos)
                return if (row is NotificationsAdapter.Row.Header) 0 else super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                val row = adapter.getRowAt(pos) as? NotificationsAdapter.Row.Item
                if (row == null) {
                    adapter.notifyItemChanged(viewHolder.bindingAdapterPosition)
                    return
                }

                val item = row.item
                if (direction == ItemTouchHelper.RIGHT) {
                    // Swipe right: mark read.
                    repo.markRead(item.id)
                    binding.root.announceForAccessibility(getString(R.string.a11y_notification_marked_read))
                    // UI will refresh from Flow; ensure row is restored visually immediately.
                    val adapterPos = adapter.findAdapterPositionForNotificationId(item.id)
                    if (adapterPos >= 0) adapter.notifyItemChanged(adapterPos)
                } else {
                    // Swipe left: dismiss with undo.
                    val removed = repo.dismiss(item.id)
                    if (removed == null) return

                    binding.root.announceForAccessibility(getString(R.string.a11y_notification_dismissed))
                    Snackbar.make(binding.root, getString(R.string.notifications_dismissed_snackbar), Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.action_undo)) {
                            repo.undoDismiss(removed)
                            // Accessibility: announce restoration.
                            binding.root.announceForAccessibility(getString(R.string.action_undo))
                        }
                        .show()
                }
            }
        })
        touchHelper.attachToRecyclerView(binding.recyclerView)

        // Accessibility hint for the list itself.
        binding.recyclerView.contentDescription =
            getString(R.string.a11y_swipe_right_mark_read) + ". " + getString(R.string.a11y_swipe_left_dismiss)
    }

    private fun setupRecyclerAnimations() {
        (binding.recyclerView.itemAnimator as? DefaultItemAnimator)?.apply {
            supportsChangeAnimations = false
            addDuration = resources.getInteger(R.integer.anim_item_appear_duration_ms).toLong()
            removeDuration = 120L
            moveDuration = 120L
            changeDuration = 120L
        } ?: run {
            binding.recyclerView.itemAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = false
                addDuration = resources.getInteger(R.integer.anim_item_appear_duration_ms).toLong()
                removeDuration = 120L
                moveDuration = 120L
                changeDuration = 120L
            }
        }
    }
}
