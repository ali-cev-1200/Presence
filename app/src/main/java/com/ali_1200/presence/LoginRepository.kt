package com.ali_1200.presence

import android.content.Context

// Repository مسئولیت ارتباط با منابع داده (API و دیتابیس) را بر عهده دارد
class LoginRepository(context: Context) {
    private val loginDao = MainDatabase.getDatabase(context).loginDao // دسترسی به DAO

    // دریافت داده‌های ذخیره شده از دیتابیس
    suspend fun getSavedLoginData(): Logingdata? = loginDao.getLoginData()

    suspend fun insertdata(data: Logingdata) {
        loginDao.insertLoginData(data)
    }
}
