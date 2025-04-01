package com.myapp.uebandana

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class AdminReportsAdapter(
    private val reports: List<Report>,
    private val onItemClick: (Report) -> Unit
) : RecyclerView.Adapter<AdminReportsAdapter.ReportViewHolder>() {

    class ReportViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.report_card)
        val titleView: TextView = view.findViewById(R.id.report_title)
        val userNameView: TextView = view.findViewById(R.id.report_username)
        val dateView: TextView = view.findViewById(R.id.report_date)
        val statusView: TextView = view.findViewById(R.id.report_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        
        holder.titleView.text = report.title
        holder.userNameView.text = report.userName
        
        // Format date
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val dateString = dateFormat.format(report.createdAt.toDate())
        holder.dateView.text = dateString
        
        // Set status and color
        holder.statusView.text = report.status
        when (report.status) {
            "Pending" -> holder.statusView.setTextColor(Color.parseColor("#FF9800")) // Orange
            "Reviewed" -> holder.statusView.setTextColor(Color.parseColor("#2196F3")) // Blue
            "Resolved" -> holder.statusView.setTextColor(Color.parseColor("#4CAF50")) // Green
        }
        
        // Set click listener
        holder.cardView.setOnClickListener {
            onItemClick(report)
        }
    }

    override fun getItemCount() = reports.size
}
