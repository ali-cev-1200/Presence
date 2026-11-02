package com.ali_1200.presence

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// فایل پشتیبان استاد (JSON): همه درس‌ها با کد کلاس، لیست کلاس و حضورها؛
// برای عوض کردن گوشی یا نگه داشتن اطلاعات ترم، بدون اینترنت و بدون سرور
object AttendanceBackup {
    const val MIME_TYPE = "application/json"
    private const val FORMAT = "presence-backup"
    private const val VERSION = 1
    private val BYTE_ORDER_MARK = Char(0xFEFF).toString() // بعضی ویرایشگرها اول فایل می‌گذارند

    data class CourseData(val course: Course, val students: List<Student>, val records: List<dateResponse>)

    class InvalidBackupException(message: String) : Exception(message)

    fun encode(courses: List<CourseData>, createdAt: Long): String {
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
            .toString(2)
    }

    // شناسه درس‌ها در فایل نیست؛ هنگام بازیابی دیتابیس شناسه تازه می‌دهد (courseId همه صفر است)
    fun decode(text: String): List<CourseData> {
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
            return (0 until courses.length()).map { i ->
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
                        date = it.optString("date")
                    )
                }
                CourseData(Course(name = name, classCode = code), students, records)
            }
        } catch (e: JSONException) {
            throw InvalidBackupException("فایل پشتیبان ناقص یا خراب است")
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
