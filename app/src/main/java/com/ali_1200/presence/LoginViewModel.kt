package com.ali_1200.presence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers

// ViewModel مسئولیت ارتباط بین UI و Repository را بر عهده دارد
class LoginViewModel(application: Application) : ViewModel() {
    private val repository = LoginRepository(application) // نمونه‌ای از Repository

    // دریافت داده‌های ذخیره شده از دیتابیس
    fun getSavedLoginData(): Logingdata? {
        return repository.getSavedLoginData()
    }

    fun insertdata(data: Logingdata) =
        repository.insertdata(data)

}
