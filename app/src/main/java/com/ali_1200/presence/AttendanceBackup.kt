package com.ali_1200.presence

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// فایل پشتیبان استاد (JSON): همه درس‌ها با کد کلاس، لیست کلاس، حضورها و کلید امضای استاد؛
// برای عوض کردن گوشی یا نگه داشتن اطلاعات ترم، بدون اینترنت و بدون سرور
object AttendanceBackup {
    const val MIME_TYPE = "application/json"
    private const val FORMAT = "presence-backup"
    private const val VERSION = 2 // نسخه ۲: کلید امضای استاد (نسخه ۱ هم خوانده می‌شود)
    private val BYTE_ORDER_MARK = Char(0xFEFF).toString() // بعضی ویرایشگرها اول فایل می‌گذارند

    data class CourseData(val course: Course, val students: List<Student>, val records: List<dateResponse>)

    // بدون کلید امضا، دانشجوهایی که کلید قبلی را ذخیره کرده‌اند گوشی جدید را نمی‌پذیرند
    data class Backup(val courses: List<CourseData>, val teacherKey: TeacherSignature.TeacherKey?)

    class InvalidBackupException(message: String) : Exception(message)

    fun encode(backup: Backup, createdAt: Long): String {
        val courses = backup.courses
        val array = JSONArray()
        courses.forEach { data ->
            array.put(
                JSONObject()
                    .put("name", data.course.name)
                    .put("classCode", data.course.classCode)
                    .put("students", JSONArray().apply {
                        data.students.forEach { put(JSONObject().put("id", it.studentID).put("name", it.name)) }
                    })
                    .put("records", JSONArray().apply {
                        data.records.forEach {
                            put(
                                JSONObject()
                                    .put("id", it.studentID)
                                    .put("name", it.name)
                                    .put("device", it.deviceID)
                                    .put("time", it.timestamp)
                                    .put("date", it.date.orEmpty())
                                    .apply { it.rssi?.let { rssi -> put("rssi", rssi) } }
                            )
                        }
                    })
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", createdAt)
            .put("courses", array)
            .apply {
                backup.teacherKey?.let {
                    put("signingKey", JSONObject().put("private", it.privateKey).put("public", it.publicKey))
                }
            }
            .toString(2)
    }

    // شناسه درس‌ها در فایل نیست؛ هنگام بازیابی دیتابیس شناسه تازه می‌دهد (courseId همه صفر است)
    fun decode(text: String): Backup {
        val root = try {
            JSONObject(text.removePrefix(BYTE_ORDER_MARK))
        } catch (e: JSONException) {
            throw InvalidBackupException("این فایل پشتیبان برنامه نیست")
        }
        if (root.optString("format") != FORMAT) throw InvalidBackupException("این فایل پشتیبان برنامه نیست")
        if (root.optInt("version") > VERSION) {
            throw InvalidBackupException("این پشتیبان با نسخه جدیدتر برنامه ساخته شده است")
        }

        try {
            val courses = root.getJSONArray("courses")
            val courseData = (0 until courses.length()).map { i ->
                val json = courses.getJSONObject(i)
                val name = json.getString("name").trim()
                val code = AttendanceCrypto.normalizeClassCode(json.getString("classCode"))
                if (name.isEmpty() || code == null) throw InvalidBackupException("اطلاعات درس شماره ${i + 1} خراب است")

                val students = json.getJSONArray("students").objects().map {
                    Student(it.getString("id"), it.getString("name"))
                }.distinctBy { it.studentID }
                val records = json.getJSONArray("records").objects().map {
                    dateResponse(
                        studentID = it.getString("id"),
                        name = it.getString("name"),
                        deviceID = it.getString("device"),
                        timestamp = it.getLong("time"),
                        date = it.optString("date"),
                        rssi = if (it.has("rssi")) it.getInt("rssi") else null
                    )
                }
                CourseData(Course(name = name, classCode = code), students, records)
            }
            val teacherKey = root.optJSONObject("signingKey")?.let {
                TeacherSignature.TeacherKey(it.getString("private"), it.getString("public"))
                    .takeIf(TeacherSignature::isValidKey)
                    ?: throw InvalidBackupException("کلید امضای داخل فایل پشتیبان خراب است")
            }
            return Backup(courseData, teacherKey)
        } catch (e: JSONException) {
            throw InvalidBackupException("فایل پشتیبان ناقص یا خراب است")
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
