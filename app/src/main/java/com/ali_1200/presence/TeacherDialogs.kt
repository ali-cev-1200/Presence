package com.ali_1200.presence

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// پنجره‌های مشترک صفحه‌های استاد

fun Fragment.showCoursePicker(vm: TeacherViewModel) {
    val courses = vm.courses.value.orEmpty()
    val selectedId = vm.selectedCourse.value?.id
    val items = courses.map { if (it.id == selectedId) "✓  ${it.name}" else it.name } + "+  درس جدید"
    MaterialAlertDialogBuilder(requireContext())
        .setTitle("انتخاب درس")
        .setItems(items.toTypedArray()) { _, which ->
            if (which == courses.size) showCreateCourse(vm) else vm.selectCourse(courses[which])
        }
        .show()
}

fun Fragment.showCreateCourse(vm: TeacherViewModel) {
    showInputDialog(
        requireContext(),
        "درس جدید",
        listOf(InputField("نام درس", helper = "برای این درس یک کد کلاس تازه ساخته می‌شود")),
        "ساختن"
    ) { values ->
        val name = values[0]
        if (name.isBlank()) {
            showMessage("نام درس را وارد کنید")
        } else {
            vm.createCourse(name)
            showMessage("درس «$name» ساخته شد")
        }
    }
}

fun Fragment.copyClassCode(course: Course) {
    requireContext().copyToClipboard("کد کلاس", AttendanceCrypto.formatClassCode(course.classCode))
    showMessage("کد کلاس کپی شد")
}

// کد کلاس با حروف درشت؛ برای نشان دادن به کلاس یا نوشتن روی تخته
fun Fragment.showClassCodeDialog(course: Course) {
    val context = requireContext()
    val density = resources.displayMetrics.density
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val padding = (24 * density).toInt()
        setPadding(padding, (8 * density).toInt(), padding, 0)

        addView(TextView(context).apply {
            text = AttendanceCrypto.formatClassCode(course.classCode)
            setTextAppearance(R.style.TextAppearance_Presence_ClassCode)
            // اعداد لاتین؛ کد باید همان‌طور که دیده می‌شود تایپ شود
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            textSize = 34f
            textDirection = View.TEXT_DIRECTION_LTR
            setTextColor(ContextCompat.getColor(context, R.color.primary))
            gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            text = "دانشجوها این کد را یک بار در برنامه وارد می‌کنند. کد را فقط به دانشجوهای همین درس بدهید."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.shabnam_family)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, 0)
        })

        // دانشجوها همین عدد را بعد از اولین حضور در کارت درسشان می‌بینند و می‌توانند مقایسه کنند
        addView(TextView(context).apply {
            text = "امضای این گوشی"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.shabnam_family)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
            gravity = Gravity.CENTER
            setPadding(0, (20 * density).toInt(), 0, 0)
        })
        addView(TextView(context).apply {
            text = TeacherKeyStore(context).getOrCreate().fingerprint
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 16f
            textDirection = View.TEXT_DIRECTION_LTR
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            gravity = Gravity.CENTER
        })
    }

    MaterialAlertDialogBuilder(context)
        .setTitle("کد کلاس «${course.name}»")
        .setView(content)
        .setPositiveButton("کپی") { _, _ -> copyClassCode(course) }
        .setNegativeButton("بستن", null)
        .show()
}

fun Fragment.confirmDeleteCourse(vm: TeacherViewModel, course: Course) {
    confirmDialog(
        requireContext(),
        "حذف درس",
        "درس «${course.name}» همراه با لیست کلاس و همه حضورهایش حذف شود؟ این کار برگشت‌پذیر نیست.",
        "حذف"
    ) {
        vm.deleteCourse(course)
        showMessage("درس «${course.name}» حذف شد")
    }
}
