package com.ali_1200.presence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

// یک درس در صفحه دانشجو به همراه وضعیت امروز و تعداد جلسات حاضر
data class MyCourseItem(
    val course: Course,
    val registeredToday: Boolean,
    val attendedSessions: Int
)

// داده‌ها و کارهای مشترک صفحه‌های دانشجو
class StudentViewModel(app: Application) : AndroidViewModel(app) {
    private val loginRepository = LoginRepository(app)
    private val courseRepository = CourseRepository(app)

    val client = AttendanceClient(app)
    val checkInState: LiveData<AttendanceClient.State> = client.state

    val login: LiveData<Logingdata?> = liveData { emit(loginRepository.getSavedLoginData()) }
    val courses: LiveData<List<Course>> = courseRepository.courses
    private val myAttendance: LiveData<List<MyAttendance>> = courseRepository.myAttendance

    // تا رسیدن LiveData، حضور تازه‌ثبت‌شده همین حالا هم حساب شود
    @Volatile
    private var justRegistered: List<MyAttendance> = emptyList()

    val courseItems: LiveData<List<MyCourseItem>> = MediatorLiveData<List<MyCourseItem>>().apply {
        fun update() {
            val attendance = myAttendance.value.orEmpty()
            value = courses.value.orEmpty().map { course ->
                MyCourseItem(
                    course = course,
                    registeredToday = isRegisteredToday(course.id),
                    attendedSessions = attendance.filter { it.courseId == course.id }
                        .map { PersianDate.fromTimestamp(it.timestamp) }
                        .distinct()
                        .size
                )
            }
        }
        addSource(courses) { update() }
        addSource(myAttendance) { update() }
    }

    fun isRegisteredToday(courseId: Long): Boolean {
        val today = startOfDay(System.currentTimeMillis())
        return (myAttendance.value.orEmpty() + justRegistered)
            .any { it.courseId == courseId && it.timestamp >= today }
    }

    // اگر نمی‌شود ثبت حضور را شروع کرد، دلیلش را برمی‌گرداند
    fun checkInBlocker(): String? {
        val list = courses.value.orEmpty()
        return when {
            login.value == null -> "اطلاعات ورود پیدا نشد"
            list.isEmpty() -> "ابتدا در «درس‌های من» با کد کلاس، درس خود را اضافه کنید"
            list.all { isRegisteredToday(it.id) } -> "حضور همه درس‌های شما امروز ثبت شده است"
            else -> null
        }
    }

    fun startCheckIn() {
        val user = login.value ?: return
        client.start(
            AttendanceClient.Request(
                login = user,
                courses = courses.value.orEmpty(),
                isRegisteredToday = ::isRegisteredToday,
                onRegistered = { course ->
                    val now = System.currentTimeMillis()
                    justRegistered = justRegistered + MyAttendance(courseId = course.id, timestamp = now)
                    viewModelScope.launch { courseRepository.recordMyAttendance(course.id, now) }
                }
            )
        )
    }

    // نتیجه افزودن درس: null یعنی موفق، وگرنه پیام خطا
    suspend fun joinCourse(code: String, name: String): String? {
        val classCode = AttendanceCrypto.normalizeClassCode(code)
            ?: return "کد کلاس باید ${AttendanceCrypto.CODE_LENGTH} حرف و عدد باشد (مثل ABCDE-FGHJK)"
        val courseName = name.ifBlank { "درس ${courses.value.orEmpty().size + 1}" }
        return if (courseRepository.joinCourse(courseName, classCode) == null) "این درس قبلاً اضافه شده است" else null
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch { courseRepository.deleteCourse(course.id) }
    }

    override fun onCleared() {
        client.cancel()
    }
}
