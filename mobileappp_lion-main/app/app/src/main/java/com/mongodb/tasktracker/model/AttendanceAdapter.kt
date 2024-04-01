package com.mongodb.tasktracker.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mongodb.tasktracker.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class AttendanceAdapter(private var attendances: List<AttendanceInfo>) :
    RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    class AttendanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateTextView: TextView = view.findViewById(R.id.date_data)
        private val statusTextView: TextView = view.findViewById(R.id.Status_data)

        fun bind(attendance: AttendanceInfo) {
            val formattedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(attendance.date))
            dateTextView.text = formattedDate
            statusTextView.text = attendance.status
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(attendances[position])
    }

    override fun getItemCount(): Int = attendances.size

    fun updateAttendances(newAttendances: List<AttendanceInfo>) {
        attendances = newAttendances
        notifyDataSetChanged()
    }
}

