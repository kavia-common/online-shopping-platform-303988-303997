package com.example.kotlinfrontend.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.kotlinfrontend.data.AppRepositories
import com.example.kotlinfrontend.databinding.ActivityCartBinding

/**
 * Cart screen.
 *
 * In demo mode, schedules an inactivity reminder when cart has items.
 */
class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private lateinit var repos: AppRepositories

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repos = AppRepositories(applicationContext)

        // Existing cart view model/repo wiring may exist elsewhere; keep hook minimal.
        // When cart list changes, update demo reminder schedule.
        repos.notificationsRepository.observeNotifications() // no-op, ensures repo init

        // If there is an existing view model in the project, you can hook into it; here we do a
        // simple best-effort using a placeholder observer that should be replaced by the existing cart state.
        // For now, schedule based on adapter item count when available.
        binding.recyclerView.viewTreeObserver.addOnGlobalLayoutListener {
            val hasItems = binding.recyclerView.adapter?.itemCount?.let { it > 0 } ?: false
            repos.demoNotificationsEngine.onCartActivityHook(hasItems)
        }
    }
}
