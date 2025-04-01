package com.myapp.uebandana

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

enum class UserType {
    STUDENT,
    ADMIN
}

object UserSession {
    private val TAG = "UserSession"
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    
    var currentUserType = UserType.STUDENT
    var userName = ""
    
    fun isAdmin(): Boolean {
        return currentUserType == UserType.ADMIN
    }
    
    fun isStudent(): Boolean {
        return currentUserType == UserType.STUDENT
    }
    
    // Generate a random username in the format "bandanauser#XXXX"
    private fun generateUsername(): String {
        val random = Random.nextInt(1, 8001)
        return "bandanauser#$random"
    }
    
    fun addUser(email: String, password: String, callback: (Boolean, String) -> Unit) {
        Log.d(TAG, "Attempting to create user: $email")
        
        // Create user with email and password using Firebase Authentication
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "User creation successful for $email")
                    val user = auth.currentUser
                    
                    if (user == null) {
                        Log.e(TAG, "User created but user is null")
                        callback(false, "User created but user is null")
                        return@addOnCompleteListener
                    }
                    
                    // Generate random username for the user
                    val username = generateUsername()
                    userName = username
                    
                    // Create user document in Firestore with UID as document ID
                    val userData = hashMapOf(
                        "username" to username,
                        "email" to email,
                        "passwordHash" to password, // In production, don't store plain password
                        "role" to "User",
                        "createdAt" to Timestamp.now()
                    )
                    
                    Log.d(TAG, "Creating user document with UID: ${user.uid}")
                    db.collection("users").document(user.uid)
                        .set(userData)
                        .addOnSuccessListener {
                            Log.d(TAG, "User document created successfully")
                            callback(true, username)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error creating user document: ${e.message}", e)
                            callback(false, "Error creating user profile: ${e.message}")
                        }
                } else {
                    val exception = task.exception
                    Log.e(TAG, "User creation failed: ${exception?.message}", exception)
                    callback(false, exception?.message ?: "Unknown authentication error")
                }
            }
    }
    
    fun signInWithEmailAndPassword(email: String, password: String, callback: (Boolean, String) -> Unit) {
        Log.d(TAG, "Attempting to sign in: $email")
        
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        Log.d(TAG, "Sign in successful for: $email")
                        
                        // Get user data from Firestore using the UID
                        db.collection("users").document(user.uid)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document != null && document.exists()) {
                                    val username = document.getString("username") ?: ""
                                    userName = username
                                    
                                    // Set user type based on role
                                    val role = document.getString("role") ?: "User"
                                    currentUserType = if (role == "Admin") UserType.ADMIN else UserType.STUDENT
                                    
                                    Log.d(TAG, "User data retrieved, username: $username, role: $role")
                                    callback(true, "")
                                } else {
                                    Log.d(TAG, "No user document found, using default values")
                                    userName = email.substringBefore("@")
                                    currentUserType = UserType.STUDENT
                                    callback(true, "")
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error getting user document: ${e.message}", e)
                                userName = email.substringBefore("@")
                                currentUserType = UserType.STUDENT
                                callback(true, "")
                            }
                    } else {
                        Log.e(TAG, "Sign in successful but user is null")
                        callback(false, "Sign in successful but user is null")
                    }
                } else {
                    val exception = task.exception
                    Log.e(TAG, "Sign in failed: ${exception?.message}", exception)
                    callback(false, exception?.message ?: "Unknown authentication error")
                }
            }
    }
    
    fun logout() {
        auth.signOut()
        currentUserType = UserType.STUDENT
        userName = ""
        Log.d(TAG, "User signed out")
    }
    
    fun createAdminUser(email: String, password: String, callback: (Boolean, String) -> Unit) {
        Log.d(TAG, "Attempting to create admin user: $email")
        
        // Create user with email and password using Firebase Authentication
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Admin user creation successful for $email")
                    val user = auth.currentUser
                    
                    if (user == null) {
                        Log.e(TAG, "Admin user created but user is null")
                        callback(false, "Admin user created but user is null")
                        return@addOnCompleteListener
                    }
                    
                    // For admin, use "Admin" as username
                    val username = "Admin"
                    userName = username
                    currentUserType = UserType.ADMIN
                    
                    // Create admin user document in Firestore
                    val userData = hashMapOf(
                        "username" to username,
                        "email" to email,
                        "passwordHash" to password, // In production, don't store plain password
                        "role" to "Admin",
                        "createdAt" to Timestamp.now()
                    )
                    
                    Log.d(TAG, "Creating admin user document with UID: ${user.uid}")
                    db.collection("users").document(user.uid)
                        .set(userData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Admin user document created successfully")
                            callback(true, username)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error creating admin user document: ${e.message}", e)
                            callback(false, "Error creating admin profile: ${e.message}")
                        }
                } else {
                    val exception = task.exception
                    Log.e(TAG, "Admin user creation failed: ${exception?.message}", exception)
                    callback(false, exception?.message ?: "Unknown authentication error")
                }
            }
    }
}
