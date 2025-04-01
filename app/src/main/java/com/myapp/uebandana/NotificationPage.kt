package com.myapp.uebandana

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class NotificationPage : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, 
    NotificationsAdapter.OnNotificationClickListener {

    private val TAG = "NotificationPage" // Tag for logging

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    
    // Notification UI components
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var notificationsAdapter: NotificationsAdapter
    
    // Firebase components
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Notifications list
    private val notifications = mutableListOf<NotificationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_page)

        // Initialize notification UI components
        recyclerView = findViewById(R.id.notifications_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        progressBar = findViewById(R.id.progress_bar)
        
        // Set up the RecyclerView
        setupRecyclerView()
        
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
        navigationView.setCheckedItem(R.id.nav_notifications)
        
        // Update navigation header with user name
        val headerView = navigationView.getHeaderView(0)
        val navUsername: TextView = headerView.findViewById(R.id.nav_header_username)
        navUsername.text = "Welcome, ${UserSession.userName}"
        
        // Add FAB for admin
        if (UserSession.isAdmin()) {
            val fab = findViewById<FloatingActionButton>(R.id.fab_add_notification)
            fab.visibility = View.VISIBLE
            fab.setOnClickListener {
                showCreateNotificationDialog()
            }
        }
        
        // Load notifications from Firebase
        loadNotifications()
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        notificationsAdapter = NotificationsAdapter(notifications, this, UserSession.isAdmin())
        recyclerView.adapter = notificationsAdapter
    }
    
    private fun showCreateNotificationDialog() {
        val dialog = Dialog(this, R.style.BottomDialogTheme)
        dialog.setContentView(R.layout.dialog_create_notification)
        
        // Set dialog width to match parent and adjust for keyboard
        val window = dialog.window
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(Gravity.BOTTOM)
        
        // This prevents the keyboard from pushing up the entire dialog
        window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )

        val titleEdit = dialog.findViewById<TextInputEditText>(R.id.notification_title)
        val messageEdit = dialog.findViewById<TextInputEditText>(R.id.notification_message)
        val sendButton = dialog.findViewById<Button>(R.id.send_button)
        val cancelButton = dialog.findViewById<Button>(R.id.cancel_button)

        sendButton.setOnClickListener {
            val title = titleEdit.text.toString().trim()
            val message = messageEdit.text.toString().trim()
            
            if (message.isNotEmpty()) {
                // Create notification with title (if provided)
                createNotification(title, message)
                dialog.dismiss()
            } else {
                messageEdit.error = "Message cannot be empty"
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
    
    private fun createNotification(title: String, message: String) {
        Log.d(TAG, "Creating new notification: title='$title', message='$message'")
        
        // Create a notification for all users (admin broadcast)
        val notification = hashMapOf(
            "title" to title,
            "message" to message,
            "isRead" to false,
            "createdAt" to Timestamp.now(),
            "isGlobal" to true  // Mark this as a global notification
        )

        db.collection("notifications")
            .add(notification)
            .addOnSuccessListener { documentRef ->
                Log.d(TAG, "Notification created with ID: ${documentRef.id}")
                Toast.makeText(this, "Notification sent", Toast.LENGTH_SHORT).show()
                loadNotifications()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creating notification", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun loadNotifications() {
        progressBar.visibility = View.VISIBLE
        Log.d(TAG, "Starting to load notifications. User is admin: ${UserSession.isAdmin()}")
        
        try {
            if (UserSession.isAdmin()) {
                // Admins see all notifications
                Log.d(TAG, "Loading all notifications (admin view)")
                db.collection("notifications")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { processNotificationsResult(it) }
                    .addOnFailureListener { handleError(it) }
            } else {
                // For regular users, just get all notifications without filters
                // This is a simpler approach to avoid query issues
                Log.d(TAG, "Loading notifications for regular user")
                db.collection("notifications")
                    .get()
                    .addOnSuccessListener { documents ->
                        Log.d(TAG, "Retrieved ${documents.size()} notifications")
                        // Process the results here by filtering in-memory
                        val filteredDocs = documents.filter { doc ->
                            // Keep notifications that are global or have no isGlobal field
                            doc.getBoolean("isGlobal") ?: true
                        }
                        Log.d(TAG, "Filtered to ${filteredDocs.size} notifications")
                        processNotificationsResult(filteredDocs)
                    }
                    .addOnFailureListener { handleError(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error setting up notification query", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
        }
    }
    
    private fun processNotificationsResult(documents: Iterable<com.google.firebase.firestore.DocumentSnapshot>) {
        try {
            Log.d(TAG, "Processing notification results")
            val notificationsList = documents.mapNotNull { doc ->
                try {
                    val timestamp = doc.getTimestamp("createdAt")
                    val title = doc.getString("title") ?: ""
                    val message = doc.getString("message") ?: ""
                    val isRead = doc.getBoolean("isRead") ?: false
                    
                    Log.d(TAG, "Processing notification: id=${doc.id}, title=$title")
                    
                    NotificationItem(
                        id = doc.id,
                        title = title,
                        message = message,
                        timeAgo = getTimeAgo(timestamp),
                        isRead = isRead,
                        timestamp = timestamp
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing notification doc ${doc.id}", e)
                    null
                }
            }
            
            Log.d(TAG, "Processed ${notificationsList.size} valid notifications")
            
            notifications.clear()
            notifications.addAll(notificationsList)
            notificationsAdapter.notifyDataSetChanged()
            
            progressBar.visibility = View.GONE
            
            // Show empty view if there are no notifications
            if (notifications.isEmpty()) {
                Log.d(TAG, "No notifications to display, showing empty view")
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in processNotificationsResult", e)
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Error processing notifications: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleError(e: Exception) {
        Log.e(TAG, "Error loading notifications", e)
        progressBar.visibility = View.GONE
        
        // More descriptive error message with error code if available
        val errorMessage = when (e) {
            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                "Database error (${e.code}): ${e.message}"
            }
            else -> {
                "Error: ${e.message}"
            }
        }
        
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    private fun getTimeAgo(timestamp: Timestamp?): String {
        if (timestamp == null) return "Unknown"
        
        val now = System.currentTimeMillis()
        val timeStampMillis = timestamp.toDate().time
        val diffMillis = now - timeStampMillis
        
        return when {
            diffMillis < 60 * 60 * 1000 -> "Just now"
            diffMillis < 24 * 60 * 60 * 1000 -> "${diffMillis / (60 * 60 * 1000)}h ago"
            diffMillis < 48 * 60 * 60 * 1000 -> "Yesterday"
            diffMillis < 7 * 24 * 60 * 60 * 1000 -> "${diffMillis / (24 * 60 * 60 * 1000)}d ago"
            else -> "${diffMillis / (7 * 24 * 60 * 60 * 1000)}w ago"
        }
    }

    override fun onNotificationClick(notification: NotificationItem) {
        Log.d(TAG, "Notification clicked: ${notification.id}")
        
        // Mark notification as read in Firestore
        db.collection("notifications").document(notification.id)
            .update("isRead", true)
            .addOnSuccessListener {
                Log.d(TAG, "Notification marked as read: ${notification.id}")
                // Update local data
                val position = notifications.indexOfFirst { it.id == notification.id }
                if (position != -1) {
                    notifications[position] = notification.copy(isRead = true)
                    notificationsAdapter.notifyItemChanged(position)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error marking notification as read", e)
            }
    }

    override fun onViewButtonClick(notification: NotificationItem) {
        // Mark notification as read and redirect based on content
        onNotificationClick(notification)
        
        // Basic content-based redirection
        when {
            notification.message.contains("report", ignoreCase = true) -> {
                if (UserSession.isAdmin()) {
                    startActivity(Intent(this, AdminReportsPage::class.java))
                } else {
                    startActivity(Intent(this, ReportsPage::class.java))
                }
            }
            notification.message.contains("wall", ignoreCase = true) -> {
                startActivity(Intent(this, FreedomWallPage::class.java))
            }
            notification.message.contains("mental health", ignoreCase = true) -> {
                startActivity(Intent(this, MentalHealthPage::class.java))
            }
            else -> {
                Toast.makeText(this, "Notification viewed", Toast.LENGTH_SHORT).show()
            }
        }
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
            R.id.nav_mental_health -> {
                startActivity(Intent(this, MentalHealthPage::class.java))
                finish()
            }
            R.id.nav_respond -> {
                startActivity(Intent(this, RespondToStudentsPage::class.java))
                finish()
            }
            R.id.nav_view_reports -> {
                startActivity(Intent(this, AdminReportsPage::class.java))
                finish()
            }
            R.id.nav_notifications -> {
                // Already on this page
                return true
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