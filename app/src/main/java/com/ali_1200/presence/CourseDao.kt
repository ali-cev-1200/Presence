package com.ali_1200.presence

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

// تعداد چیزی (دانشجو یا جلسه) برای هر درس
data class CourseCount(val courseId: Long, val count: Int)

// DAO درس‌ها و حضورهای خود دانشجو
@Dao
interface CourseDao {
    @Query("SELECT courseId, COUNT(*) AS count FROM Student GROUP BY courseId")
    fun rosterCounts(): LiveData<List<CourseCount>>

    // تعداد روزهای متفاوتی که در هر درس حضور ثبت شده (= تعداد جلسات)
    @Query(
        "SELECT courseId, COUNT(DISTINCT date(timestamp / 1000, 'unixepoch', 'localtime')) AS count " +
            "FROM Respons GROUP BY courseId"
    )
    fun sessionCounts(): LiveData<List<CourseCount>>

    @Query("SELECT * FROM Course ORDER BY id")
    fun getCourses(): LiveData<List<Course>>

    @Query("SELECT * FROM Course ORDER BY id")
    suspend fun getCoursesOnce(): List<Course>

    @Query("SELECT * FROM Course WHERE classCode = :classCode LIMIT 1")
    suspend fun findByCode(classCode: String): Course?

    @Insert
    suspend fun insertCourse(course: Course): Long

    @Query("DELETE FROM Course WHERE id = :courseId")
    suspend fun deleteCourseRow(courseId: Long)

    @Query("DELETE FROM Student WHERE courseId = :courseId")
    suspend fun deleteRoster(courseId: Long)

    @Query("DELETE FROM Respons WHERE courseId = :courseId")
    suspend fun deleteResponses(courseId: Long)

    @Query("DELETE FROM MyAttendance WHERE courseId = :courseId")
    suspend fun deleteMyAttendance(courseId: Long)

    // درس همراه با لیست کلاس و همه حضورهایش حذف می‌شود
    @Transaction
    suspend fun deleteCourse(courseId: Long) {
        deleteRoster(courseId)
        deleteResponses(courseId)
        deleteMyAttendance(courseId)
        deleteCourseRow(courseId)
    }

    @Insert
    suspend fun insertMyAttendance(attendance: MyAttendance)

    @Query("SELECT * FROM MyAttendance ORDER BY timestamp")
    fun getMyAttendance(): LiveData<List<MyAttendance>>
}
