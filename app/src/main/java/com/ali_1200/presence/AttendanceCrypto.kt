package com.ali_1200.presence

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// رمزنگاری حضور: کد کلاس فقط بین استاد و دانشجوهای همان کلاس مشترک است
// - استاد با اثبات (HMAC) روی nonce نشان می‌دهد گوشی استاد واقعی است
// - دانشجو با اثبات روی همان nonce نشان می‌دهد کد کلاس را دارد و پیامش تکراری (replay) نیست
object AttendanceCrypto {
    // بدون حروف و اعداد شبیه به هم (O/0 و I/1)
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val CODE_LENGTH = 10
    const val NONCE_SIZE = 16
    const val PROOF_SIZE = 16

    private val TEACHER_LABEL = "presence-teacher".toByteArray()
    private val STUDENT_LABEL = "presence-student".toByteArray()
    private val random = SecureRandom()

    fun generateClassCode(): String =
        (1..CODE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    // ورودی کاربر را یکدست می‌کند ("abcde-fghjk" ← "ABCDEFGHJK")؛ اگر نامعتبر باشد null
    fun normalizeClassCode(input: String): String? {
        val code = PersianDate.normalizeDigits(input).uppercase().filter { it.isLetterOrDigit() }
        return code.takeIf { it.length == CODE_LENGTH && it.all { c -> c in ALPHABET } }
    }

    fun formatClassCode(code: String): String = code.chunked(5).joinToString("-")

    fun newNonce(): ByteArray = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }

    fun teacherProof(classCode: String, nonce: ByteArray): ByteArray =
        mac(classCode, TEACHER_LABEL, nonce)

    fun verifyTeacherProof(classCode: String, nonce: ByteArray, proof: ByteArray): Boolean =
        MessageDigest.isEqual(teacherProof(classCode, nonce), proof)

    fun studentProof(classCode: String, nonce: ByteArray, name: String, studentId: String, deviceId: String): String =
        Base64.getEncoder().encodeToString(
            mac(classCode, STUDENT_LABEL, nonce, name.toByteArray(), studentId.toByteArray(), deviceId.toByteArray())
        )

    fun verifyStudentProof(classCode: String, nonce: ByteArray, payload: AttendancePayload): Boolean {
        val expected = studentProof(classCode, nonce, payload.name, payload.studentId, payload.deviceId)
        return MessageDigest.isEqual(expected.toByteArray(), payload.proof.toByteArray())
    }

    // هر بخش با طولش وارد HMAC می‌شود تا مرز بین بخش‌ها قابل جابه‌جایی نباشد
    private fun mac(classCode: String, vararg parts: ByteArray): ByteArray {
        val key = MessageDigest.getInstance("SHA-256").digest(classCode.toByteArray())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach {
            mac.update(ByteBuffer.allocate(4).putInt(it.size).array())
            mac.update(it)
        }
        return mac.doFinal().copyOf(PROOF_SIZE)
    }
}
