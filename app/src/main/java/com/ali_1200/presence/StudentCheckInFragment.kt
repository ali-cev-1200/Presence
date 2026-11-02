package com.ali_1200.presence

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ali_1200.presence.databinding.FragmentStudentCheckInBinding
import com.google.android.material.chip.Chip
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

// دانشجو: دکمه بزرگ ثبت حضور، وضعیت تلاش فعلی و وضعیت امروز هر درس
class StudentCheckInFragment : Fragment(R.layout.fragment_student_check_in) {
    private val vm: StudentViewModel by activityViewModels()
    private var _binding: FragmentStudentCheckInBinding? = null
    private val binding get() = _binding!!

    private var pulse: ValueAnimator? = null
    private lateinit var biometricPrompt: BiometricPrompt

    // اسکنر QR (کتابخانه zxing، کاملاً آفلاین)؛ مجوز دوربین را خودش می‌گیرد
    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents
        when {
            text == null -> renderStatus(
                "اسکن QR لغو شد", "برای ثبت حضور دوباره دکمه را بزنید",
                R.drawable.ic_info, R.color.warning_container, R.color.warning
            )
            !RotatingQr.isValidFormat(text) -> renderStatus(
                "QR اشتباه است", "QR حضور و غیاب روی گوشی استاد را اسکن کنید",
                R.drawable.ic_warning, R.color.error_container, R.color.error
            )
            // هویت چند ثانیه پیش تایید شده؛ دوباره اثر انگشت خواسته نمی‌شود
            else -> vm.startCheckIn(qrCode = text)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) authenticateThenStart() else showMessage("بدون مجوز بلوتوث نمی‌شود حضور ثبت کرد")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStudentCheckInBinding.bind(view)
        biometricPrompt = createBiometricPrompt()

        vm.login.observe(viewLifecycleOwner) { login ->
            if (login == null) return@observe
            binding.txtAvatar.text = login.name.trim().firstOrNull()?.toString() ?: "؟"
            binding.txtHello.text = "سلام، ${login.name}"
            val today = PersianDate.fromTimestamp(System.currentTimeMillis()).toLongText()
            binding.txtInfo.text = "کد دانشجویی ${login.studentID} • $today"
        }

        vm.courseItems.observe(viewLifecycleOwner, ::renderTodayChips)
        vm.checkInState.observe(viewLifecycleOwner, ::renderState)

