package com.ali_1200.presence

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ali_1200.presence.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// قاب اصلی برنامه: صفحه ورود، یا نوار بالا + صفحه هر تب + ناوبری پایین (جدا برای استاد و دانشجو)
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val teacherViewModel: TeacherViewModel by viewModels()

    private class Tab(val tag: String, val title: String, val create: () -> Fragment)

    private var tabs: Map<Int, Tab> = emptyMap()
    private var currentTab: Int = 0
    private var isTeacher = false

    // دکمه برگشت در تب‌های دیگر به تب اول برمی‌گردد
    private val backToFirstTab = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            tabs.keys.firstOrNull()?.let { binding.bottomNav.selectedItemId = it }
        }
    }

    companion object {
        private const val KEY_TAB = "current_tab"
        private const val LOGIN_TAG = "login"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        usePersianLanguage()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_about -> showAbout()
                R.id.action_reset -> confirmReset()
            }
            true
        }
        onBackPressedDispatcher.addCallback(this, backToFirstTab)

        val savedTab = savedInstanceState?.getInt(KEY_TAB)
        lifecycleScope.launch {
            val login = LoginRepository(this@MainActivity).getSavedLoginData()
            if (login == null) showLogin() else showHome(login.isTeacher, savedTab)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    // همه متن‌ها، پنجره‌ها و جهت صفحه فارسی و راست‌به‌چپ (حتی اگر زبان گوشی انگلیسی باشد)
    private fun usePersianLanguage() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa"))
        }
    }

    // برنامه زیر نوار وضعیت و نوار ناوبری گوشی کشیده می‌شود؛ فاصله لازم اینجا داده می‌شود
    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // ناوبری پایین خودش فاصله پایین را می‌گیرد؛ در صفحه ورود (بدون ناوبری) کیبورد هم حساب می‌شود
            val bottom = if (binding.bottomNav.visibility == View.VISIBLE) 0 else maxOf(bars.bottom, ime.bottom)
            view.setPadding(bars.left, bars.top, bars.right, bottom)
            insets
        }
    }

    private fun showLogin() {
        isTeacher = false
        tabs = emptyMap()
        binding.toolbar.visibility = View.GONE
        binding.bottomNav.visibility = View.GONE
        backToFirstTab.isEnabled = false
        ViewCompat.requestApplyInsets(binding.root)

        val transaction = supportFragmentManager.beginTransaction()
        supportFragmentManager.fragments.filter { it.tag != LOGIN_TAG }.forEach { transaction.remove(it) }
        if (supportFragmentManager.findFragmentByTag(LOGIN_TAG) == null) {
            transaction.add(R.id.frag_main, LoginFragment(), LOGIN_TAG)
        }
        transaction.commit()
    }

    fun showHome(teacher: Boolean, restoreTab: Int? = null) {
        isTeacher = teacher
        tabs = if (teacher) {
            linkedMapOf(
                R.id.nav_session to Tab("session", "حضور و غیاب") { TeacherSessionFragment() },
                R.id.nav_courses to Tab("courses", "درس‌ها") { TeacherCoursesFragment() },
                R.id.nav_report to Tab("report", "گزارش") { TeacherReportFragment() }
            )
        } else {
            linkedMapOf(
                R.id.nav_check_in to Tab("check_in", "ثبت حضور") { StudentCheckInFragment() },
                R.id.nav_my_courses to Tab("my_courses", "درس‌های من") { StudentCoursesFragment() }
            )
        }

        binding.toolbar.visibility = View.VISIBLE
        binding.bottomNav.visibility = View.VISIBLE
        ViewCompat.requestApplyInsets(binding.root)

        binding.bottomNav.setOnItemSelectedListener(null)
        binding.bottomNav.menu.clear()
        binding.bottomNav.inflateMenu(if (teacher) R.menu.nav_teacher else R.menu.nav_student)
        val first = tabs.keys.first()
        val target = restoreTab?.takeIf { it in tabs } ?: first
        binding.bottomNav.selectedItemId = target
        binding.bottomNav.setOnItemSelectedListener { item ->
            switchTo(item.itemId)
            true
        }
        switchTo(target)

        // زیرعنوان نوار بالا: درس فعال استاد
        if (teacher) {
            teacherViewModel.selectedCourse.observe(this) { course ->
                if (isTeacher) binding.toolbar.subtitle = course?.name
            }
        } else {
            binding.toolbar.subtitle = null
        }
    }

    fun selectTab(itemId: Int) {
        if (itemId in tabs) binding.bottomNav.selectedItemId = itemId
    }

    // صفحه هر تب یک بار ساخته و بعد فقط پنهان/نمایان می‌شود تا وضعیتش حفظ شود
    private fun switchTo(itemId: Int) {
        val tab = tabs[itemId] ?: return
        val manager = supportFragmentManager
        val transaction = manager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

        manager.findFragmentByTag(LOGIN_TAG)?.let { transaction.remove(it) }
        tabs.values.filter { it !== tab }.forEach { other ->
            manager.findFragmentByTag(other.tag)?.let { transaction.hide(it) }
        }
        val existing = manager.findFragmentByTag(tab.tag)
        if (existing == null) transaction.add(R.id.frag_main, tab.create(), tab.tag) else transaction.show(existing)
        transaction.commit()

        currentTab = itemId
        binding.toolbar.title = tab.title
        backToFirstTab.isEnabled = itemId != tabs.keys.first()
    }

    fun showMessage(message: String) {
        Snackbar.make(binding.fragMain, message, Snackbar.LENGTH_LONG).apply {
            if (binding.bottomNav.visibility == View.VISIBLE) anchorView = binding.bottomNav
        }.show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("درباره برنامه")
            .setMessage(
                "حضور و غیاب بدون اینترنت — نسخه ۱٫۰\n\n" +
                    "ثبت حضور با بلوتوث کم‌مصرف و احراز هویت رمزنگاری‌شده (HMAC-SHA256)؛ " +
                    "همه اطلاعات فقط روی همین گوشی ذخیره می‌شود.\n\n" +
                    "فونت: شبنم، اثر صابر راستی‌کردار (مجوز SIL OFL 1.1)"
            )
            .setPositiveButton("باشه", null)
            .show()
    }

    // برای عوض کردن نقش یا شروع دوباره؛ همه درس‌ها و حضورها پاک می‌شوند
    private fun confirmReset() {
        val warning = if (isTeacher) {
            "همه درس‌ها، لیست‌های کلاس و حضورهای ثبت‌شده پاک می‌شوند. اگر لازمشان دارید، اول از «گزارش» خروجی Excel بگیرید."
        } else {
            "درس‌ها و سابقه حضور شما روی این گوشی پاک می‌شود."
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("خروج و حذف اطلاعات")
            .setMessage(warning)
            .setPositiveButton("حذف و خروج") { _, _ -> resetAll() }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun resetAll() {
        if (isTeacher) teacherViewModel.stopServer()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                MainDatabase.getDatabase(this@MainActivity).clearAllTables()
            }
            getSharedPreferences("presence", MODE_PRIVATE).edit().clear().apply()
            viewModelStore.clear()
            recreate()
        }
    }
}
