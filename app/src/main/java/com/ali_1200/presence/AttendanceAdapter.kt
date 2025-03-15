package com.ali_1200.presence

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ali_1200.presence.databinding.ItemAttendanceBinding

class AttendanceAdapter :
    ListAdapter<dateResponse, AttendanceAdapter.AttendanceViewHolder>(AttendanceDiffCallback()) {

    class AttendanceViewHolder(val binding: ItemAttendanceBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val binding = ItemAttendanceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AttendanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val attendance = getItem(position)
        with(holder.binding) {
            txtStudentName.text = attendance.name
            txtStudentId.text = "کد دانشجویی: ${attendance.studentID}"
//            txtAttendanceTime.text = getRelativeTime(attendance.timestamp)
        }
    }
}

// ✅ مقایسه‌ی داده‌های جدید و قدیمی برای بهینه‌سازی لیست
class AttendanceDiffCallback : DiffUtil.ItemCallback<dateResponse>() {
    override fun areItemsTheSame(oldItem: dateResponse, newItem: dateResponse): Boolean {
        return oldItem.studentID == newItem.studentID
    }

    override fun areContentsTheSame(oldItem: dateResponse, newItem: dateResponse): Boolean {
        return oldItem == newItem
    }
}
