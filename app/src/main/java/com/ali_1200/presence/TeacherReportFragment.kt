package com.ali_1200.presence

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ali_1200.presence.databinding.FragmentTeacherReportBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// استاد: آمار حضور درس فعال و خروجی Excel
class TeacherReportFragment : Fragment(R.layout.fragment_teacher_report) {
    private val vm: TeacherViewModel by activityViewModels()
    private var _binding: FragmentTeacherReportBinding? = null
    private val binding get() = _binding!!

    private var exportTarget: Course? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(XlsxWriter.MIME_TYPE)
    ) { uri -> if (uri != null) exportTarget?.let { export(it, uri) } }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTeacherReportBinding.bind(view)

        val adapter = ReportAdapter()
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        vm.selectedCourse.observe(viewLifecycleOwner) { course ->
            binding.txtCourseName.text = course?.name ?: "درسی انتخاب نشده"
            binding.fabExport.isEnabled = course != null
        }

        vm.stats.observe(viewLifecycleOwner) { stats ->
            // دانشجوهای در خطر بالای لیست
            adapter.submitList(stats.students.sortedWith(compareByDescending<StudentStat> { it.absentCount }.thenBy { it.name }))
            binding.txtSessions.text = stats.sessions.size.toString()
            binding.txtAverage.text = "${stats.averageAttendancePercent}٪"
            binding.txtAtRisk.text = stats.atRiskCount.toString()
            binding.txtRange.text = if (stats.sessions.isEmpty()) {
                "هنوز جلسه‌ای ثبت نشده"
            } else {
                "${stats.sessions.size} جلسه، از ${PersianDate.toPersianDigits(stats.sessions.first())} تا ${PersianDate.toPersianDigits(stats.sessions.last())}"
            }
            binding.txtEmpty.visibility = if (stats.students.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabExport.setOnClickListener {
            val course = vm.selectedCourse.value ?: return@setOnClickListener
            exportTarget = course
            val today = PersianDate.fromTimestamp(System.currentTimeMillis()).toString().replace('/', '-')
            val courseName = course.name.replace(Regex("""[\\/:*?"<>|]"""), "-")
            exportLauncher.launch("حضور-$courseName-$today.xlsx")
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun export(course: Course, uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val rows = vm.exportRows(course)
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    requireContext().contentResolver.openOutputStream(uri)?.use {
                        XlsxWriter.write(it, course.name, rows)
                    } != null
                }.getOrDefault(false)
            }
            showMessage(if (saved) "فایل Excel ذخیره شد" else "ذخیره فایل ناموفق بود")
        }
    }
}
