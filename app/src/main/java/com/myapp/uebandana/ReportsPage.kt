package com.myapp.uebandana

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
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
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class ReportsPage : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "ReportsPage"
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    
    // UI components
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progressBar: View
    private lateinit var fab: FloatingActionButton
    
    // Firebase
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Reports list
    private val reportsList = mutableListOf<Report>()
    private lateinit var reportsAdapter: ReportsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports_page)

        // Check if user is student, admins should use AdminReportsPage
        if (UserSession.isAdmin()) {
            startActivity(Intent(this, AdminReportsPage::class.java))
            finish()
            return
        }
        
        // Initialize UI components
        initializeViews()
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
        navigationView.inflateMenu(R.menu.drawer_menu_student)
        navigationView.setNavigationItemSelectedListener(this)
        
        // Set the selected item in the navigation drawer
        navigationView.setCheckedItem(R.id.nav_reports)
        
        // Update navigation header with user name
        val headerView = navigationView.getHeaderView(0)
        val navUsername: TextView = headerView.findViewById(R.id.nav_header_username)
        navUsername.text = "Welcome, ${UserSession.userName}"
        
        // Set up FAB for creating new reports
        fab.setOnClickListener {
            showCreateReportDialog()
        }
        
        // Load user's reports
        loadUserReports()
    }
    
    private fun initializeViews() {
        recyclerView = findViewById(R.id.reports_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        progressBar = findViewById(R.id.progress_bar)
        fab = findViewById(R.id.fab_create_report)
    }

    private fun checkGooglePlayServices(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)

        if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(this, resultCode, 1)?.show()
            } else {
                Toast.makeText(
                    this,
                    "This device is not supported for Google Play Services",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            return false
        }
        return true
    }

    private fun setupRecyclerView() {
        reportsAdapter = ReportsAdapter(reportsList) { report ->
            // Show report details when clicked
            showReportDetailsDialog(report)
        }
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ReportsPage)
            adapter = reportsAdapter
        }
    }

    private fun showErrorToast(error: Exception) {
        val errorMessage = when {
            error.message?.contains("PERMISSION_DENIED") == true -> "You don't have permission to access reports"
            error.message?.contains("network") == true -> "Network error. Please check your connection"
            else -> "Error loading reports: ${error.message}"
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }


    private fun loadUserReports() {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE

        try {
            db.collection("reports")
                .whereEqualTo("userId", currentUser.uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { documents ->
                    progressBar.visibility = View.GONE

                    if (documents.isEmpty) {
                        showEmptyState()
                        return@addOnSuccessListener
                    }

                    reportsList.clear()

                    for (document in documents) {
                        try {
                            val report = Report(
                                id = document.id,
                                userId = document.getString("userId") ?: "",
                                title = document.getString("title") ?: "",
                                description = document.getString("description") ?: "",
                                status = document.getString("status") ?: "Pending",
                                createdAt = document.getTimestamp("createdAt") ?: Timestamp.now(),
                                adminResponse = document.getString("adminResponse") ?: ""
                            )
                            reportsList.add(report)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing report document", e)
                        }
                    }

                    reportsAdapter.notifyDataSetChanged()

                    if (reportsList.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    showEmptyState()
                    Log.e(TAG, "Error getting reports", e)
                    if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        showIndexRequiredError(null) // Optionally extract URL from e.message if available
                    } else {
                        showErrorToast(e)
                    }
                }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Log.e(TAG, "Error in loadUserReports", e)
            showErrorToast(e)
        }
    }

    private fun showIndexRequiredError(indexUrl: String?) {
        val message = "Database index required. Please contact the administrator."
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.w(TAG, "Required index URL: https://$indexUrl")
    }

    private fun showEmptyState() {
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
    
    private fun hideEmptyState() {
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
    
    private fun showCreateReportDialog() {
        val dialog = Dialog(this, R.style.BottomDialogTheme)
        dialog.setContentView(R.layout.dialog_create_report)
        
        // Setup dialog window
        val window = dialog.window
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(Gravity.BOTTOM)
        window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        
        // Get dialog views
        val titleInput = dialog.findViewById<TextInputEditText>(R.id.report_title_input)
        val descriptionInput = dialog.findViewById<TextInputEditText>(R.id.report_description_input)
        val submitButton = dialog.findViewById<Button>(R.id.submit_button)
        val cancelButton = dialog.findViewById<Button>(R.id.cancel_button)
        
        // Set submit button action
        submitButton.setOnClickListener {
            val title = titleInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            
            if (title.isEmpty()) {
                titleInput.error = "Title is required"
                return@setOnClickListener
            }
            
            if (description.isEmpty()) {
                descriptionInput.error = "Description is required"
                return@setOnClickListener
            }
            
            // Submit the report
            submitReport(title, description)
            dialog.dismiss()
        }
        
        // Set cancel button action
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun submitReport(title: String, description: String) {
        val currentUser = auth.currentUser ?: return
        
        progressBar.visibility = View.VISIBLE
        
        val report = hashMapOf(
            "userId" to currentUser.uid,
            "userName" to UserSession.userName,
            "title" to title,
            "description" to description,
            "status" to "Pending",
            "createdAt" to Timestamp.now(),
            "adminResponse" to ""
        )
        
        db.collection("reports")
            .add(report)
            .addOnSuccessListener { documentRef ->
                // Create a notification for admins
                createNotificationForAdmin(title)
                
                Toast.makeText(this, "Report submitted successfully", Toast.LENGTH_SHORT).show()
                loadUserReports() // Refresh the list
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Error submitting report", e)
                Toast.makeText(this, "Error submitting report: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun createNotificationForAdmin(reportTitle: String) {
        val notification = hashMapOf(
            "title" to "New Report Submitted",
            "message" to "A new report '$reportTitle' was submitted by ${UserSession.userName}. Please review it.",
            "isRead" to false,
            "createdAt" to Timestamp.now(),
            "isGlobal" to false,
            "forAdmin" to true
        )
        
        db.collection("notifications")
            .add(notification)
            .addOnSuccessListener {
                Log.d(TAG, "Admin notification created successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creating admin notification", e)
            }
    }
    
    private fun showReportDetailsDialog(report: Report) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_report_details)
        
        // Set dialog to be full width
        val window = dialog.window
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Get views
        val titleTextView = dialog.findViewById<TextView>(R.id.report_detail_title)
        val dateTextView = dialog.findViewById<TextView>(R.id.report_detail_date)
        val statusTextView = dialog.findViewById<TextView>(R.id.report_detail_status)
        val descriptionTextView = dialog.findViewById<TextView>(R.id.report_detail_description)
        val adminResponseSection = dialog.findViewById<View>(R.id.admin_response_section)
        val adminResponseText = dialog.findViewById<TextView>(R.id.admin_response_text)
        val closeButton = dialog.findViewById<Button>(R.id.close_button)
        
        // Set report data
        titleTextView.text = report.title
        
        // Format date
        val dateFormat = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
        val dateString = dateFormat.format(report.createdAt.toDate())
        dateTextView.text = dateString
        
        // Set status with color
        statusTextView.text = report.status
        when (report.status) {
            "Pending" -> statusTextView.setTextColor(resources.getColor(R.color.orange, theme))
            "Reviewed" -> statusTextView.setTextColor(resources.getColor(R.color.blue, theme))
            "Resolved" -> statusTextView.setTextColor(resources.getColor(R.color.green, theme))
        }
        
        descriptionTextView.text = report.description
        
        // Show admin response if available
        if (report.adminResponse.isNotEmpty()) {
            adminResponseSection.visibility = View.VISIBLE
            adminResponseText.text = report.adminResponse
        } else {
            adminResponseSection.visibility = View.GONE
        }
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation item clicks
        when (item.itemId) {
            R.id.nav_user -> {
                startActivity(Intent(this, UserPage::class.java))
                finish()
            }
            R.id.nav_reports -> {
                // Already on this page
                return true
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
            R.id.nav_notifications -> {
                startActivity(Intent(this, NotificationPage::class.java))
                finish()
            }
            R.id.nav_sign_out -> {
                // Reset user session
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