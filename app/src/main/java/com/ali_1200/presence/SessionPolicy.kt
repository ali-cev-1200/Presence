package com.ali_1200.presence

import android.content.Context

// محدوده کلاس بر اساس قدرت سیگنال بلوتوث (RSSI، دسی‌بل-میلی‌وات)؛ هرچه منفی‌تر، دورتر
// عددها تقریبی‌اند و به گوشی و دیوارها بستگی دارند؛ بعد از آزمون میدانی تنظیم شوند
enum class RangeLevel(val title: String, val minRssi: Int?) {
    OFF("بدون محدودیت", null),
    NORMAL("کلاس معمولی (حدود ۱۰ متر)", -82),
    SMALL("کلاس کوچک (حدود ۵ متر)", -72);

    companion object {
        fun from(name: String?): RangeLevel = entries.firstOrNull { it.name == name } ?: OFF
    }
}

// تنظیمات جلسه که داخل چالش امضاشده به گوشی دانشجو می‌رسد؛ پس گوشی جعلی نمی‌تواند آن را عوض کند
// قالب: پرچم‌ها (۱ بایت) | کمترین RSSI (۱ بایت، علامت‌دار)
data class SessionPolicy(val minRssi: Int? = null) {
    fun toBytes(): ByteArray = byteArrayOf(
        (if (minRssi != null) FLAG_RSSI else 0).toByte(),
        (minRssi ?: 0).toByte()
    )

    // دانشجویی که سیگنالش ضعیف‌تر از حد است، احتمالاً بیرون کلاس است
    fun allowsRssi(rssi: Int): Boolean = minRssi == null || rssi >= minRssi

    companion object {
        const val SIZE = 2
        private const val FLAG_RSSI = 1

        fun fromBytes(bytes: ByteArray): SessionPolicy {
            val flags = bytes[0].toInt()
            return SessionPolicy(minRssi = if (flags and FLAG_RSSI != 0) bytes[1].toInt() else null)
        }
    }
}

// انتخاب استاد برای جلسه‌ها؛ تغییرش از چالش بعدی اثر می‌کند
class SessionSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("presence", Context.MODE_PRIVATE)

    var range: RangeLevel
        get() = RangeLevel.from(prefs.getString("session_range", null))
        set(value) = prefs.edit().putString("session_range", value.name).apply()

    fun policy(): SessionPolicy = SessionPolicy(minRssi = range.minRssi)
}

// برچسب سیگنال برای لیست حاضرین استاد
fun signalLabel(rssi: Int): String = when {
    rssi >= -65 -> "سیگنال قوی"
    rssi >= -80 -> "سیگنال متوسط"
    else -> "سیگنال ضعیف"
}
