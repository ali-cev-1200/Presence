package com.ali_1200.presence

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {
    lateinit var repository: LoginRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        darkMode()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

        repository = LoginRepository(this)
        val data = repository.getSavedLoginData()
        if (data != null) {
            if (data.studentID == "1") {
                replace_Fragment(BLEAdvertiserFragment())
            } else {
                replace_Fragment(BLEScannerFragment())
            }
        } else {
            replace_Fragment(Frag_Login())
        }
    }

    fun replace_Fragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.frag_main, fragment)
        transaction.commit()
    }
    // متد برای تنظیم حالت دارک مود و تغییر آیکون
    private fun setDarkMode(isNightMode: Boolean) {
        if (isNightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        // فقط به‌روزرسانی محتویات فعلی فرگمنت
    }
    fun darkMode() {
        // SharedPreferences برای ذخیره وضعیت دارک مود
        val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isNightMode = sharedPreferences.getBoolean("night_mode", false)

        // تنظیم آیکون و حالت دارک مود بر اساس وضعیت ذخیره‌شده
        setDarkMode(isNightMode)
    }
}