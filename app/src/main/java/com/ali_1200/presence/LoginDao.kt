package com.ali_1200.presence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// DAO (Data Access Object) برای مدیریت عملیات دیتابیس مربوط به لاگین
@Dao
interface LoginDao {
    // درج یا جایگزینی رکورد در صورت وجود تضاد (مثلاً اگر کاربر قبلاً لاگین کرده بود)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLoginData(data: Logingdata)

    // دریافت آخرین داده‌های لاگین ذخیره شده (حداکثر ۱ رکورد)
    @Query("SELECT * FROM Login LIMIT 1")
    fun getLoginData(): Logingdata?

}