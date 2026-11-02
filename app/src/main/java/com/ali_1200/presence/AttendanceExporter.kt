package com.ali_1200.presence

// جدول خروجی Excel: هر سطر یک دانشجو، هر ستون یک جلسه (روزی که حداقل یک نفر حاضر بوده)
object AttendanceExporter {
    const val ALLOWED_ABSENCES = AttendanceStats.ALLOWED_ABSENCES

    fun buildRows(roster: List<Student>, records: List<dateResponse>): List<List<Any>> {
        val stats = AttendanceStats.compute(roster, records)

        val header = listOf("ردیف", "کد دانشجویی", "نام") + stats.sessions +
            listOf("تعداد حضور", "تعداد غیبت", "وضعیت")

        val rows = stats.students.mapIndexed { index, student ->
            val status = listOfNotNull(
                when {
                    student.overLimit -> "غیبت بیش از حد مجاز"
                    student.atLimit -> "در آستانه حد مجاز غیبت"
                    else -> null
                },
                if (!student.inRoster) "خارج از لیست کلاس" else null
            ).joinToString(" - ")

            listOf<Any>(index + 1, student.studentId, student.name) +
                stats.sessions.map { if (it in student.attendedSessions) "حاضر" else "غایب" } +
                listOf<Any>(student.presentCount, student.absentCount, status)
        }

        return listOf(header) + rows
    }
}
