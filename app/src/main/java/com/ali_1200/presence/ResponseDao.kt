package com.ali_1200.presence

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

// DAO (Data Access Object) برای مدیریت حضورهای دریافت‌شده در گوشی استاد
@Dao
interface ResponseDao {
    @Insert
    suspend fun insertResponse(data: dateResponse)

    @Delete
    suspend fun deleteResponse(data: dateResponse)

    // حضورهای یک درس از زمان داده‌شده (مثلاً از اول امروز)، جدیدترین اول
    @Query("SELECT * FROM Respons WHERE courseId = :courseId AND timestamp >= :since ORDER BY timestamp DESC")
    fun getResponsesSince(courseId: Long, since: Long): LiveData<List<dateResponse>>

    // همه حضورهای یک درس برای صفحه گزارش
    @Query("SELECT * FROM Respons WHERE courseId = :courseId ORDER BY timestamp")
    fun getAll(courseId: Long): LiveData<List<dateResponse>>

    // همه حضورهای یک درس برای خروجی Excel
    @Query("SELECT * FROM Respons WHERE courseId = :courseId ORDER BY timestamp")
    suspend fun getAllOnce(courseId: Long): List<dateResponse>

    // حضورهایی که در همین درس از زمان داده‌شده با همین کد دانشجویی یا همین گوشی ثبت شده‌اند
    @Query(
        "SELECT * FROM Respons WHERE courseId = :courseId AND timestamp >= :since " +
            "AND (studentID = :studentId OR deviceID = :deviceId)"
    )
    suspend fun findSince(courseId: Long, studentId: String, deviceId: String, since: Long): List<dateResponse>
}
