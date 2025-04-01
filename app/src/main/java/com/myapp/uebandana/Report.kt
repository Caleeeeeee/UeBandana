package com.myapp.uebandana

import com.google.firebase.Timestamp

data class Report(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "Pending",
    val adminResponse: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val respondedAt: Timestamp? = null
)
