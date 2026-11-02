package com.ali_1200.presence

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ali_1200.presence.databinding.FragmentTeacherSessionBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// استاد: شروع/توقف حضور و غیاب درس فعال و لیست حاضرین و غایبین امروز
class TeacherSessionFragment : Fragment(R.layout.fragment_teacher_session) {
    private val vm: TeacherViewModel by activityViewModels()
    private var _binding: FragmentTeacherSessionBinding? = null
    private val binding get() = _binding!!

    private lateinit var presentAdapter: AttendanceAdapter
    private lateinit var absentAdapter: StudentAdapter
    private var showingAbsent = false
    private var pulse: ValueAnimator? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) vm.startServer() else showMessage("بدون مجوز بلوتوث نمی‌شود حضور و غیاب کرد")
    }

    companion object {
        private const val REFRESH_INTERVAL: Long = 60_000 // به‌روزرسانی «... دقیقه پیش» هر دقیقه
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTeacherSessionBinding.bind(view)

        presentAdapter = AttendanceAdapter(onClick = ::confirmDeleteRecord)
        absentAdapter = StudentAdapter(onClick = ::confirmManualPresent)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = presentAdapter

        binding.toggleList.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showingAbsent = checkedId == R.id.btnAbsent
            binding.recycler.adapter = if (showingAbsent) absentAdapter else presentAdapter
            renderLists()
        }

        binding.btnChangeCourse.setOnClickListener { showCoursePicker(vm) }
        binding.btnCreateCourse.setOnClickListener { showCreateCourse(vm) }
        binding.btnCopyCode.setOnClickListener { vm.selectedCourse.value?.let(::copyClassCode) }
        binding.txtClassCode.setOnClickListener { vm.selectedCourse.value?.let(::showClassCodeDialog) }
        binding.btnToggle.setOnClickListener {
            if (vm.server.isActive) vm.stopServer() else requestPermissionsAndStart()
        }

        vm.selectedCourse.observe(viewLifecycleOwner) { course ->
            binding.noCourseState.visibility = if (course == null) View.VISIBLE else View.GONE
            binding.content.visibility = if (course == null) View.GONE else View.VISIBLE
            binding.txtCourseName.text = course?.name.orEmpty()
            binding.txtClassCode.text = course?.let { AttendanceCrypto.formatClassCode(it.classCode) }.orEmpty()
        }
        vm.server.state.observe(viewLifecycleOwner, ::renderServerState)
        vm.todayRecords.observe(viewLifecycleOwner) {
            presentAdapter.submitList(it)
            renderLists()
        }
        vm.absentToday.observe(viewLifecycleOwner) {
            absentAdapter.submitList(it)
            renderLists()
        }
        vm.roster.observe(viewLifecycleOwner) { renderLists() }
        vm.rosterIds.observe(viewLifecycleOwner) { presentAdapter.rosterIds = it }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    vm.refreshDay()
                    delay(REFRESH_INTERVAL)
                    presentAdapter.notifyItemRangeChanged(0, presentAdapter.itemCount)
                }
            }
        }
    }

    override fun onDestroyView() {
        pulse?.cancel()
        pulse = null
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        _binding = null
        super.onDestroyView()
    }

    private fun requestPermissionsAndStart() {
        val missing = PermissionUtils.checkPermissions(
            requireContext(), PermissionUtils.getRequiredPermissions(isAdvertiser = true)
        )
        if (missing.isEmpty()) vm.startServer() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun renderServerState(state: AttendanceServer.State) {
        val running = state is AttendanceServer.State.Running
        val context = requireContext()

        binding.txtStatus.text = when (state) {
            is AttendanceServer.State.Running -> "در حال دریافت حضور… دانشجوها می‌توانند ثبت کنند"
            AttendanceServer.State.Starting -> "در حال روشن کردن بلوتوث…"
            is AttendanceServer.State.Failed -> state.message
            AttendanceServer.State.Stopped -> "حضور و غیاب متوقف است"
        }
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, if (running) R.color.success_container else R.color.white)
        )
        binding.statusDot.alpha = if (running) 1f else 0.7f

        if (running && pulse == null) {
            pulse = binding.statusPulse.startPulse()
        } else if (!running) {
            binding.statusPulse.stopPulse(pulse)
            pulse = null
        }

        binding.btnToggle.isEnabled = state != AttendanceServer.State.Starting
        if (running) {
            binding.btnToggle.text = "توقف حضور و غیاب"
            binding.btnToggle.setIconResource(R.drawable.ic_stop)
            binding.btnToggle.setTextColor(ContextCompat.getColor(context, R.color.error))
            binding.btnToggle.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.error))
        } else {
            binding.btnToggle.text = "شروع حضور و غیاب"
            binding.btnToggle.setIconResource(R.drawable.ic_play)
            binding.btnToggle.setTextColor(ContextCompat.getColor(context, R.color.primary))
            binding.btnToggle.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary))
        }

        // تا وقتی حضور و غیاب روشن است صفحه خاموش نشود
        if (running) {
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (state is AttendanceServer.State.Failed) showMessage(state.message)
    }

    // آمار بالای صفحه، راهنمای لیست و حالت لیست خالی
    private fun renderLists() {
        val present = vm.todayRecords.value.orEmpty()
        val absent = vm.absentToday.value.orEmpty()
        val roster = vm.roster.value.orEmpty()

        binding.txtPresentCount.text = present.size.toString()
        binding.txtAbsentCount.text = if (roster.isEmpty()) "–" else absent.size.toString()
        binding.txtTotalCount.text = if (roster.isEmpty()) "–" else roster.size.toString()

        binding.txtListHint.text = if (showingAbsent) {
            "برای ثبت دستی حضور (مثلاً گوشی خاموش)، روی هر نفر بزنید"
        } else {
            "برای حذف حضور اشتباه، روی هر نفر بزنید"
        }

        val empty: Pair<String, Boolean>? = when {
            showingAbsent && roster.isEmpty() ->
                "برای دیدن غایبین، لیست کلاس را از تب «درس‌ها» وارد کنید" to true
            showingAbsent && absent.isEmpty() -> "همه حاضرند 🎉" to false
            !showingAbsent && present.isEmpty() -> "هنوز کسی امروز حاضر نشده است" to false
            else -> null
        }
        binding.emptyState.visibility = if (empty == null) View.GONE else View.VISIBLE
        binding.recycler.visibility = if (empty == null) View.VISIBLE else View.GONE
        binding.txtListHint.visibility = if (empty == null) View.VISIBLE else View.GONE
        binding.imgEmpty.setImageResource(if (showingAbsent && absent.isEmpty() && roster.isNotEmpty()) R.drawable.ic_check_circle else R.drawable.ic_people)
        binding.txtEmpty.text = empty?.first.orEmpty()

        binding.btnEmptyAction.visibility = if (empty?.second == true) View.VISIBLE else View.GONE
        binding.btnEmptyAction.text = "رفتن به درس‌ها"
        binding.btnEmptyAction.setOnClickListener { (activity as? MainActivity)?.selectTab(R.id.nav_courses) }
    }

    private fun confirmDeleteRecord(record: dateResponse) {
        confirmDialog(requireContext(), "حذف حضور", "حضور امروز «${record.name}» حذف شود؟", "حذف") {
            vm.deleteRecord(record)
            showMessage("حضور «${record.name}» حذف شد")
        }
    }

    private fun confirmManualPresent(student: Student) {
        confirmDialog(requireContext(), "ثبت دستی حضور", "«${student.name}» حاضر ثبت شود؟", "ثبت") {
            vm.markPresentManually(student)
            showMessage("«${student.name}» حاضر ثبت شد")
        }
    }
}
