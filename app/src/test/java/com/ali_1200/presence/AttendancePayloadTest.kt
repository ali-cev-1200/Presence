package com.ali_1200.presence

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// org.json در تست معمولی فقط نسخه خالی اندروید است؛ Robolectric نسخه واقعی را می‌دهد
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AttendancePayloadTest {
    // پیام نسخه قدیمی برنامه دانشجو (بدون «v») پذیرفته نمی‌شود ولی نسخه‌اش تشخیص داده می‌شود
    @Test
    fun payloadVersionIsChecked() {
        val payload = AttendancePayload("علی", "40012345", "d1", "proof", -60)
        val bytes = payload.toBytes()
        assertEquals(payload, AttendancePayload.fromBytes(bytes))
        assertEquals(BleConstants.PROTOCOL_VERSION, AttendancePayload.versionOf(bytes))

        val old = """{"n":"علی","s":"40012345","d":"d1","p":"proof"}""".toByteArray()
        assertEquals(null, AttendancePayload.fromBytes(old))
        assertEquals(0, AttendancePayload.versionOf(old))
    }
}
