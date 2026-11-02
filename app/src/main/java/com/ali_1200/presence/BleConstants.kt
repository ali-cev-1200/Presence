package com.ali_1200.presence

import java.util.UUID

// مقادیر مشترک بین گوشی استاد (Advertiser) و گوشی دانشجو (Scanner)
object BleConstants {
    // UUID اختصاصی برنامه؛ UUIDهای استاندارد (مثل 180D ضربان قلب) را دستگاه‌های دیگر هم پخش می‌کنند
    val SERVICE_UUID: UUID = UUID.fromString("6f1c2a4e-8b3d-4c7a-9e51-2d7f0b9a3c11")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("6f1c2a4e-8b3d-4c7a-9e51-2d7f0b9a3c12")

    // دانشجو قبل از ارسال، nonce و اثبات استاد را از اینجا می‌خواند
    val CHALLENGE_UUID: UUID = UUID.fromString("6f1c2a4e-8b3d-4c7a-9e51-2d7f0b9a3c13")

    // MTU پیش‌فرض فقط ۲۰ بایت داده جا می‌دهد؛ برای نام‌های فارسی MTU بزرگ‌تر درخواست می‌شود
    const val REQUESTED_MTU = 185

    // کدهای خطای برنامه (بازه 0x80 تا 0x9F) که استاد به دانشجو برمی‌گرداند
    // کد دانشجویی یا گوشی امروز با مشخصات دیگری ثبت شده
    const val STATUS_CONFLICT = 0x80
    // کد کلاس اشتباه است یا nonce منقضی/تکراری است
    const val STATUS_AUTH_FAILED = 0x81
    // سیگنال دانشجو ضعیف‌تر از محدوده‌ای است که استاد انتخاب کرده
    const val STATUS_TOO_FAR = 0x82
}
