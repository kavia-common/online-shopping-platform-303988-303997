package com.example.kotlinfrontend.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.kotlinfrontend.R
import com.example.kotlinfrontend.data.AppRepositories
import com.example.kotlinfrontend.data.DemoNotificationsEngine
import com.example.kotlinfrontend.databinding.ActivityNotificationsBinding

/**
 * Notifications screen.
 *
 * Demo Mode:
 * - Toggle available at top of the screen to enable local simulated notifications.
 * - Long-press the bell icon (toolbar) to open a developer menu for generating events.
 */
class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var repos: AppRepositories
    private lateinit var demoEngine: DemoNotificationsEngine

    private lateinit var adapter: NotificationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repos = AppRepositories(applicationContext)
        demoEngine = repos.demoNotificationsEngine

        adapter = NotificationsAdapter(
            onNotificationClicked = { item ->
                repos.notificationsRepository.markRead(item.id)
            }
        )
        binding.recyclerView.adapter = adapter

        setupDemoToggle()

        repos.notificationsRepository.observeNotifications().observe(this, Observer { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isNullOrEmpty()) View.VISIBLE else View.GONE
        })

        // Long-press toolbar bell icon to open dev menu (fallback to toolbar long press).
        binding.toolbar.setOnLongClickListener {
            showDevMenu()
            true
        }
    }

    private fun setupDemoToggle() {
        val toggle: Switch = binding.demoSwitch
        toggle.isChecked = demoEngine.isDemoEnabled()
        toggle.setOnCheckedChangeListener { _, isChecked ->
            demoEngine.setDemoEnabled(isChecked)
            if (!isChecked) {
                // As requested: cleanup when toggled off.
                demoEngine.cancelAllScheduled()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_notifications, menu)

        // Enable long-press on the bell/menu item to open dev menu quickly.
        // If actionView exists, use it; otherwise, rely on toolbar long-press.
        val bell = menu.findItem(R.id.action_notifications)
        bell?.actionView?.setOnLongClickListener {
            showDevMenu()
            true
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }

        if (item.itemId == R.id.action_notifications) {
            // Normal behavior: no-op here; dev menu on long-press.
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun showDevMenu() {
        val options = arrayOf(
            "Generate: Order Placed",
            "Generate: Order Paid",
            "Generate: Order Shipped",
            "Generate: Order Delivered",
            "Generate: Cart Reminder",
            "Mark All Read",
            "Clear All",
        )

        AlertDialog.Builder(this)
            .setTitle("Demo Notifications (Dev)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> demoEngine.generateOrderNotification(DemoNotificationsEngine.DemoOrderStatus.PLACED)
                    1 -> demoEngine.generateOrderNotification(DemoNotificationsEngine.DemoOrderStatus.PAID)
                    2 -> demoEngine.generateOrderNotification(DemoNotificationsEngine.DemoOrderStatus.SHIPPED)
                    3 -> demoEngine.generateOrderNotification(DemoNotificationsEngine.DemoOrderStatus.DELIVERED)
                    4 -> demoEngine.generateCartReminder()
                    5 -> demoEngine.markAllRead()
                    6 -> demoEngine.clearAllNotifications()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
