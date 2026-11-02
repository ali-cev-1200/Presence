package com.ali_1200.presence

import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.sync.Mutex

// Repository مسئولیت ارتباط با منابع داده (API و دیتابیس) را بر عهده دارد
class ResponseRepository(context: Context) {
    private val responseDao = MainDatabase.getDatabase(context).responseDao // دسترسی به DAO
    private val saveLock = Mutex() // تا دو درخواست هم‌زمان هر دو از بررسی تکراری رد نشوند

    enum class SaveResult { SAVED, ALREADY_REGISTERED, CONFLICT }

    companion object {
        // حضوری که استاد دستی ثبت کرده، به‌جای شناسه گوشی این مقدار را دارد
        fun manualDeviceId(studentId: String) = "manual-$studentId"

        fun isManual(record: dateResponse) = record.deviceID == manualDeviceId(record.studentID)
    }

    // ✅ حضورهای امروز یک درس به‌صورت LiveData
    fun todayResponses(courseId: Long, now: Long): LiveData<List<dateResponse>> =
        responseDao.getResponsesSince(courseId, startOfDay(now))

    fun allResponses(courseId: Long): LiveData<List<dateResponse>> = responseDao.getAll(courseId)

    suspend fun getAllOnce(courseId: Long): List<dateResponse> = responseDao.getAllOnce(courseId)

    suspend fun delete(record: dateResponse) {
        responseDao.deleteResponse(record)
    }

    // در هر درس، هر دانشجو و هر گوشی فقط یک بار در روز ثبت می‌شود
    suspend fun saveAttendance(courseId: Long, payload: AttendancePayload, now: Long): SaveResult {
        saveLock.lock()
        try {
            return insertIfNew(courseId, payload, now)
        } finally {
            saveLock.unlock()
        }
    }

    // ثبت دستی توسط استاد (مثلاً گوشی دانشجو خاموش است)؛ اگر امروز حاضر بوده false
    suspend fun saveManual(courseId: Long, student: Student, now: Long): Boolean {
        val payload = AttendancePayload(student.name, student.studentID, manualDeviceId(student.studentID), "", rssi = 0)
        return saveAttendance(courseId, payload, now) == SaveResult.SAVED
    }

    private suspend fun insertIfNew(courseId: Long, payload: AttendancePayload, now: Long): SaveResult {
        val today = responseDao.findSince(courseId, payload.studentId, payload.deviceId, startOfDay(now))

        // همین دانشجو با همین گوشی، یا قبلاً استاد او را دستی حاضر زده
        if (today.any {
                it.studentID == payload.studentId &&
                    (it.deviceID == payload.deviceId || it.deviceID == manualDeviceId(payload.studentId))
            }
        ) {
            return SaveResult.ALREADY_REGISTERED
        }
        // همین کد دانشجویی با گوشی دیگر، یا همین گوشی با کد دانشجویی دیگر (احتمال ثبت به‌جای دیگری)
        if (today.isNotEmpty()) {
            return SaveResult.CONFLICT
        }

        responseDao.insertResponse(
            dateResponse(
                studentID = payload.studentId,
                name = payload.name,
                deviceID = payload.deviceId,
                timestamp = now,
                date = PersianDate.formatDateTime(now),
                courseId = courseId,
                // ثبت دستی سیگنالی ندارد
                rssi = payload.rssi.takeIf { payload.deviceId != manualDeviceId(payload.studentId) }
            )
        )
        return SaveResult.SAVED
    }
}
