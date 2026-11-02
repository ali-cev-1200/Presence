package com.ali_1200.presence

import org.json.JSONException
import org.json.JSONObject

// داده‌ای که دانشجو برای استاد می‌فرستد؛ به‌صورت JSON تا نام‌های چندکلمه‌ای هم درست منتقل شوند
// proof اثبات HMAC روی nonce استاد است (AttendanceCrypto.studentProof)
data class AttendancePayload(
    val name: String,
    val studentId: String,
    val deviceId: String,
    val proof: String
) {
    fun toBytes(): ByteArray = JSONObject()
        .put("n", name)
        .put("s", studentId)
        .put("d", deviceId)
        .put("p", proof)
        .toString()
        .toByteArray(Charsets.UTF_8)

    companion object {
        // در صورت خراب یا ناقص بودن داده null برمی‌گرداند
        fun fromBytes(bytes: ByteArray): AttendancePayload? = try {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            AttendancePayload(
                json.getString("n").trim(),
                json.getString("s").trim(),
                json.getString("d").trim(),
                json.getString("p").trim()
            ).takeIf {
                it.name.isNotEmpty() && it.studentId.isNotEmpty() && it.deviceId.isNotEmpty() && it.proof.isNotEmpty()
            }
        } catch (e: JSONException) {
            null
        }
    }
}
