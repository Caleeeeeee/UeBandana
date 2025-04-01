package com.myapp.uebandana

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
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
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiaryPage : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "DiaryPage"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    
    // Diary UI components
    private lateinit var diaryDateText: TextView
    private lateinit var diaryTitleEdit: EditText
    private lateinit var diaryContentEdit: EditText
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar
    
    // Flag to track if we're updating an existing diary
    private var isUpdating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary_page)

        // Initialize diary UI components
        diaryDateText = findViewById(R.id.diary_date)
        diaryTitleEdit = findViewById(R.id.diary_title)
        diaryContentEdit = findViewById(R.id.diary_content)
        saveButton = findViewById(R.id.save_button)
        
        // Add a progress bar to the layout
        addProgressBarToDiaryLayout()
        
        // Set up current date
        setupCurrentDate()
        
        // Load existing diary entry if it exists
        loadDiaryEntry()
        
        // Set up button listeners
        setupButtonListeners()

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
        navigationView.setCheckedItem(R.id.nav_diary)
        
        
        val headerView = navigationView.getHeaderView(0)
        val navUsername: TextView = headerView.findViewById(R.id.nav_header_username)
        navUsername.text = "Welcome, ${UserSession.userName}"
    }
    
    private fun addProgressBarToDiaryLayout() {
        progressBar = ProgressBar(this)
        progressBar.visibility = View.GONE
        
        val linearLayout = findViewById<View>(R.id.content_frame) as? androidx.appcompat.widget.LinearLayoutCompat
        if (linearLayout != null) {
            val params = androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                androidx.appcompat.widget.LinearLayoutCompat.LayoutParams.WRAP_CONTENT,
                androidx.appcompat.widget.LinearLayoutCompat.LayoutParams.WRAP_CONTENT
            )
            params.gravity = android.view.Gravity.CENTER
            params.topMargin = 16
            params.bottomMargin = 16
            linearLayout.addView(progressBar, linearLayout.indexOfChild(saveButton), params)
        }
    }
    
    private fun setupCurrentDate() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        diaryDateText.text = currentDate
    }
    
    private fun loadDiaryEntry() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "No user logged in")
            Toast.makeText(this, "You need to be logged in to view your diary", Toast.LENGTH_SHORT).show()
            return
        }
        
        progressBar.visibility = View.VISIBLE
        
        // Try to fetch an existing diary entry for the current user
        db.collection("diary")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE
                
                if (document.exists()) {
                    // We found an existing diary entry, load it for editing
                    val title = document.getString("title") ?: ""
                    val content = document.getString("content") ?: ""
                    
                    diaryTitleEdit.setText(title)
                    diaryContentEdit.setText(content)
                    
                    isUpdating = true
                    saveButton.text = "Update"
                    
                    Log.d(TAG, "Existing diary entry loaded for editing")
                } else {
                    // No existing diary entry found
                    isUpdating = false
                    saveButton.text = "Save"
                    Log.d(TAG, "No existing diary entry found")
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Error loading diary entry: ${e.message}", e)
                Toast.makeText(this, "Error loading diary entry", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun setupButtonListeners() {
        // Save button click listener
        saveButton.setOnClickListener {
            val title = diaryTitleEdit.text.toString().trim()
            val content = diaryContentEdit.text.toString().trim()
            
            // Validate input
            if (title.isEmpty()) {
                diaryTitleEdit.error = "Please enter a title"
                return@setOnClickListener
            }
            
            if (content.isEmpty()) {
                Toast.makeText(this, "Please write some content", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Save or update diary entry to Firestore
            saveDiaryToFirestore(title, content)
        }
    }
    
    private fun saveDiaryToFirestore(title: String, content: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "No user logged in")
            Toast.makeText(this, "You need to be logged in to save your diary", Toast.LENGTH_SHORT).show()
            return
        }
        
        progressBar.visibility = View.VISIBLE
        saveButton.isEnabled = false
        
        // Create a diary entry object
        val diaryEntry = hashMapOf(
            "title" to title,
            "content" to content,
            "createdAt" to if (isUpdating) Timestamp.now() else Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )
        
        // Save to Firestore - using the user's UID as the document ID
        db.collection("diary")
            .document(currentUser.uid)
            .set(diaryEntry)  // Using set instead of add to ensure only one diary per user
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true
                
                val message = if (isUpdating) "Diary updated successfully!" else "Diary saved successfully!"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                
                // Update flags
                isUpdating = true
                saveButton.text = "Update"
                
                Log.d(TAG, "Diary entry saved successfully")
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true
                
                Toast.makeText(this, "Error saving diary: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error saving diary entry: ${e.message}", e)
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
                // Already on this page
                return true
            }
            R.id.nav_respond -> {
                startActivity(Intent(this, RespondToStudentsPage::class.java))
                finish()
            }
            R.id.nav_view_reports -> {
                startActivity(Intent(this, AdminReportsPage::class.java))
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
                UserSession.logout()
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