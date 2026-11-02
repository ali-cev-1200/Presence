package com.ali_1200.presence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

// داده‌ها و کارهای مشترک همه صفحه‌های استاد؛ تا وقتی صفحه برنامه باز است زنده می‌ماند
// پخش بلوتوث مال کل برنامه است (AttendanceServer.get) و با بسته شدن صفحه هم ادامه پیدا می‌کند
class TeacherViewModel(app: Application) : AndroidViewModel(app) {
    private val courseRepository = CourseRepository(app)
    private val responseRepository = ResponseRepository(app)
    private val rosterRepository = RosterRepository(app)

    val server = AttendanceServer.get(app)

    // آمار خلاصه هر درس برای صفحه درس‌ها
    data class CourseSummary(val course: Course, val students: Int, val sessions: Int, val selected: Boolean)

    val courses: LiveData<List<Course>> = courseRepository.courses

    private val selectedId = MutableLiveData(courseRepository.selectedCourseId)

    // درس انتخاب‌شده؛ اگر حذف شده باشد اولین درس
    val selectedCourse: LiveData<Course?> = MediatorLiveData<Course?>().apply {
        fun update() {
            val list = courses.value ?: return
            value = list.firstOrNull { it.id == selectedId.value } ?: list.firstOrNull()
        }
        addSource(courses) { update() }
        addSource(selectedId) { update() }
    }.distinctUntilChanged()

    val courseSummaries: LiveData<List<CourseSummary>> = MediatorLiveData<List<CourseSummary>>().apply {
        fun update() {
            val list = courses.value ?: return
            val students = courseRepository.rosterCounts.value.orEmpty().associate { it.courseId to it.count }
            val sessions = courseRepository.sessionCounts.value.orEmpty().associate { it.courseId to it.count }
            val selected = selectedCourse.value?.id
            value = list.map {
                CourseSummary(it, students[it.id] ?: 0, sessions[it.id] ?: 0, it.id == selected)
            }
        }
        addSource(courses) { update() }
        addSource(courseRepository.rosterCounts) { update() }
        addSource(courseRepository.sessionCounts) { update() }
        addSource(selectedCourse) { update() }
    }

    // روز جاری؛ اگر برنامه بعد از نیمه‌شب هم باز بماند refreshDay آن را عوض می‌کند
    private val day = MutableLiveData(startOfDay(System.currentTimeMillis()))

    private data class CourseDay(val courseId: Long?, val day: Long)

    private val courseDay: LiveData<CourseDay> = MediatorLiveData<CourseDay>().apply {
        fun update() {
            value = CourseDay(selectedCourse.value?.id, day.value ?: 0)
        }
        addSource(selectedCourse) { update() }
        addSource(day) { update() }
    }.distinctUntilChanged()

    val todayRecords: LiveData<List<dateResponse>> = courseDay.switchMap { (courseId, day) ->
        if (courseId == null) MutableLiveData(emptyList()) else responseRepository.todayResponses(courseId, day)
    }

    val roster: LiveData<List<Student>> = selectedCourse.switchMap { course ->
        if (course == null) MutableLiveData(emptyList()) else rosterRepository.students(course.id)
    }

    private val allRecords: LiveData<List<dateResponse>> = selectedCourse.switchMap { course ->
        if (course == null) MutableLiveData(emptyList()) else responseRepository.allResponses(course.id)
    }

    // آمار کامل درس انتخاب‌شده برای صفحه گزارش
    val stats: LiveData<CourseStats> = MediatorLiveData<CourseStats>().apply {
        fun update() {
            value = AttendanceStats.compute(roster.value.orEmpty(), allRecords.value.orEmpty())
        }
        addSource(roster) { update() }
        addSource(allRecords) { update() }
    }

    // غایبین امروز = دانشجوهای لیست کلاس که هنوز حاضر نشده‌اند
    val absentToday: LiveData<List<Student>> = MediatorLiveData<List<Student>>().apply {
        fun update() {
            val present = todayRecords.value.orEmpty().map { it.studentID }.toSet()
            value = roster.value.orEmpty().filter { it.studentID !in present }
        }
        addSource(todayRecords) { update() }
        addSource(roster) { update() }
    }

    val rosterIds: LiveData<Set<String>> = roster.map { list -> list.map { it.studentID }.toSet() }

    fun refreshDay() {
        val today = startOfDay(System.currentTimeMillis())
        if (day.value != today) day.value = today
    }

    fun selectCourse(course: Course) {
        // پخش بلوتوث مال درس قبلی است؛ با عوض شدن درس متوقف می‌شود
        if (server.isActive && selectedCourse.value?.id != course.id) server.stop()
        courseRepository.selectedCourseId = course.id
        selectedId.value = course.id
    }

    fun createCourse(name: String) {
        viewModelScope.launch {
            val id = courseRepository.createCourse(name)
            courseRepository.selectedCourseId = id
            if (server.isActive) server.stop()
            selectedId.value = id
        }
    }

    fun deleteCourse(course: Course) {
        if (selectedCourse.value?.id == course.id && server.isActive) server.stop()
        viewModelScope.launch { courseRepository.deleteCourse(course.id) }
    }

    fun replaceRoster(courseId: Long, students: List<Student>) {
        viewModelScope.launch { rosterRepository.replaceAll(courseId, students) }
    }

    fun deleteRecord(record: dateResponse) {
        viewModelScope.launch { responseRepository.delete(record) }
    }

    fun markPresentManually(student: Student) {
        val course = selectedCourse.value ?: return
        viewModelScope.launch { responseRepository.saveManual(course.id, student, System.currentTimeMillis()) }
    }

    fun startServer() {
        val course = selectedCourse.value ?: return
        server.start(course)
        // اگر بلوتوث خاموش بود start فوراً Failed می‌شود و سرویسی لازم نیست
        if (server.isActive) AttendanceService.start(getApplication())
    }

    fun stopServer() {
        server.stop()
    }

    suspend fun exportRows(course: Course): List<List<Any>> =
        AttendanceExporter.buildRows(
            rosterRepository.getStudentsOnce(course.id),
            responseRepository.getAllOnce(course.id)
        )
}
