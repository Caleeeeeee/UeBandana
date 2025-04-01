package com.myapp.uebandana

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class AdminReportsPage : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "AdminReportsPage"

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle

    // UI components
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progressBar: View
    private lateinit var filterRadioGroup: RadioGroup

    // Firebase
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Reports list
    private val reportsList = mutableListOf<Report>()
    private lateinit var reportsAdapter: AdminReportsAdapter

    // Current filter
    private var currentFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "Starting onCreate")
            setContentView(R.layout.activity_admin_reports_page)
            Log.d(TAG, "Content view set")

            if (!UserSession.isAdmin()) {
                Log.d(TAG, "User is not admin")
                Toast.makeText(this, "Unauthorized access", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, UserPage::class.java))
                finish()
                return
            }
            initializeViews()
            Log.d(TAG, "Views initialized")
            setupRecyclerView()
            Log.d(TAG, "RecyclerView setup")
            setupFilterChips()
            Log.d(TAG, "Filter chips setup")

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        drawerLayout = findViewById(R.id.drawer_layout)
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )

        toggle.drawerArrowDrawable.color = resources.getColor(R.color.white, theme)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.inflateMenu(R.menu.drawer_menu_admin)
        navigationView.setNavigationItemSelectedListener(this)

        navigationView.setCheckedItem(R.id.nav_view_reports)

        val headerView = navigationView.getHeaderView(0)
        val navUsername: TextView = headerView.findViewById(R.id.nav_header_username)
        navUsername.text = "Welcome, ${UserSession.userName}"

        // Load all reports
        loadReports()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.admin_reports_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        progressBar = findViewById(R.id.progress_bar)
        filterRadioGroup = findViewById(R.id.filter_radio_group)
    }

    private fun setupRecyclerView() {
        reportsAdapter = AdminReportsAdapter(reportsList) { report ->
            // Show report details when clicked
            showReportDetailsDialog(report)
            // Record that this admin viewed the report
            recordReportView(report.id)
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@AdminReportsPage)
            adapter = reportsAdapter
        }
    }

    private fun setupFilterChips() {
        // Find all filter chips
        val allChip: Chip = findViewById(R.id.filter_all)
        val pendingChip: Chip = findViewById(R.id.filter_pending)
        val reviewedChip: Chip = findViewById(R.id.filter_reviewed)
        val resolvedChip: Chip = findViewById(R.id.filter_resolved)

        // Set listener for filter changes
        filterRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) {
                R.id.filter_all -> "All"
                R.id.filter_pending -> "Pending"
                R.id.filter_reviewed -> "Reviewed"
                R.id.filter_resolved -> "Resolved"
                else -> "All"
            }
            loadReports()
        }
    }

    private fun loadReports() {
        progressBar.visibility = View.VISIBLE

        // Create base query
        var query = db.collection("reports")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        // Apply status filter if not "All"
        if (currentFilter != "All") {
            query = query.whereEqualTo("status", currentFilter)
        }

        query.get()
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
                            userName = document.getString("userName") ?: "Unknown User",
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
                Toast.makeText(this, "Error loading reports: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEmptyState() {
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    private fun recordReportView(reportId: String) {
        val adminId = auth.currentUser?.uid ?: return

        val viewRecord = hashMapOf(
            "adminId" to adminId,
            "reportId" to reportId,
            "viewedAt" to Timestamp.now()
        )

        db.collection("viewReports")
            .add(viewRecord)
            .addOnSuccessListener {
                Log.d(TAG, "Report view recorded successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error recording report view", e)
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
        val adminReplySection = dialog.findViewById<View>(R.id.admin_reply_section)
        val adminReplyInput = dialog.findViewById<TextInputEditText>(R.id.admin_reply_input)
        val statusRadioGroup = dialog.findViewById<RadioGroup>(R.id.status_radio_group)
        val closeButton = dialog.findViewById<Button>(R.id.close_button)
        val respondButton = dialog.findViewById<Button>(R.id.respond_button)
        
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
            
            // Pre-fill the response field with existing response
            adminReplyInput.setText(report.adminResponse)
        } else {
            adminResponseSection.visibility = View.GONE
        }
        
        // Show admin reply section
        adminReplySection.visibility = View.VISIBLE
        
        // Set current status in radio group
        when (report.status) {
            "Pending" -> statusRadioGroup.check(R.id.status_pending)
            "Reviewed" -> statusRadioGroup.check(R.id.status_reviewed)
            "Resolved" -> statusRadioGroup.check(R.id.status_resolved)
        }
        
        // Show respond button for admin
        respondButton.visibility = View.VISIBLE
        
        // Set respond button action
        respondButton.setOnClickListener {
            val response = adminReplyInput.text.toString().trim()
            
            if (response.isEmpty()) {
                adminReplyInput.error = "Response cannot be empty"
                return@setOnClickListener
            }
            
            // Get selected status
            val selectedStatusId = statusRadioGroup.checkedRadioButtonId
            val selectedStatus = when (selectedStatusId) {
                R.id.status_pending -> "Pending"
                R.id.status_reviewed -> "Reviewed"
                R.id.status_resolved -> "Resolved"
                else -> report.status
            }
            
            // Update the report
            updateReport(report.id, response, selectedStatus)
            dialog.dismiss()
        }
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun updateReport(reportId: String, adminResponse: String, status: String) {
        progressBar.visibility = View.VISIBLE
        
        val updates = hashMapOf<String, Any>(
            "adminResponse" to adminResponse,
            "status" to status,
            "respondedAt" to Timestamp.now()
        )
        
        db.collection("reports").document(reportId)
            .update(updates)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Report updated successfully", Toast.LENGTH_SHORT).show()
                
                // Notify the user
                createNotificationForUser(reportId, status)
                
                // Reload reports
                loadReports()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Error updating report", e)
                Toast.makeText(this, "Error updating report: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun createNotificationForUser(reportId: String, status: String) {
        // First get the report to find the userId
        db.collection("reports").document(reportId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val userId = document.getString("userId")
                    val reportTitle = document.getString("title") ?: "your report"
                    
                    if (userId != null) {
                        val notificationMessage = when(status) {
                            "Reviewed" -> "Your report '$reportTitle' has been reviewed by an admin."
                            "Resolved" -> "Your report '$reportTitle' has been resolved."
                            else -> "There's an update to your report '$reportTitle'."
                        }
                        
                        val notification = hashMapOf(
                            "title" to "Report Status Update",
                            "message" to notificationMessage,
                            "isRead" to false,
                            "createdAt" to Timestamp.now(),
                            "isGlobal" to false,
                            "userId" to userId
                        )
                        
                        db.collection("notifications")
                            .add(notification)
                            .addOnSuccessListener {
                                Log.d(TAG, "User notification created successfully")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error creating user notification", e)
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting report details for notification", e)
            }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation item clicks
        when (item.itemId) {
            R.id.nav_user -> {
                startActivity(Intent(this, UserPage::class.java))
                finish()
            }
            R.id.nav_view_reports -> {
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
            R.id.nav_respond -> {
                startActivity(Intent(this, RespondToStudentsPage::class.java))
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