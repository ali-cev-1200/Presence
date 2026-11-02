package com.ali_1200.presence

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.radiobutton.MaterialRadioButton

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

// تنظیمات جلسه: محدوده کلاس با قدرت سیگنال و QR چرخشی
fun Fragment.showSessionSettingsDialog(settings: SessionSettings, onChanged: () -> Unit) {
    val context = requireContext()
    val density = resources.displayMetrics.density
    val levels = RangeLevel.entries
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (24 * density).toInt()
        setPadding(padding, (8 * density).toInt(), padding, 0)
    }
    // متنی که با «QR» شروع می‌شود هم راست‌چین بماند
    fun label(text: String, top: Int) = TextView(context).apply {
        this.text = text
        textDirection = View.TEXT_DIRECTION_RTL
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        typeface = ResourcesCompat.getFont(context, R.font.shabnam_bold_fd)
        setPadding(0, (top * density).toInt(), 0, (4 * density).toInt())
    }
    fun hint(text: String) = TextView(context).apply {
        this.text = text
        textDirection = View.TEXT_DIRECTION_RTL
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        typeface = ResourcesCompat.getFont(context, R.font.shabnam_fd)
        setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
    }

    content.addView(label("محدوده کلاس", 0))
    content.addView(hint("دانشجویی که سیگنالش از این محدوده ضعیف‌تر باشد ثبت نمی‌شود؛ فاصله‌ها تقریبی‌اند"))
    val group = RadioGroup(context)
    levels.forEachIndexed { index, level ->
        group.addView(MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = level.title
            typeface = ResourcesCompat.getFont(context, R.font.shabnam_fd)
            isChecked = level == settings.range
            tag = index
        })
    }
    content.addView(group)

    content.addView(label("QR چرخشی", 16))
    content.addView(hint("دانشجو باید QR روی صفحه شما را هم اسکن کند؛ QR هر ۱۰ ثانیه عوض می‌شود، پس عکسش برای دوست بیرون کلاس به کار نمی‌آید"))
    val qrSwitch = MaterialSwitch(context).apply {
        text = "QR الزامی باشد"
        textDirection = View.TEXT_DIRECTION_RTL
        typeface = ResourcesCompat.getFont(context, R.font.shabnam_fd)
        isChecked = settings.qrRequired
    }
    content.addView(qrSwitch)

    MaterialAlertDialogBuilder(context)
        .setTitle("تنظیمات جلسه")
        .setView(ScrollView(context).apply { addView(content) })
        .setPositiveButton("ذخیره") { _, _ ->
            val checked = group.findViewById<View>(group.checkedRadioButtonId)?.tag as? Int
            if (checked != null) settings.range = levels[checked]
            settings.qrRequired = qrSwitch.isChecked
            onChanged()
            showMessage("تنظیمات از دانشجوی بعدی اعمال می‌شود")
        }
        .setNegativeButton("انصراف", null)
        .show()
}

// QR چرخشی تمام‌صفحه با شمارش معکوس تا QR بعدی؛ تا وقتی حضور و غیاب روشن است عوض می‌شود
fun Fragment.showRotatingQr(vm: TeacherViewModel, course: Course) {
    val context = requireContext()
    val density = resources.displayMetrics.density
    val teacherKey = TeacherKeyStore(context).getOrCreate()
    val size = (minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) * 0.72).toInt()

    val image = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_HORIZONTAL }
        contentDescription = "QR حضور و غیاب"
    }
    val progress = LinearProgressIndicator(context).apply {
        max = RotatingQr.PERIOD.toInt()
        trackCornerRadius = (4 * density).toInt()
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = (12 * density).toInt() }
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (24 * density).toInt()
        setPadding(padding, (8 * density).toInt(), padding, 0)
        addView(image)
        addView(progress)
        addView(TextView(context).apply {
            text = "دانشجوها بعد از زدن «ثبت حضور» این QR را اسکن می‌کنند. هر ۱۰ ثانیه عوض می‌شود."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            typeface = ResourcesCompat.getFont(context, R.font.shabnam_fd)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, 0)
        })
    }

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle("QR «${course.name}»")
        .setView(content)
        .setPositiveButton("بستن", null)
        .create()

    val handler = Handler(Looper.getMainLooper())
    var shownPeriod = -1L
    val tick = object : Runnable {
        override fun run() {
            if (!vm.server.isActive) {
                dialog.dismiss()
                return
            }
            val now = System.currentTimeMillis()
            val period = RotatingQr.period(now)
            if (period != shownPeriod) {
                shownPeriod = period
                image.setImageBitmap(RotatingQr.bitmap(RotatingQr.content(teacherKey, course.classCode, period), size))
            }
            progress.progress = (RotatingQr.PERIOD - now % RotatingQr.PERIOD).toInt()
            handler.postDelayed(this, 100)
        }
    }
    dialog.setOnShowListener {
        // روشنایی کامل تا دوربین دانشجوها راحت‌تر بخواند
        dialog.window?.attributes = dialog.window?.attributes?.apply { screenBrightness = 1f }
        tick.run()
    }
    dialog.setOnDismissListener { handler.removeCallbacks(tick) }
    dialog.show()
}
