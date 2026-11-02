package com.ali_1200.presence

import java.util.Calendar

// ساعت ۰۰:۰۰ همان روز؛ برای تشخیص «امروز» در هر دو طرف استاد و دانشجو
fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
