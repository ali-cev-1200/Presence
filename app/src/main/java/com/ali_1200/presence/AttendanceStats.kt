package com.ali_1200.presence

// آمار حضور یک درس: جلسه‌ها (روزهایی که حداقل یک نفر حاضر بوده) و وضعیت هر دانشجو
data class StudentStat(
    val studentId: String,
    val name: String,
    val attendedSessions: Set<String>,
    val presentCount: Int,
    val absentCount: Int,
    val inRoster: Boolean
) {
    // قانون رایج دانشگاه‌ها: غیبت بیش از ۳/۱۶ جلسات (۳ جلسه از ۱۶ جلسه ترم)
    val overLimit: Boolean get() = absentCount > AttendanceStats.ALLOWED_ABSENCES
    val atLimit: Boolean get() = absentCount == AttendanceStats.ALLOWED_ABSENCES
}

data class CourseStats(
    val sessions: List<String>,
    val students: List<StudentStat>
) {
    // میانگین درصد حضور دانشجوهای لیست (یا همه، اگر لیست کلاس وارد نشده)
    val averageAttendancePercent: Int
        get() {
            if (sessions.isEmpty() || students.isEmpty()) return 0
            return students.sumOf { it.presentCount } * 100 / (students.size * sessions.size)
        }

    val atRiskCount: Int get() = students.count { it.overLimit || it.atLimit }
}

object AttendanceStats {
    const val ALLOWED_ABSENCES = 3

    fun compute(roster: List<Student>, records: List<dateResponse>): CourseStats {
        val sessions = records.map { sessionOf(it) }.distinct().sorted()
        val attendedBy = records.groupBy { it.studentID }
            .mapValues { (_, list) -> list.map { sessionOf(it) }.toSet() }

        // اول دانشجوهای لیست کلاس، بعد کسانی که حاضر شده‌اند ولی در لیست نیستند
        val names = LinkedHashMap<String, String>()
        roster.forEach { names[it.studentID] = it.name }
        records.sortedBy { it.timestamp }.forEach { names.putIfAbsent(it.studentID, it.name) }
        val rosterIds = roster.map { it.studentID }.toSet()

        val students = names.map { (studentId, name) ->
            val attended = attendedBy[studentId].orEmpty()
            val presentCount = sessions.count { it in attended }
            StudentStat(
                studentId = studentId,
                name = name,
                attendedSessions = attended,
                presentCount = presentCount,
                absentCount = sessions.size - presentCount,
                inRoster = rosterIds.isEmpty() || studentId in rosterIds
            )
        }
        return CourseStats(sessions, students)
    }

    private fun sessionOf(record: dateResponse): String = PersianDate.fromTimestamp(record.timestamp).toString()
}