        binding.btnCheckIn.setOnClickListener { onCheckInPressed() }
    }

    override fun onDestroyView() {
        pulse?.cancel()
        pulse = null
        _binding = null
        super.onDestroyView()
    }

    private fun onCheckInPressed() {
        if (vm.client.isBusy) return
        vm.checkInBlocker()?.let { message ->
            renderStatus("کاری برای انجام نیست", message, R.drawable.ic_info, R.color.primary_container, R.color.primary)
            return
        }

        val missing = PermissionUtils.checkPermissions(requireContext(), PermissionUtils.getRequiredPermissions())
        if (missing.isEmpty()) authenticateThenStart() else permissionLauncher.launch(missing.toTypedArray())
    }

    // صاحب گوشی باید با اثر انگشت یا قفل صفحه تایید کند؛ گوشی دست دیگری باشد حضور ثبت نمی‌شود
    private fun authenticateThenStart() {
        val authenticators = allowedAuthenticators()
        if (BiometricManager.from(requireContext()).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            renderStatus(
                "قفل صفحه فعال نیست",
                "برای ثبت حضور باید اثر انگشت یا رمز صفحه روی گوشی فعال باشد",
                R.drawable.ic_lock, R.color.warning_container, R.color.warning
            )
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تایید هویت")
            .setSubtitle("برای ثبت حضور، هویت خود را تایید کنید")
            .setAllowedAuthenticators(authenticators)
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    // اندروید ۹ و ۱۰ ترکیب BIOMETRIC_STRONG با رمز صفحه را پشتیبانی نمی‌کنند
    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

    private fun createBiometricPrompt(): BiometricPrompt =
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(requireContext()),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vm.startCheckIn()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (_binding != null) {
                        renderStatus("تایید هویت انجام نشد", "دوباره دکمه ثبت حضور را بزنید",
                            R.drawable.ic_lock, R.color.warning_container, R.color.warning)
                    }
                }
            }
        )

    private fun renderState(state: AttendanceClient.State) {
        val busy = state is AttendanceClient.State.Searching ||
            state is AttendanceClient.State.Connecting ||
            state is AttendanceClient.State.Sending

        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnCheckIn.isEnabled = !busy
        if (busy && pulse == null) {
            pulse = binding.pulseRing.startPulse()
        } else if (!busy) {
            binding.pulseRing.stopPulse(pulse)
            pulse = null
        }

        when (state) {
            AttendanceClient.State.Idle -> renderStatus(
                "آماده ثبت حضور", "داخل کلاس دکمه بالا را بزنید",
                R.drawable.ic_info, R.color.primary_container, R.color.primary
            )
            AttendanceClient.State.Searching -> renderStatus(
                "در حال جستجوی گوشی استاد…", "گوشی را داخل کلاس نگه دارید",
                R.drawable.ic_bluetooth, R.color.primary_container, R.color.primary
            )
            AttendanceClient.State.Connecting -> renderStatus(
                "گوشی استاد پیدا شد", "در حال اتصال امن…",
                R.drawable.ic_bluetooth, R.color.primary_container, R.color.primary
            )
            is AttendanceClient.State.Sending -> renderStatus(
                "در حال ثبت حضور", "درس «${state.courseName}»",
                R.drawable.ic_bluetooth, R.color.primary_container, R.color.primary
            )
            is AttendanceClient.State.Success -> renderStatus(
                if (state.alreadyRegistered) "قبلاً ثبت شده" else "حضور شما ثبت شد",
                "درس «${state.courseName}» — ${PersianDate.formatTime(System.currentTimeMillis())}",
                R.drawable.ic_check_circle, R.color.success_container, R.color.success
            )
            is AttendanceClient.State.NeedsQr -> {
                renderStatus(
                    "اسکن QR لازم است", "QR روی گوشی استاد درس «${state.courseName}» را اسکن کنید",
                    R.drawable.ic_qr, R.color.primary_container, R.color.primary
                )
                if (!vm.qrScanLaunched) {
                    vm.qrScanLaunched = true
                    launchQrScanner()
                }
            }
            is AttendanceClient.State.Failed -> renderStatus(
                "ثبت نشد", state.message,
                R.drawable.ic_warning, R.color.error_container, R.color.error
            )
        }
    }

    private fun launchQrScanner() {
        qrScanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("QR روی گوشی استاد را داخل کادر بگیرید")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    private fun renderStatus(
        title: String,
        body: String,
        @DrawableRes icon: Int,
        @ColorRes background: Int,
        @ColorRes foreground: Int
    ) {
        val context = requireContext()
        binding.txtStatusTitle.text = title
        binding.txtStatusBody.text = body
        binding.imgStatus.setImageResource(icon)
        binding.imgStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, background))
        binding.imgStatus.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, foreground))
    }

    // برای هر درس یک برچسب: سبز اگر امروز ثبت شده
    private fun renderTodayChips(items: List<MyCourseItem>) {
        val context = requireContext()
        binding.chipsToday.removeAllViews()
        binding.txtNoCourses.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        items.forEach { item ->
            binding.chipsToday.addView(Chip(context).apply {
                text = item.course.name
                isClickable = false
                isCheckable = false
                if (item.registeredToday) {
                    setChipIconResource(R.drawable.ic_check)
                    isChipIconVisible = true
                    chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.success))
                    chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.success_container))
                    setTextColor(ContextCompat.getColor(context, R.color.success))
                    chipStrokeWidth = 0f
                } else {
                    chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface))
                }
            })
        }
    }
}
