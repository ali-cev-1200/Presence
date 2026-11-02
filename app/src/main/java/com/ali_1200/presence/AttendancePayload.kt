package com.ali_1200.presence

import org.json.JSONException
import org.json.JSONObject

// داده‌ای که دانشجو برای استاد می‌فرستد؛ به‌صورت JSON تا نام‌های چندکلمه‌ای هم درست منتقل شوند
// proof اثبات HMAC روی nonce استاد است (AttendanceCrypto.studentProof)
// rssi قدرت سیگنال گوشی استاد از دید گوشی دانشجو (دسی‌بل-میلی‌وات)؛ qr متن QR چرخشی اسکن‌شده (اگر لازم بوده)
data class AttendancePayload(
    val name: String,
    val studentId: String,
    val deviceId: String,
    val proof: String,
    val rssi: Int,
    val qr: String = ""
) {
    fun toBytes(): ByteArray = JSONObject()
        .put("v", BleConstants.PROTOCOL_VERSION)
        .put("n", name)
        .put("s", studentId)
        .put("d", deviceId)
        .put("p", proof)
        .put("r", rssi)
        .put("q", qr)
        .toString()
        .toByteArray(Charsets.UTF_8)

    companion object {
        // در صورت خراب یا ناقص بودن داده null برمی‌گرداند
        fun fromBytes(bytes: ByteArray): AttendancePayload? = try {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            if (json.optInt("v") != BleConstants.PROTOCOL_VERSION) throw JSONException("نسخه دیگر پروتکل")
            AttendancePayload(
                json.getString("n").trim(),
                json.getString("s").trim(),
                json.getString("d").trim(),
                json.getString("p").trim(),
                json.getInt("r"),
                json.optString("q").trim()
            ).takeIf {
                it.name.isNotEmpty() && it.studentId.isNotEmpty() && it.deviceId.isNotEmpty() && it.proof.isNotEmpty()
            }
        } catch (e: JSONException) {
            null
        }

        // نسخه پروتکل پیام دانشجو (برای پیام «نسخه برنامه فرق دارد»)؛ پیام نسخه‌های قدیمی «v» ندارد
        fun versionOf(bytes: ByteArray): Int = try {
            JSONObject(bytes.toString(Charsets.UTF_8)).optInt("v")
        } catch (e: JSONException) {
            0
        }
    }
}
