package com.ali_1200.presence

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ساختن فایل پشتیبان از دیتابیس و بازگرداندن آن
class BackupRepository(context: Context) {
    private val appContext = context.applicationContext
    private val backupDao = MainDatabase.getDatabase(appContext).backupDao
    private val courseRepository = CourseRepository(appContext)

    suspend fun export(uri: Uri): Boolean {
        val courses = backupDao.courses().map {
            AttendanceBackup.CourseData(it, backupDao.students(it.id), backupDao.records(it.id))
        }
        val text = AttendanceBackup.encode(courses, System.currentTimeMillis())
        return withContext(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
            }.getOrDefault(false)
        }
    }

    // تعداد درس‌های بازیابی‌شده؛ اگر فایل نامعتبر باشد InvalidBackupException
    suspend fun restore(uri: Uri): Int {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
        } ?: throw AttendanceBackup.InvalidBackupException("خواندن فایل ناموفق بود")

        val courses = AttendanceBackup.decode(text)
        if (courses.isEmpty()) throw AttendanceBackup.InvalidBackupException("در این فایل درسی نیست")
        val ids = backupDao.replaceAll(courses)
        courseRepository.selectedCourseId = ids.first()
        return ids.size
    }
}
