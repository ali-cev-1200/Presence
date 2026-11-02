package com.ali_1200.presence

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class AttendanceExporterTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int = 10): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0)
        }.timeInMillis

    private fun record(id: String, name: String, timestamp: Long) =
        dateResponse(studentID = id, name = name, deviceID = "d-$id", timestamp = timestamp)

    @Test
    fun buildsMatrixWithCountsAndWarnings() {
        val roster = listOf(Student("1001", "علی"), Student("1002", "مریم"))
        val days = (1..4).map { at(2026, 11, it) } // ۴ جلسه
        val records = days.map { record("1001", "علی", it) } +
            listOf(record("1002", "مریم", days[0]), record("2000", "مهمان", days[1]))

        val rows = AttendanceExporter.buildRows(roster, records)

        assertEquals(
            listOf("ردیف", "کد دانشجویی", "نام", "1405/08/10", "1405/08/11", "1405/08/12", "1405/08/13",
                "تعداد حضور", "تعداد غیبت", "وضعیت"),
            rows[0]
        )
        assertEquals(listOf(1, "1001", "علی", "حاضر", "حاضر", "حاضر", "حاضر", 4, 0, ""), rows[1])
        assertEquals(
            listOf(2, "1002", "مریم", "حاضر", "غایب", "غایب", "غایب", 1, 3, "در آستانه حد مجاز غیبت"),
            rows[2]
        )
        assertEquals(
            listOf(3, "2000", "مهمان", "غایب", "حاضر", "غایب", "غایب", 1, 3,
                "در آستانه حد مجاز غیبت - خارج از لیست کلاس"),
            rows[3]
        )
    }

    @Test
    fun twoRecordsOnSameDayAreOneSession() {
        val records = listOf(
            record("1001", "علی", at(2026, 11, 2, 9)),
            record("1002", "مریم", at(2026, 11, 2, 15))
        )
        val rows = AttendanceExporter.buildRows(emptyList(), records)
        assertEquals(listOf(1, "1001", "علی", "حاضر", 1, 0, ""), rows[1])
    }
}
