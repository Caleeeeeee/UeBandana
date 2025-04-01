package com.myapp.uebandana

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class UserPage : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private var isMenuExpanded = false

    // UI components
    private lateinit var profileImageView: ImageView
    private lateinit var usernameText: TextView
    private lateinit var changePasswordButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_page)

        // Initialize UI components
        profileImageView = findViewById(R.id.profile_image)
        usernameText = findViewById(R.id.username_text)
        changePasswordButton = findViewById(R.id.change_password_button)

        // Set up profile data
        setupProfileData()

        // Set up buttons
        setupButtons()

        // Set up the toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // Set toolbar title based on user type
        val toolbarTitle: TextView = findViewById(R.id.toolbarTitle)
        if (UserSession.isAdmin()) {
            toolbarTitle.text = "Admin"
        } else {
            toolbarTitle.text = "User Profile"
        }

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
        navigationView.setCheckedItem(R.id.nav_user)
        
        // Update navigation header with user name
        val headerView = navigationView.getHeaderView(0)
        val navUsername: TextView = headerView.findViewById(R.id.nav_header_username)
        navUsername.text = "Welcome, ${UserSession.userName}"

        // Add drawer listener to track state
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

            override fun onDrawerOpened(drawerView: View) {
                isMenuExpanded = true
            }

            override fun onDrawerClosed(drawerView: View) {
                isMenuExpanded = false
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })
    }

    private fun setupProfileData() {
        // Set username - display the system-generated username
        usernameText.text = UserSession.userName
    }

    private fun setupButtons() {
        // Set up change password button
        changePasswordButton.setOnClickListener {
            // Handle password change functionality
            Toast.makeText(this, "Change Password clicked", Toast.LENGTH_SHORT).show()
            // Add navigation to change password screen or show dialog
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation item clicks based on user type
        when (item.itemId) {
            R.id.nav_user -> {
                // Already on this page
                return true
            }
            R.id.nav_reports -> {
                // Student-specific reports page
                startActivity(Intent(this, ReportsPage::class.java))
                finish()
            }
            R.id.nav_view_reports -> {
                // Admin-specific view reports page
                startActivity(Intent(this, AdminReportsPage::class.java))
                finish()
            }
            R.id.nav_counsellors -> {
                startActivity(Intent(this, GuidanceCounsellorsPage::class.java))
                finish()
            }
            R.id.nav_freedom_wall -> {
                startActivity(Intent(this, FreedomWallPage::class.java))
                finish()
            }
            R.id.nav_diary -> {
                startActivity(Intent(this, DiaryPage::class.java))
                finish()
            }
            R.id.nav_respond -> {
                // Admin-specific respond to students page
                startActivity(Intent(this, RespondToStudentsPage::class.java))
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
                // Reset user session using the new logout method
                UserSession.logout()
                
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