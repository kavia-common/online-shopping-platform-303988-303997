package com.example.kotlinfrontend.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
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

        binding.swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                repo.fetchLatest()
                binding.swipeRefresh.isRefreshing = false
            }
        }

        // Seed demo content if empty so UI is useful even without backend.
        repo.seedDemoIfEmpty()

        // Observe list.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.items.collectLatest { list ->
                    adapter.submitList(list)
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
