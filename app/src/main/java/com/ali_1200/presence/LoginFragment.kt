package com.ali_1200.presence

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ali_1200.presence.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

// صفحه ورود: انتخاب نقش (دانشجو/استاد) و مشخصات
class LoginFragment : Fragment(R.layout.fragment_login) {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val isTeacher: Boolean get() = binding.toggleRole.checkedButtonId == R.id.btnRoleTeacher

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)

        binding.toggleRole.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) renderRole()
        }
        // کد کلاس همیشه با حروف بزرگ نمایش داده می‌شود
        binding.editClassCode.filters = arrayOf(InputFilter.AllCaps())
        renderRole()
        binding.btnLogin.setOnClickListener { submit() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // استاد کد دانشجویی ندارد و کد کلاس هر درس را خود برنامه برایش می‌سازد
    private fun renderRole() {
        val studentFields = if (isTeacher) View.GONE else View.VISIBLE
        binding.layoutStudentId.visibility = studentFields
        binding.layoutClassCode.visibility = studentFields
        binding.layoutCourseName.hint = if (isTeacher) "نام اولین درس" else "نام درس (اختیاری)"
        binding.layoutCourseName.helperText = if (isTeacher) "درس‌های دیگر را بعداً اضافه می‌کنید" else null
        binding.btnLogin.text = if (isTeacher) "ورود به‌عنوان استاد" else "ورود به‌عنوان دانشجو"
    }

    @SuppressLint("HardwareIds")
    private fun submit() {
        listOf(binding.layoutName, binding.layoutStudentId, binding.layoutClassCode).forEach { it.error = null }

        val teacher = isTeacher
        val name = binding.editName.text.toString().trim()
        // کد دانشجویی با اعداد فارسی هم پذیرفته و به لاتین تبدیل می‌شود تا با لیست کلاس جور شود
        val studentId = PersianDate.normalizeDigits(binding.editStudentId.text.toString().trim())
        val classCode = AttendanceCrypto.normalizeClassCode(binding.editClassCode.text.toString())

        var valid = true
        if (name.isBlank()) {
            binding.layoutName.error = "نام را وارد کنید"
            valid = false
        }
        if (!teacher && (studentId.isBlank() || studentId.any { !it.isDigit() })) {
            binding.layoutStudentId.error = "کد دانشجویی فقط باید عدد باشد"
            valid = false
        }
        if (!teacher && classCode == null) {
            binding.layoutClassCode.error = "کد کلاس باید ${AttendanceCrypto.CODE_LENGTH} حرف و عدد باشد"
            valid = false
        }
        if (!valid) return

        val androidId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        val courseName = binding.editCourseName.text.toString().trim().ifBlank { "درس ۱" }
        val loginRepository = LoginRepository(requireContext())
        val courseRepository = CourseRepository(requireContext())

        binding.btnLogin.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            loginRepository.insertdata(
                Logingdata(1, if (teacher) "" else studentId, name, androidId, isTeacher = teacher)
            )
            if (teacher) {
                courseRepository.selectedCourseId = courseRepository.createCourse(courseName)
            } else {
                courseRepository.joinCourse(courseName, classCode!!)
            }
            (activity as? MainActivity)?.showHome(teacher)
        }
    }
}
