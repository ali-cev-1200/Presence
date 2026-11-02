package com.ali_1200.presence

import androidx.room.Entity

// یک دانشجو در لیست یک درس (از فایل CSV وارد می‌شود)
@Entity(tableName = "Student", primaryKeys = ["courseId", "studentID"])
data class Student(
    val studentID: String,
    val name: String,
    val courseId: Long = 0
)
