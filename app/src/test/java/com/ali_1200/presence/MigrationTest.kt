package com.ali_1200.presence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// دیتابیس نسخه ۴ (همان SQL که Room برای نسخه ۴ ساخته بود) با Migrationها به نسخه آخر برده می‌شود؛
// Room بعد از Migration ساختار جدول‌ها را با Entityها مقایسه می‌کند و اگر فرق داشته باشد خطا می‌دهد
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"

    @Before
    fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createVersion4(isTeacher: Boolean) {
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `Login` (`id` INTEGER NOT NULL, `studentID` TEXT NOT NULL, `name` TEXT NOT NULL, `deviceID` TEXT NOT NULL, `timestamp` INTEGER, `is_send` INTEGER, `isTeacher` INTEGER NOT NULL, `classCode` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `Respons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `studentID` TEXT NOT NULL, `name` TEXT NOT NULL, `deviceID` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `date` TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `Student` (`studentID` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`studentID`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c017e600339ad9c54c3dda25470a28c2')")

            val teacher = if (isTeacher) 1 else 0
            db.execSQL("INSERT INTO Login VALUES (1, '40012345', 'علی', 'dev1', 5000, 1, $teacher, 'ABCDE23456')")
            db.execSQL("INSERT INTO Respons (studentID, name, deviceID, timestamp, date) VALUES ('40012345', 'علی', 'dev1', 1000, '1405/08/11 - 10:00')")
            db.execSQL("INSERT INTO Respons (studentID, name, deviceID, timestamp, date) VALUES ('40012346', 'مریم', 'dev2', 2000, '1405/08/11 - 10:01')")
            db.execSQL("INSERT INTO Student VALUES ('40012345', 'علی')")
            db.version = 4
        }
    }

    private fun openMigrated(): MainDatabase =
        Room.databaseBuilder(context, MainDatabase::class.java, dbName)
            .addMigrations(*MainDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    @Test
    fun teacherDataMovesToFirstCourse() = runBlocking {
        createVersion4(isTeacher = true)
        val db = openMigrated()
        try {
            val courses = db.courseDao.getCoursesOnce()
            assertEquals(listOf(Course(1, "درس ۱", "ABCDE23456")), courses)

            val login = db.loginDao.getLoginData()!!
            assertTrue(login.isTeacher)
            assertEquals("علی", login.name)

            val records = db.responseDao.getAllOnce(1)
            assertEquals(listOf("40012345", "40012346"), records.map { it.studentID })
            assertEquals(listOf(Student("40012345", "علی", 1)), db.studentDao.getStudentsOnce(1))

            // شماره‌گذاری خودکار بعد از Migration ادامه پیدا می‌کند
            db.responseDao.insertResponse(dateResponse(studentID = "3", name = "x", deviceID = "d", timestamp = 3000, courseId = 1))
            assertEquals(3L, db.responseDao.getAllOnce(1).last().id)
        } finally {
            db.close()
        }
    }

    @Test
    fun studentSentStatusBecomesMyAttendance() = runBlocking {
        createVersion4(isTeacher = false)
        val db = openMigrated()
        try {
            assertEquals(1, db.courseDao.getCoursesOnce().size)
            val cursor = db.query("SELECT courseId, timestamp FROM MyAttendance", null)
            cursor.use {
                assertTrue(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
                assertEquals(5000L, it.getLong(1))
                assertEquals(1, it.count)
            }

            // نسخه ۶: کلید استاد هنوز ذخیره نشده؛ اولین بار ذخیره و بعد عوض نمی‌شود
            assertEquals(null, db.courseDao.getCoursesOnce().single().teacherKey)
            db.courseDao.pinTeacherKey(1, "first")
            db.courseDao.pinTeacherKey(1, "second")
            assertEquals("first", db.courseDao.getCoursesOnce().single().teacherKey)
        } finally {
            db.close()
        }
    }
}
