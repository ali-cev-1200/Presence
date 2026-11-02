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
    private val teacherKeyStore = TeacherKeyStore(appContext)

    suspend fun export(uri: Uri): Boolean {
        val courses = backupDao.courses().map {
            AttendanceBackup.CourseData(it, backupDao.students(it.id), backupDao.records(it.id))
        }
        val backup = AttendanceBackup.Backup(courses, teacherKeyStore.getOrCreate())
        val text = AttendanceBackup.encode(backup, System.currentTimeMillis())
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

        val backup = AttendanceBackup.decode(text)
        if (backup.courses.isEmpty()) throw AttendanceBackup.InvalidBackupException("در این فایل درسی نیست")
        val ids = backupDao.replaceAll(backup.courses)
        // گوشی جدید با همان امضای گوشی قبلی کار می‌کند و دانشجوها چیزی را عوض نمی‌کنند
        backup.teacherKey?.let(teacherKeyStore::set)
        courseRepository.selectedCourseId = ids.first()
        return ids.size
    }
}
