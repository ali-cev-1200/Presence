package com.ali_1200.presence

import androidx.room.Entity
import androidx.room.PrimaryKey

// اطلاعات کاربر همین گوشی (همیشه یک ردیف)
@Entity(tableName = "Login")
data class Logingdata(
    @PrimaryKey val id: Int = 1,
    val studentID: String,
    val name: String,
    val deviceID: String,
    val isTeacher: Boolean = false
)

// یک درس: در گوشی استاد درس‌هایی که ساخته، در گوشی دانشجو درس‌هایی که با کد کلاس اضافه کرده
// teacherKey فقط در گوشی دانشجو: کلید عمومی امضای استاد (base64) که در اولین ثبت حضور موفق ذخیره می‌شود
@Entity(tableName = "Course")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val classCode: String,
    val teacherKey: String? = null
)

// حضورهای دریافت‌شده در گوشی استاد
@Entity(tableName = "Respons")
data class dateResponse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentID: String,
    val name: String,
    val deviceID: String,
    val timestamp: Long,
    val date :String?="",
    val courseId: Long = 0
)

// حضورهای موفق خود دانشجو (در گوشی دانشجو)؛ برای «امروز ثبت شده؟» و تعداد جلسات
@Entity(tableName = "MyAttendance")
data class MyAttendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val timestamp: Long
)
