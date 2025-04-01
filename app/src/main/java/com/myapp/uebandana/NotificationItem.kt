package com.myapp.uebandana

import com.google.firebase.Timestamp

/**
 * Data class representing a notification item
 */
data class NotificationItem(
    val id: String,  // Changed from Int to String for Firestore document IDs
    val title: String,
    val message: String,
    val timeAgo: String,
    val iconResId: Int = R.drawable.ic_menu_placeholder, // Default icon
    val isRead: Boolean = false,  // Added for read/unread status
    val timestamp: Timestamp? = null  // Added for Firestore timestamp
)
