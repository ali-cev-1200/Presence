package com.ali_1200.presence

import java.util.Calendar
import java.util.Locale

// تاریخ شمسی بدون کتابخانه بیرونی
data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    // مثل 1405/08/11؛ با اعداد لاتین تا در Excel درست مرتب شود (Locale.US چون زبان برنامه فارسی است)
    override fun toString(): String = String.format(Locale.US, "%04d/%02d/%02d", year, month, day)

    // مثل «۱۱ آبان ۱۴۰۵» برای نمایش
    fun toLongText(): String = PersianDate.toPersianDigits("$day ${PersianDate.MONTH_NAMES[month - 1]} $year")
}

object PersianDate {
    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    // به ترتیب Calendar.SUNDAY (۱) تا Calendar.SATURDAY (۷)
    private val WEEKDAY_NAMES = listOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")

    private val GREGORIAN_DAYS_BEFORE_MONTH = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    // الگوریتم رایج تبدیل میلادی به شمسی (بر پایه چرخه ۳۳ ساله)
    fun fromGregorian(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 +
            gd + GREGORIAN_DAYS_BEFORE_MONTH[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        return if (days < 186) {
            JalaliDate(jy, 1 + days / 31, 1 + days % 31)
        } else {
            JalaliDate(jy, 7 + (days - 186) / 30, 1 + (days - 186) % 30)
        }
    }

    fun fromTimestamp(timestamp: Long): JalaliDate {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return fromGregorian(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun formatTime(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format(Locale.US, "%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    // مثل «سه‌شنبه ۱۱ آبان ۱۴۰۵، ساعت ۱۰:۳۲» برای نمایش
    fun formatLong(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val weekday = WEEKDAY_NAMES[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        return "$weekday ${fromTimestamp(timestamp).toLongText()}، ساعت ${toPersianDigits(formatTime(timestamp))}"
    }

    // مثل «1405/08/11 - 10:32» برای ذخیره و خروجی
    fun formatDateTime(timestamp: Long): String = "${fromTimestamp(timestamp)} - ${formatTime(timestamp)}"

    // ۰ تا ۹ فارسی برای نمایش در برنامه
    fun toPersianDigits(text: String): String = buildString {
        text.forEach { c -> append(if (c in '0'..'9') '۰' + (c - '0') else c) }
    }

    // اعداد فارسی و عربی را به لاتین تبدیل می‌کند (کد دانشجویی تایپ‌شده با کیبورد فارسی یا از فایل Excel)
    fun normalizeDigits(text: String): String = buildString {
        text.forEach { c ->
            append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    else -> c
                }
            )
        }
    }
}
