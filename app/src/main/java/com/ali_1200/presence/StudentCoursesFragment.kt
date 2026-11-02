package com.ali_1200.presence

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ali_1200.presence.databinding.FragmentListWithFabBinding
import kotlinx.coroutines.launch

// دانشجو: درس‌هایی که با کد کلاس اضافه کرده، همراه با وضعیت امروز و تعداد جلسات حاضر
class StudentCoursesFragment : Fragment(R.layout.fragment_list_with_fab) {
    private val vm: StudentViewModel by activityViewModels()
    private var _binding: FragmentListWithFabBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListWithFabBinding.bind(view)

        val adapter = StudentCourseAdapter(onDelete = { course ->
            confirmDialog(requireContext(), "حذف درس", "درس «${course.name}» از لیست شما حذف شود؟", "حذف") {
                vm.deleteCourse(course)
                showMessage("درس «${course.name}» حذف شد")
            }
        })
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.fab.text = "افزودن درس"
        binding.fab.setOnClickListener { showAddCourseDialog() }
        binding.txtEmptyTitle.text = "هنوز درسی اضافه نکرده‌اید"
        binding.txtEmptyBody.text = "کد کلاس را از استاد بگیرید و با دکمه پایین اضافه کنید"

        vm.courseItems.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun showAddCourseDialog() {
        showInputDialog(
            requireContext(),
            "افزودن درس",
            listOf(
                InputField("کد کلاس", latinCode = true, helper = "کد ۱۰ حرفی که استاد داده، مثل ABCDE-FGHJK"),
                InputField("نام درس (اختیاری)")
            ),
            "افزودن"
        ) { values ->
            viewLifecycleOwner.lifecycleScope.launch {
                val error = vm.joinCourse(values[0], values[1])
                showMessage(error ?: "درس اضافه شد")
            }
        }
    }
}
