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

class SignupPage : AppCompatActivity() {
    private val TAG = "SignupPage"
    
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var signupButton: Button
    private lateinit var loginText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_signup_page)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        signupButton = findViewById(R.id.signupButton)
        loginText = findViewById(R.id.loginText)
        progressBar = findViewById(R.id.progressBar)

        // Set click listeners
        signupButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()
            
            when {
                email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() -> {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
                password != confirmPassword -> {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
                !email.endsWith("@ue.edu.ph") -> {
                    // Show error for invalid email format
                    val shake = AnimationUtils.loadAnimation(this, R.anim.shake_animation)
                    emailInput.startAnimation(shake)
                    emailInput.error = "Email must end with @ue.edu.ph"
                }
                else -> {
                    // Show progress bar
                    progressBar.visibility = View.VISIBLE
                    signupButton.isEnabled = false
                    
                    // Create new user with Firebase Auth
                    UserSession.addUser(email, password) { success, message ->
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            signupButton.isEnabled = true
                            
                            if (success) {
                                // Show the generated username in a toast message
                                Toast.makeText(this, "Signup successful! Your username is $message", Toast.LENGTH_LONG).show()
                                
                                // Navigate to UserPage
                                val intent = Intent(this, UserPage::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                // Handle specific error cases
                                when {
                                    message.contains("email address is already in use") -> {
                                        Toast.makeText(this, "This email is already registered", Toast.LENGTH_SHORT).show()
                                    }
                                    message.contains("password") -> {
                                        Toast.makeText(this, "Password should be at least 6 characters", Toast.LENGTH_SHORT).show()
                                    }
                                    else -> {
                                        Toast.makeText(this, "Error creating account: $message", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                Log.e(TAG, "Signup error: $message")
                            }
                        }
                    }
                }
            }
        }

        loginText.setOnClickListener {
            // Navigate to login page
            val intent = Intent(this, LoginPage::class.java)
            startActivity(intent)
            finish()
        }
    }
}
