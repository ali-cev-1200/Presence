package com.ali_1200.presence

import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ali_1200.presence.databinding.ItemAttendanceBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.Handler

class AttendanceAdapter :
    ListAdapter<dateResponse, AttendanceAdapter.AttendanceViewHolder>(AttendanceDiffCallback()) {

    class AttendanceViewHolder(val binding: ItemAttendanceBinding) :
        RecyclerView.ViewHolder(binding.root)

    private val handler = android.os.Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            notifyDataSetChanged() // 🚀 لیست را هر دقیقه به‌روز کن
            handler.postDelayed(this, 60000) // ⏳ هر ۶۰ ثانیه یک بار اجرا شود
        }
    }

    init {
        handler.postDelayed(updateRunnable, 60000) // ⏳ شروع اجرای خودکار
    }

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
            txtTime.text = attendance.date.toString()
            txtAttendanceTime.text = getRelativeTime(attendance.timestamp)
//            txtAttendanceTime.text = getRelativeTime(attendance.timestamp)
        }
    }

    private fun getRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "لحظاتی پیش" // کمتر از ۱ دقیقه
            diff < 60 * 60_000 -> "${diff / 60_000} دقیقه پیش" // کمتر از ۱ ساعت
            diff < 24 * 60 * 60_000 -> "${diff / (60 * 60_000)} ساعت پیش" // کمتر از ۱ روز
            diff < 7 * 24 * 60 * 60_000 -> "${diff / (24 * 60 * 60_000)} روز پیش" // کمتر از ۱ هفته
            else -> "${diff / (24 * 60 * 60_000)} روز پیش" // بیشتر از ۱ هفته
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
