package com.ali_1200.presence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// org.json در تست معمولی فقط نسخه خالی اندروید است؛ Robolectric نسخه واقعی را می‌دهد
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AttendanceBackupTest {
    private val sample = listOf(
        AttendanceBackup.CourseData(
            Course(7, "ریاضی ۱", "ABCDE23456"),
            listOf(Student("40012345", "علی رضایی", 7), Student("40012346", "مریم احمدی", 7)),
            listOf(dateResponse(3, "40012345", "علی رضایی", "dev1", 1000, "1405/08/11 - 10:00", 7))
        ),
        AttendanceBackup.CourseData(Course(9, "فیزیک", "FGHJK78923"), emptyList(), emptyList())
    )

    @Test
    fun encodeThenDecodeKeepsData() {
        val decoded = AttendanceBackup.decode(AttendanceBackup.encode(sample, 0))

        assertEquals(listOf("ریاضی ۱", "فیزیک"), decoded.map { it.course.name })
        assertEquals(listOf("ABCDE23456", "FGHJK78923"), decoded.map { it.course.classCode })
        assertEquals(listOf("40012345", "40012346"), decoded[0].students.map { it.studentID })
        val record = decoded[0].records.single()
        assertEquals("dev1", record.deviceID)
        assertEquals(1000L, record.timestamp)
        assertEquals("1405/08/11 - 10:00", record.date)
    }

    @Test
    fun rejectsOtherFiles() {
        assertThrows(AttendanceBackup.InvalidBackupException::class.java) { AttendanceBackup.decode("سلام") }
        assertThrows(AttendanceBackup.InvalidBackupException::class.java) { AttendanceBackup.decode("""{"a":1}""") }
        // کد کلاس خراب
        val broken = AttendanceBackup.encode(sample, 0).replace("ABCDE23456", "ABC")
        assertThrows(AttendanceBackup.InvalidBackupException::class.java) { AttendanceBackup.decode(broken) }
    }

    @Test
    fun restoreReplacesAllCourses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MainDatabase::class.java).allowMainThreadQueries().build()
        try {
            val oldId = db.courseDao.insertCourse(Course(name = "درس قدیمی", classCode = "QWERT23456"))
            db.studentDao.insertAll(listOf(Student("1", "قدیمی", oldId)))

            val ids = db.backupDao.replaceAll(AttendanceBackup.decode(AttendanceBackup.encode(sample, 0)))

            assertEquals(listOf("ریاضی ۱", "فیزیک"), db.backupDao.courses().map { it.name })
            assertEquals(2, db.backupDao.students(ids[0]).size)
            assertEquals(listOf("40012345"), db.backupDao.records(ids[0]).map { it.studentID })
            assertEquals(0, db.backupDao.students(oldId).size)
        } finally {
            db.close()
        }
    }
}
