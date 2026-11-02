package com.ali_1200.presence

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ali_1200.presence.databinding.ItemPersonBinding

// وضعیت حضور هر دانشجو در صفحه گزارش، با نوار درصد حضور
class ReportAdapter : ListAdapter<StudentStat, ReportAdapter.ViewHolder>(ReportDiffCallback()) {

    class ViewHolder(val binding: ItemPersonBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stat = getItem(position)
        val sessions = stat.presentCount + stat.absentCount
        val percent = if (sessions == 0) 0 else stat.presentCount * 100 / sessions

        with(holder.binding) {
            txtAvatar.bindAvatar(stat.name, stat.studentId)
            txtName.text = if (stat.inRoster) stat.name else "${stat.name} (خارج از لیست)"
            txtSubtitle.text = "${stat.studentId} • حضور ${stat.presentCount} از $sessions • غیبت ${stat.absentCount}"

            val (background, foreground) = when {
                stat.overLimit -> R.color.error_container to R.color.error
                stat.atLimit -> R.color.warning_container to R.color.warning
                else -> R.color.success_container to R.color.success
            }
            txtBadge.bindBadge("$percent٪", background, foreground)

            progress.visibility = View.VISIBLE
            progress.progress = percent
            progress.setIndicatorColor(ContextCompat.getColor(root.context, foreground))
            progress.trackColor = ContextCompat.getColor(root.context, background)
            root.isClickable = false
        }
    }
}

class ReportDiffCallback : DiffUtil.ItemCallback<StudentStat>() {
    override fun areItemsTheSame(oldItem: StudentStat, newItem: StudentStat): Boolean =
        oldItem.studentId == newItem.studentId

    override fun areContentsTheSame(oldItem: StudentStat, newItem: StudentStat): Boolean =
        oldItem == newItem
}
