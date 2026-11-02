package com.ali_1200.presence

import android.content.Context
import androidx.lifecycle.LiveData

// Repository لیست دانشجوهای هر درس
class RosterRepository(context: Context) {
    private val studentDao = MainDatabase.getDatabase(context).studentDao

    fun students(courseId: Long): LiveData<List<Student>> = studentDao.getStudents(courseId)

    suspend fun getStudentsOnce(courseId: Long): List<Student> = studentDao.getStudentsOnce(courseId)

    suspend fun replaceAll(courseId: Long, students: List<Student>) {
        studentDao.replaceAll(courseId, students)
    }
}
