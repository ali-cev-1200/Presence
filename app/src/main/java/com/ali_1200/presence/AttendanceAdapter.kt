package com.ali_1200.presence

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ali_1200.presence.databinding.ItemPersonBinding

// لیست حاضرین امروز؛ به‌روزرسانی دقیقه‌ای «... دقیقه پیش» در TeacherSessionFragment انجام می‌شود
class AttendanceAdapter(
    private val onClick: (dateResponse) -> Unit
) : ListAdapter<dateResponse, AttendanceAdapter.ViewHolder>(AttendanceDiffCallback()) {

    class ViewHolder(val binding: ItemPersonBinding) : RecyclerView.ViewHolder(binding.root)

    // کد دانشجوهای لیست کلاس؛ اگر خالی باشد علامت «خارج از لیست» نمایش داده نمی‌شود
    var rosterIds: Set<String> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        with(holder.binding) {
            txtAvatar.bindAvatar(record.name, record.studentID)
            txtName.text = record.name

            val details = buildList {
                add(record.studentID)
                add(relativeTime(record.timestamp))
                if (ResponseRepository.isManual(record)) add("ثبت دستی")
                record.rssi?.let { add(signalLabel(it)) }
                if (rosterIds.isNotEmpty() && record.studentID !in rosterIds) add("خارج از لیست")
            }
            txtSubtitle.text = details.joinToString(" • ")
            progress.visibility = View.GONE

            txtBadge.bindBadge(PersianDate.formatTime(record.timestamp), R.color.success_container, R.color.success)
            root.setOnClickListener { onClick(record) }
        }
    }
}

// ✅ مقایسه‌ی داده‌های جدید و قدیمی برای بهینه‌سازی لیست
class AttendanceDiffCallback : DiffUtil.ItemCallback<dateResponse>() {
    override fun areItemsTheSame(oldItem: dateResponse, newItem: dateResponse): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: dateResponse, newItem: dateResponse): Boolean {
        return oldItem == newItem
    }
}
