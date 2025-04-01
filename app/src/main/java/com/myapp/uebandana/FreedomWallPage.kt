package com.myapp.uebandana

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FreedomWallPage : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "FreedomWallPage"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    
    // Freedom Wall UI components
    private lateinit var recyclerView: RecyclerView
    private lateinit var newPostButton: FloatingActionButton
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    
    // Adapter for posts
    private lateinit var postsAdapter: PostsAdapter
    private val postsList = mutableListOf<Post>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_freedom_wall_page)

        // Initialize UI components
        recyclerView = findViewById(R.id.freedom_wall_recycler_view)
        newPostButton = findViewById(R.id.new_post_button)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        progressBar = findViewById(R.id.progress_bar)
        
        // Set up RecyclerView
        setupRecyclerView()
        
        // Set up the new post button
        setupNewPostButton()
        
        // Set up swipe refresh
        setupSwipeRefresh()
        
        // Load posts initially
        loadPosts()

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
        navigationView.setCheckedItem(R.id.nav_freedom_wall)
        
        // Update navigation header with user name
        val headerView = navigationView.getHeaderView(0)
        val navUsername: TextView = headerView.findViewById(R.id.nav_header_username)
        navUsername.text = "Welcome, ${UserSession.userName}"
    }
    
    private fun setupRecyclerView() {
        // Initialize adapter
        postsAdapter = PostsAdapter(postsList)
        
        // Set up RecyclerView with Linear Layout
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@FreedomWallPage)
            adapter = postsAdapter
        }
    }
    
    private fun setupNewPostButton() {
        newPostButton.setOnClickListener {
            showNewPostDialog()
        }
    }
    
    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            // Clear the current list and reload
            postsList.clear()
            postsAdapter.notifyDataSetChanged()
            loadPosts()
        }
    }
    
    private fun showNewPostDialog() {
        // Create and configure bottom sheet dialog
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_new_freedom_wall_post, null)
        bottomSheetDialog.setContentView(view)
        
        // Get references to the dialog views
        val contentEditText = view.findViewById<EditText>(R.id.post_content)
        val anonymousCheckBox = view.findViewById<CheckBox>(R.id.anonymous_checkbox)
        val postButton = view.findViewById<Button>(R.id.post_button)
        val cancelButton = view.findViewById<Button>(R.id.cancel_button)
        val dialogProgressBar = view.findViewById<ProgressBar>(R.id.dialog_progress_bar)
        
        // Set up dialog buttons
        postButton.setOnClickListener {
            val content = contentEditText.text.toString().trim()
            val isAnonymous = anonymousCheckBox.isChecked
            
            if (content.isEmpty()) {
                contentEditText.error = "Please write something"
                return@setOnClickListener
            }
            
            // Show progress
            dialogProgressBar.visibility = View.VISIBLE
            postButton.isEnabled = false
            cancelButton.isEnabled = false
            
            // Save post to Firestore
            savePost(content, isAnonymous, onSuccess = {
                dialogProgressBar.visibility = View.GONE
                bottomSheetDialog.dismiss()
                // Refresh the posts list
                postsList.clear()
                postsAdapter.notifyDataSetChanged()
                loadPosts()
            }, onFailure = { errorMessage ->
                dialogProgressBar.visibility = View.GONE
                postButton.isEnabled = true
                cancelButton.isEnabled = true
                Toast.makeText(this, "Error posting: $errorMessage", Toast.LENGTH_SHORT).show()
            })
        }
        
        cancelButton.setOnClickListener {
            bottomSheetDialog.dismiss()
        }
        
        // Show the dialog
        bottomSheetDialog.show()
    }
    
    private fun savePost(content: String, isAnonymous: Boolean, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val currentUser = auth.currentUser
        
        // Prepare post data
        val post = hashMapOf(
            "userId" to if (isAnonymous) null else currentUser?.uid,
            "userName" to if (isAnonymous) "Anonymous" else UserSession.userName,
            "content" to content,
            "createdAt" to Timestamp.now()
        )
        
        // Save to Firestore
        db.collection("freedomWall")
            .add(post)
            .addOnSuccessListener {
                Log.d(TAG, "Post added with ID: ${it.id}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding post", e)
                onFailure(e.message ?: "Unknown error")
            }
    }
    
    private fun loadPosts() {
        progressBar.visibility = View.VISIBLE
        
        // Query Firestore for freedom wall posts, ordered by creation time (newest first)
        db.collection("freedomWall")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                
                // Clear existing list and add new posts
                postsList.clear()
                
                for (document in documents) {
                    try {
                        val userId = document.getString("userId")
                        val userName = document.getString("userName") ?: "Anonymous"
                        val content = document.getString("content") ?: ""
                        val createdAt = document.getTimestamp("createdAt") ?: Timestamp.now()
                        
                        val post = Post(
                            id = document.id,
                            userId = userId,
                            userName = userName,
                            content = content,
                            createdAt = createdAt
                        )
                        
                        postsList.add(post)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing post: ${document.id}", e)
                    }
                }
                
                // Update the adapter
                postsAdapter.notifyDataSetChanged()
                
                // Show empty view if no posts
                if (postsList.isEmpty()) {
                    findViewById<TextView>(R.id.empty_view).visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    findViewById<TextView>(R.id.empty_view).visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                Log.e(TAG, "Error loading posts", e)
                Toast.makeText(this, "Error loading posts: ${e.message}", Toast.LENGTH_SHORT).show()
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
            R.id.nav_view_reports -> {
                startActivity(Intent(this, AdminReportsPage::class.java))
                finish()
            }
            R.id.nav_counsellors -> {
                startActivity(Intent(this, GuidanceCounsellorsPage::class.java))
                finish()
            }
            R.id.nav_freedom_wall -> {
                // Already on this page
                return true
            }
            R.id.nav_diary -> {
                startActivity(Intent(this, DiaryPage::class.java))
                finish()
            }
            R.id.nav_respond -> {
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
    
    // Data class for posts
    data class Post(
        val id: String,
        val userId: String?,
        val userName: String,
        val content: String,
        val createdAt: Timestamp
    )
    
    // Adapter for the RecyclerView
    inner class PostsAdapter(private val posts: List<Post>) : 
            RecyclerView.Adapter<PostsAdapter.PostViewHolder>() {
        
        inner class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val userNameText: TextView = view.findViewById(R.id.post_username)
            val contentText: TextView = view.findViewById(R.id.post_content)
            val dateText: TextView = view.findViewById(R.id.post_date)
            val avatarImage: ImageView = view.findViewById(R.id.post_avatar)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_freedom_wall_post, parent, false)
            return PostViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
            val post = posts[position]
            
            // Set user name
            holder.userNameText.text = post.userName
            
            // Set content
            holder.contentText.text = post.content
            
            // Format and set date
            val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            val date = Date(post.createdAt.seconds * 1000)
            holder.dateText.text = dateFormat.format(date)
            
            // Set avatar icon based on anonymous or identified post
            if (post.userId == null) {
                holder.avatarImage.setImageResource(R.drawable.ic_anonymous_avatar)
            } else {
                holder.avatarImage.setImageResource(R.drawable.ic_user_avatar)
            }
        }
        
        override fun getItemCount() = posts.size
    }
}