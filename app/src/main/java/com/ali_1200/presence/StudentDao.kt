package com.ali_1200.presence

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

// DAO لیست دانشجوهای هر درس
@Dao
interface StudentDao {
    @Query("SELECT * FROM Student WHERE courseId = :courseId ORDER BY name")
    fun getStudents(courseId: Long): LiveData<List<Student>>

    @Query("SELECT * FROM Student WHERE courseId = :courseId ORDER BY name")
    suspend fun getStudentsOnce(courseId: Long): List<Student>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(students: List<Student>)

    @Query("DELETE FROM Student WHERE courseId = :courseId")
    suspend fun deleteAll(courseId: Long)

    // لیست جدید جایگزین لیست قبلی همان درس می‌شود
    @Transaction
    suspend fun replaceAll(courseId: Long, students: List<Student>) {
        deleteAll(courseId)
        insertAll(students.map { it.copy(courseId = courseId) })
    }
}
