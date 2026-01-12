package org.example.app

import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import org.example.app.data.ShopRepository

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    private var lastNavClickUptimeMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize repository persistence early so state is restored before Fragments observe it.
        ShopRepository.initialize(applicationContext)

        // Single-activity host layout: toolbar + NavHostFragment + footer
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Top-level destinations show no-up behavior; others show "up".
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.catalogFragment,
                R.id.cartFragment
            )
        )

        toolbar.setupWithNavController(navController, appBarConfiguration)

        // Footer shortcuts (kept global/persistent).
        bindFooterNavigation()

        // Update cart badge in toolbar whenever cart changes.
        // We re-run prepareOptionsMenu() so onPrepareOptionsMenu can update the action view.
        ShopRepository.cartLiveData().observe(this, Observer {
            invalidateOptionsMenu()
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Attach/refresh cart badge action view.
        val cartItem = menu.findItem(R.id.menu_cart)
        if (cartItem != null) {
            cartItem.setActionView(R.layout.view_cart_badge)
            val badgeText = cartItem.actionView?.findViewById<TextView>(R.id.cart_badge)

            val count = ShopRepository.cartItems().sumOf { it.quantity }
            if (badgeText != null) {
                if (count > 0) {
                    badgeText.visibility = android.view.View.VISIBLE
                    badgeText.text = if (count > 99) "99+" else count.toString()
                } else {
                    badgeText.visibility = android.view.View.GONE
                    badgeText.text = ""
                }
            }

            // Make the whole action view clickable and behave like selecting the menu item.
            cartItem.actionView?.setOnClickListener {
                onOptionsItemSelected(cartItem)
            }
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> return super.onOptionsItemSelected(item)

            R.id.menu_catalog -> {
                safeNavigateToTopLevel(R.id.catalogFragment)
                return true
            }

            R.id.menu_cart -> {
                safeNavigateToTopLevel(R.id.cartFragment)
                return true
            }

            R.id.menu_favorites -> {
                // Favorites list screen is not implemented as a destination in this app.
                // Keep entry optional/non-blocking: if no destination exists, do nothing.
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun bindFooterNavigation() {
        val home = findViewById<TextView>(R.id.footer_home)
        val catalog = findViewById<TextView>(R.id.footer_catalog)
        val cart = findViewById<TextView>(R.id.footer_cart)

        home.contentDescription = getString(R.string.cd_nav_home)
        catalog.contentDescription = getString(R.string.cd_nav_catalog)
        cart.contentDescription = getString(R.string.cd_nav_cart)

        home.isClickable = true
        home.isFocusable = true
        catalog.isClickable = true
        catalog.isFocusable = true
        cart.isClickable = true
        cart.isFocusable = true

        home.setOnClickListener { safeNavigateToTopLevel(R.id.homeFragment) }
        catalog.setOnClickListener { safeNavigateToTopLevel(R.id.catalogFragment) }
        cart.setOnClickListener { safeNavigateToTopLevel(R.id.cartFragment) }
    }

    private fun safeNavigateToTopLevel(destinationId: Int) {
        // Basic debounce so rapid taps don't spam the NavController and cause IllegalArgumentException.
        val now = SystemClock.uptimeMillis()
        if (now - lastNavClickUptimeMs < 350L) return
        lastNavClickUptimeMs = now

        val currentDest = navController.currentDestination?.id
        if (currentDest == destinationId) return

        // For top-level tabs, just navigate directly; Navigation component handles back stack.
        navController.navigate(destinationId)
    }
}
