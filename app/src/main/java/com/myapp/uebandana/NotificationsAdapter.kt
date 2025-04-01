package com.myapp.uebandana

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NotificationsAdapter(
    private val notifications: List<NotificationItem>,
    private val listener: OnNotificationClickListener,
    private val isAdmin: Boolean = false
) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {

    interface OnNotificationClickListener {
        fun onNotificationClick(notification: NotificationItem)
        fun onViewButtonClick(notification: NotificationItem)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.notification_title)
        val messageTextView: TextView = view.findViewById(R.id.notification_message)
        val timeTextView: TextView = view.findViewById(R.id.notification_time)
        val viewButton: Button = view.findViewById(R.id.notification_view_button)
        
        init {
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onNotificationClick(notifications[position])
                }
            }
            
            viewButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onViewButtonClick(notifications[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = notifications[position]
        
        // Set the title (use first part of message if title is empty)
        if (notification.title.isNotEmpty()) {
            holder.titleTextView.text = notification.title
        } else {
            // Use first 5 words of message as title
            val words = notification.message.split(" ")
            val title = words.take(5).joinToString(" ") + if (words.size > 5) "..." else ""
            holder.titleTextView.text = title
        }
        
        // Set other fields
        holder.messageTextView.text = notification.message
        holder.timeTextView.text = notification.timeAgo
        
        // Apply bold text style for unread notifications
        if (!notification.isRead) {
            holder.titleTextView.setTypeface(null, Typeface.BOLD)
            holder.messageTextView.setTypeface(null, Typeface.BOLD)
        } else {
            holder.titleTextView.setTypeface(null, Typeface.NORMAL)
            holder.messageTextView.setTypeface(null, Typeface.NORMAL)
        }
    }

    override fun getItemCount() = notifications.size
}
