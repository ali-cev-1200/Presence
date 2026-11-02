package com.ali_1200.presence

import android.content.Context
import androidx.lifecycle.LiveData

// Repository درس‌ها (هم برای استاد و هم برای دانشجو)
class CourseRepository(context: Context) {
    private val courseDao = MainDatabase.getDatabase(context).courseDao
    private val prefs = context.applicationContext.getSharedPreferences("presence", Context.MODE_PRIVATE)

    val courses: LiveData<List<Course>> = courseDao.getCourses()
    val myAttendance: LiveData<List<MyAttendance>> = courseDao.getMyAttendance()
    val rosterCounts: LiveData<List<CourseCount>> = courseDao.rosterCounts()
    val sessionCounts: LiveData<List<CourseCount>> = courseDao.sessionCounts()

    suspend fun getCoursesOnce(): List<Course> = courseDao.getCoursesOnce()

    // استاد: درس تازه با کد کلاس تصادفی
    suspend fun createCourse(name: String): Long =
        courseDao.insertCourse(Course(name = name, classCode = AttendanceCrypto.generateClassCode()))

    // دانشجو: اضافه کردن درس با کد کلاسی که استاد داده؛ اگر قبلاً اضافه شده null
    suspend fun joinCourse(name: String, classCode: String): Long? {
        if (courseDao.findByCode(classCode) != null) return null
        return courseDao.insertCourse(Course(name = name, classCode = classCode))
    }

    suspend fun deleteCourse(courseId: Long) {
        courseDao.deleteCourse(courseId)
    }

    suspend fun recordMyAttendance(courseId: Long, now: Long) {
        courseDao.insertMyAttendance(MyAttendance(courseId = courseId, timestamp = now))
    }

    // درسی که استاد آخرین بار انتخاب کرده بود
    var selectedCourseId: Long
        get() = prefs.getLong("selected_course_id", 0)
        set(value) = prefs.edit().putLong("selected_course_id", value).apply()
}
