package com.ali_1200.presence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap

// سرویس پیش‌زمینه (Foreground Service) گوشی استاد در زمان حضور و غیاب:
// با قفل شدن گوشی یا رفتن به برنامه دیگر، اندروید برنامه را نمی‌بندد و پخش بلوتوث ادامه پیدا می‌کند.
// اعلان آن نام درس و تعداد حاضرین را نشان می‌دهد و دکمه «توقف» دارد.
class AttendanceService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "attendance_session"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.ali_1200.presence.STOP_ATTENDANCE"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AttendanceService::class.java))
        }
    }

    private val server by lazy { AttendanceServer.get(this) }
    private val responseRepository by lazy { ResponseRepository(this) }

    private val runningCourse = MutableLiveData<Course?>()
    private var presentCount = 0

    override fun onCreate() {
        super.onCreate()
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("حضور و غیاب در حال اجرا")
                .setDescription("تا وقتی گوشی استاد سیگنال کلاس را پخش می‌کند نمایش داده می‌شود")
                .build()
        )

        server.state.observe(this) { state ->
            when (state) {
                is AttendanceServer.State.Running ->
                    if (runningCourse.value?.id != state.course.id) runningCourse.value = state.course
                is AttendanceServer.State.Starting -> Unit
                // حضور و غیاب تمام شد (از داخل برنامه، از اعلان یا خاموش شدن بلوتوث)
                else -> stopSelfNow()
            }
        }
        // حضورهای امروز درس در حال پخش؛ با هر حضور تازه اعلان به‌روز می‌شود
        runningCourse.switchMap { course ->
            if (course == null) MutableLiveData(emptyList())
            else responseRepository.todayResponses(course.id, System.currentTimeMillis())
        }.observe(this) { records ->
            presentCount = records.size
            updateNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // اندروید باید ظرف چند ثانیه اعلان را ببیند، وگرنه برنامه را می‌بندد؛
        // برای همین حتی پیش از توقف هم اول startForeground صدا زده می‌شود
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        if (intent?.action == ACTION_STOP) server.stop()
        // ناظر وضعیت سرور از اینجا فعال می‌شود و اگر سرور متوقف شده باشد سرویس را می‌بندد
        super.onStartCommand(intent, flags, startId)
        // اگر اندروید برنامه را بست، سرویس دوباره ساخته نمی‌شود؛ سرور بلوتوث هم دیگر وجود ندارد
        return START_NOT_STICKY
    }

    private fun stopSelfNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // بدون مجوز اعلان (اندروید ۱۳ به بعد) سرویس باز هم کار می‌کند، فقط اعلان به‌روز نمی‌شود
    private fun updateNotification() {
        if (!server.isActive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_bluetooth)
        .setContentTitle(runningCourse.value?.let { "حضور و غیاب «${it.name}»" } ?: "حضور و غیاب در حال اجرا")
        .setContentText("حاضر امروز: ${PersianDate.toPersianDigits(presentCount.toString())} نفر")
        .setColor(ContextCompat.getColor(this, R.color.primary))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .addAction(
            R.drawable.ic_stop, "توقف",
            PendingIntent.getService(
                this, 1,
                Intent(this, AttendanceService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()
}
