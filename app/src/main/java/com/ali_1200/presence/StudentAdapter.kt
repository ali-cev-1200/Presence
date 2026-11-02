package com.ali_1200.presence

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ali_1200.presence.databinding.ItemPersonBinding

// لیست غایبین امروز (دانشجوهای لیست کلاس که هنوز حاضر نشده‌اند)
class StudentAdapter(
    private val onClick: (Student) -> Unit
) : ListAdapter<Student, StudentAdapter.ViewHolder>(StudentDiffCallback()) {

    class ViewHolder(val binding: ItemPersonBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = getItem(position)
        with(holder.binding) {
            txtAvatar.bindAvatar(student.name, student.studentID)
            txtName.text = student.name
            txtSubtitle.text = student.studentID
            progress.visibility = View.GONE
            txtBadge.bindBadge("غایب", R.color.error_container, R.color.error)
            root.setOnClickListener { onClick(student) }
        }
    }
}

class StudentDiffCallback : DiffUtil.ItemCallback<Student>() {
    override fun areItemsTheSame(oldItem: Student, newItem: Student): Boolean =
        oldItem.studentID == newItem.studentID

    override fun areContentsTheSame(oldItem: Student, newItem: Student): Boolean =
        oldItem == newItem
}
