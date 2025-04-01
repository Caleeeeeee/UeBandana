package com.myapp.uebandana

import android.view.animation.AnimationUtils
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class LoginPage : AppCompatActivity() {
    private val TAG = "LoginPage"
    
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var signupText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_page)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        signupText = findViewById(R.id.signupText)
        progressBar = findViewById(R.id.progressBar)

        // Set click listeners
        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            } else if (!email.endsWith("@ue.edu.ph") && !email.equals("admin@ue.edu.ph", ignoreCase = true)) {
                // Show error and apply shake animation for invalid email format
                emailInput.error = "Email must end with @ue.edu.ph"
                val shake = AnimationUtils.loadAnimation(this, R.anim.shake_animation)
                emailInput.startAnimation(shake)
            } else {
                // Show progress bar
                progressBar.visibility = View.VISIBLE
                loginButton.isEnabled = false

                // Special case for admin login
                if (email.equals("admin@ue.edu.ph", ignoreCase = true) && password == "admin123") {
                    handleAdminLogin(email, password)
                } else {
                    // Regular user login
                    UserSession.signInWithEmailAndPassword(email, password) { success, errorMessage ->
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                                navigateToUserPage()
                            } else {
                                progressBar.visibility = View.GONE
                                loginButton.isEnabled = true
                                
                                // Show appropriate error message
                                if (errorMessage.contains("no user record") || 
                                    errorMessage.contains("email address")) {
                                    Toast.makeText(this, "User not found. Please sign up.", Toast.LENGTH_SHORT).show()
                                } else if (errorMessage.contains("password is invalid")) {
                                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                                    val shake = AnimationUtils.loadAnimation(this, R.anim.shake_animation)
                                    passwordInput.startAnimation(shake)
                                } else {
                                    Toast.makeText(this, "Login failed: $errorMessage", Toast.LENGTH_SHORT).show()
                                }
                                Log.e(TAG, "Login error: $errorMessage")
                            }
                        }
                    }
                }
            }
        }

        signupText.setOnClickListener {
            // Navigate to signup page
            val intent = Intent(this, SignupPage::class.java)
            startActivity(intent)
            finish()
        }
    }
    
    private fun handleAdminLogin(email: String, password: String) {
        UserSession.signInWithEmailAndPassword(email, password) { success, errorMessage ->
            if (success) {
                // User exists, check if they're admin
                if (UserSession.isAdmin()) {
                    runOnUiThread {
                        Toast.makeText(this, "Admin login successful", Toast.LENGTH_SHORT).show()
                        navigateToUserPage()
                    }
                } else {
                    // User exists but is not an admin
                    UserSession.logout()
                    
                    // Create admin user (overwriting existing one)
                    createAdminUser(email, password)
                }
            } else {
                // User doesn't exist, create admin account
                createAdminUser(email, password)
            }
        }
    }
    
    private fun createAdminUser(email: String, password: String) {
        UserSession.createAdminUser(email, password) { success, errorMessage ->
            runOnUiThread {
                progressBar.visibility = View.GONE
                loginButton.isEnabled = true
                
                if (success) {
                    Toast.makeText(this, "Admin login successful", Toast.LENGTH_SHORT).show()
                    navigateToUserPage()
                } else {
                    Toast.makeText(this, "Admin login failed: $errorMessage", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Admin creation error: $errorMessage")
                }
            }
        }
    }
    
    private fun navigateToUserPage() {
        progressBar.visibility = View.GONE
        loginButton.isEnabled = true
        
        val intent = Intent(this, UserPage::class.java)
        startActivity(intent)
        finish()
    }
}