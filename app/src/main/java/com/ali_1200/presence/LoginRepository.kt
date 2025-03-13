package com.ali_1200.presence

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Repository مسئولیت ارتباط با منابع داده (API و دیتابیس) را بر عهده دارد
class LoginRepository(context: Context) {
    private val loginDao = MainDatabase.getDatabase(context).loginDao // دسترسی به DAO

    // دریافت داده‌های ذخیره شده از دیتابیس
    fun getSavedLoginData(): Logingdata? {
            val data = loginDao.getLoginData()
        return data
    }

    fun insertdata(data: Logingdata){
        loginDao.insertLoginData(data)
    }
}
