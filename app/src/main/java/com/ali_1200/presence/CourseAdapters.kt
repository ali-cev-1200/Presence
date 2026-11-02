package com.ali_1200.presence

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ali_1200.presence.databinding.ItemCourseStudentBinding
import com.ali_1200.presence.databinding.ItemCourseTeacherBinding

// کارت‌های درس استاد: انتخاب، کپی و نمایش کد، لیست کلاس و حذف
class TeacherCourseAdapter(
    private val onSelect: (Course) -> Unit,
    private val onCopyCode: (Course) -> Unit,
    private val onShowCode: (Course) -> Unit,
    private val onImportRoster: (Course) -> Unit,
    private val onDelete: (Course) -> Unit
) : ListAdapter<TeacherViewModel.CourseSummary, TeacherCourseAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<TeacherViewModel.CourseSummary>() {
        override fun areItemsTheSame(a: TeacherViewModel.CourseSummary, b: TeacherViewModel.CourseSummary) =
            a.course.id == b.course.id

        override fun areContentsTheSame(a: TeacherViewModel.CourseSummary, b: TeacherViewModel.CourseSummary) =
            a == b
    }
) {
    class ViewHolder(val binding: ItemCourseTeacherBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemCourseTeacherBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val course = item.course
        with(holder.binding) {
            txtCourseName.text = course.name
            txtCourseStats.text = "${item.students} دانشجو • ${item.sessions} جلسه"
            txtClassCode.text = AttendanceCrypto.formatClassCode(course.classCode)
            txtSelected.visibility = if (item.selected) View.VISIBLE else View.GONE
            card.strokeWidth = if (item.selected) (2 * root.resources.displayMetrics.density).toInt() else 0

            card.setOnClickListener { onSelect(course) }
            btnCopy.setOnClickListener { onCopyCode(course) }
            btnShowCode.setOnClickListener { onShowCode(course) }
            btnRoster.setOnClickListener { onImportRoster(course) }
            btnDelete.setOnClickListener { onDelete(course) }
        }
    }
}

// کارت‌های درس دانشجو با وضعیت امروز و تعداد جلسات حاضر
class StudentCourseAdapter(
    private val onOpen: (MyCourseItem) -> Unit,
    private val onDelete: (Course) -> Unit
) : ListAdapter<MyCourseItem, StudentCourseAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<MyCourseItem>() {
        override fun areItemsTheSame(a: MyCourseItem, b: MyCourseItem) = a.course.id == b.course.id
        override fun areContentsTheSame(a: MyCourseItem, b: MyCourseItem) = a == b
    }
) {
    class ViewHolder(val binding: ItemCourseStudentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemCourseStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            txtCourseName.text = item.course.name
            txtSessions.text = "حاضر در ${item.attendedSessions} جلسه • لمس برای سابقه"
            val teacherKey = item.course.teacherKey
            if (teacherKey == null) {
                txtTeacherKeyLabel.text = "امضای استاد در اولین حضور ذخیره می‌شود"
                txtTeacherKey.visibility = View.GONE
            } else {
                txtTeacherKeyLabel.text = "امضای استاد:"
                txtTeacherKey.text = TeacherSignature.fingerprint(teacherKey)
                txtTeacherKey.visibility = View.VISIBLE
            }
            if (item.registeredToday) {
                txtStatus.bindBadge("امروز ثبت شد ✓", R.color.success_container, R.color.success)
            } else {
                txtStatus.bindBadge("امروز ثبت نشده", R.color.surface_variant, R.color.on_surface_variant)
            }
            btnDelete.setOnClickListener { onDelete(item.course) }
            root.setOnClickListener { onOpen(item) }
        }
    }
}
