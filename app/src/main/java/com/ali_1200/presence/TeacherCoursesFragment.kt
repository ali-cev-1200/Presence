package com.ali_1200.presence

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ali_1200.presence.databinding.FragmentListWithFabBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// استاد: لیست درس‌ها؛ انتخاب درس فعال، کد کلاس، وارد کردن لیست کلاس و حذف
class TeacherCoursesFragment : Fragment(R.layout.fragment_list_with_fab) {
    private val vm: TeacherViewModel by activityViewModels()
    private var _binding: FragmentListWithFabBinding? = null
    private val binding get() = _binding!!

    // درسی که فایل لیست کلاسش در حال انتخاب است
    private var importTarget: Course? = null

    private val importRosterLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importTarget?.let { importRoster(it, uri) } }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListWithFabBinding.bind(view)

        val adapter = TeacherCourseAdapter(
            onSelect = { course ->
                vm.selectCourse(course)
                showMessage("«${course.name}» درس فعال شد")
            },
            onCopyCode = ::copyClassCode,
            onShowCode = ::showClassCodeDialog,
            onImportRoster = { course ->
                importTarget = course
                // نوع فایل CSV در گوشی‌های مختلف فرق می‌کند؛ Excel هم مستقیم پذیرفته می‌شود
                importRosterLauncher.launch(
                    arrayOf(
                        XlsxWriter.MIME_TYPE, "text/*", "application/csv", "application/vnd.ms-excel",
                        "application/octet-stream"
                    )
                )
            },
            onDelete = { confirmDeleteCourse(vm, it) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.fab.text = "درس جدید"
        binding.fab.setOnClickListener { showCreateCourse(vm) }
        binding.txtEmptyTitle.text = "هنوز درسی نساخته‌اید"
        binding.txtEmptyBody.text = "برای هر درس یک کد کلاس ساخته می‌شود که به دانشجوها می‌دهید"

        vm.courseSummaries.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        // با پایین رفتن لیست، دکمه شناور کوچک می‌شود
        binding.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.fab.shrink() else if (dy < 0) binding.fab.extend()
            }
        })
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun importRoster(course: Course, uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val students = withContext(Dispatchers.IO) {
                runCatching {
                    requireContext().contentResolver.openInputStream(uri)?.use { RosterParser.parse(it.readBytes()) }
                }.getOrNull()
            }

            if (students.isNullOrEmpty()) {
                confirmDialog(
                    requireContext(),
                    "دانشجویی پیدا نشد",
                    "فایل باید Excel (.xlsx) یا CSV با دو ستون کد دانشجویی و نام باشد. از فایل Excel، اولین برگه خوانده می‌شود.",
                    "باشه"
                ) {}
                return@launch
            }

            val current = vm.courseSummaries.value.orEmpty().firstOrNull { it.course.id == course.id }?.students ?: 0
            val message = if (current == 0) {
                "${students.size} دانشجو در فایل پیدا شد. برای درس «${course.name}» ذخیره شود؟"
            } else {
                "${students.size} دانشجو در فایل پیدا شد. جایگزین لیست فعلی درس «${course.name}» ($current نفر) شود؟"
            }
            confirmDialog(requireContext(), "لیست کلاس", message, "ذخیره") {
                vm.replaceRoster(course.id, students)
                showMessage("لیست ${students.size} نفره ذخیره شد")
            }
        }
    }
}
