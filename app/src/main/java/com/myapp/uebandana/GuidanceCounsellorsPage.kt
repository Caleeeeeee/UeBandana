package com.myapp.uebandana

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class GuidanceCounsellorsPage : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guidance_counsellors_page)

        // Set up the toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Set up the drawer layout
        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        
        // Customize the drawer toggle
        toggle.drawerArrowDrawable.color = resources.getColor(R.color.white, theme)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Set up navigation view with listener
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        
        // Clear any existing menu and set the appropriate one based on user type
        navigationView.menu.clear()
        if (UserSession.isAdmin()) {
            navigationView.inflateMenu(R.menu.drawer_menu_admin)
        } else {
            navigationView.inflateMenu(R.menu.drawer_menu_student)
        }
        
        navigationView.setNavigationItemSelectedListener(this)
        
        // Set the selected item in the navigation drawer
        if (navigationView.menu.findItem(R.id.nav_counsellors) != null) {
            navigationView.setCheckedItem(R.id.nav_counsellors)
        }
        
        // Update navigation header with user name
        val headerView = navigationView.getHeaderView(0)
        val navUsername: TextView = headerView.findViewById(R.id.nav_header_username)
        navUsername.text = "Welcome, ${UserSession.userName}"
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation item clicks
        when (item.itemId) {
            R.id.nav_user -> {
                startActivity(Intent(this, UserPage::class.java))
                finish()
            }
            R.id.nav_reports -> {
                startActivity(Intent(this, ReportsPage::class.java))
                finish()
            }
            R.id.nav_counsellors -> {
                // Already on this page
                return true
            }
            R.id.nav_freedom_wall -> {
                startActivity(Intent(this, FreedomWallPage::class.java))
                finish()
            }
            R.id.nav_diary -> {
                startActivity(Intent(this, DiaryPage::class.java))
                finish()
            }
            R.id.nav_mental_health -> {
                startActivity(Intent(this, MentalHealthPage::class.java))
                finish()
            }
            R.id.nav_notifications -> {
                startActivity(Intent(this, NotificationPage::class.java))
                finish()
            }
            R.id.nav_sign_out -> {
                // Handle sign out
                Toast.makeText(this, "Signing out...", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginPage::class.java)
                startActivity(intent)
                finish()
            }
        }

        // Close the drawer
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        // Close drawer if open, otherwise proceed with back
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}