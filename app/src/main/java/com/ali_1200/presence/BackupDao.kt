package com.ali_1200.presence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

// DAO پشتیبان‌گیری و بازیابی همه اطلاعات استاد
@Dao
interface BackupDao {
    @Query("SELECT * FROM Course ORDER BY id")
    suspend fun courses(): List<Course>

    @Query("SELECT * FROM Student WHERE courseId = :courseId ORDER BY name")
    suspend fun students(courseId: Long): List<Student>

    @Query("SELECT * FROM Respons WHERE courseId = :courseId ORDER BY timestamp")
    suspend fun records(courseId: Long): List<dateResponse>

    @Query("DELETE FROM Course")
    suspend fun deleteCourses()

    @Query("DELETE FROM Student")
    suspend fun deleteStudents()

    @Query("DELETE FROM Respons")
    suspend fun deleteRecords()

    @Insert
    suspend fun insertCourse(course: Course): Long

    @Insert
    suspend fun insertStudents(students: List<Student>)

    @Insert
    suspend fun insertRecords(records: List<dateResponse>)

    // همه درس‌های فعلی جای خود را به درس‌های فایل می‌دهند؛ یا همه انجام می‌شود یا هیچ‌کدام
    @Transaction
    suspend fun replaceAll(courses: List<AttendanceBackup.CourseData>): List<Long> {
        deleteRecords()
        deleteStudents()
        deleteCourses()
        return courses.map { data ->
            val id = insertCourse(data.course.copy(id = 0))
            insertStudents(data.students.map { it.copy(courseId = id) })
            insertRecords(data.records.map { it.copy(id = 0, courseId = id) })
            id
        }
    }
}
