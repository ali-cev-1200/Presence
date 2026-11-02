package com.ali_1200.presence

import java.util.concurrent.ConcurrentHashMap

// سمت استاد: به هر گوشی دانشجو یک nonce یک‌بارمصرف (با اثبات HMAC و امضای استاد) می‌دهد
// و اثبات دانشجو را با آن بررسی می‌کند
class AttendanceVerifier(private val classCode: String, private val teacherKey: TeacherSignature.TeacherKey) {
    private class Challenge(val nonce: ByteArray, val response: ByteArray, val issuedAt: Long)

    private val challenges = ConcurrentHashMap<String, Challenge>()

    companion object {
        const val CHALLENGE_LIFETIME: Long = 60_000 // nonce فقط یک دقیقه معتبر است
    }

    // پاسخ کامل خواندن چالش (TeacherChallenge)
    // خواندن‌های تکه‌ای بعدی (offset > 0) باید همان بایت‌ها را بگیرند؛ امضای ECDSA هر بار فرق می‌کند
    fun challengeFor(address: String, offset: Int, now: Long): ByteArray? {
        val challenge = if (offset == 0) {
            val created = TeacherChallenge.create(classCode, teacherKey)
            Challenge(created.nonce, created.toBytes(), now).also { challenges[address] = it }
        } else {
            challenges[address] ?: return null
        }
        return challenge.response
    }

    fun verify(address: String, payload: AttendancePayload, now: Long): Boolean {
        val challenge = challenges.remove(address) ?: return false // هر nonce فقط یک بار
        if (now - challenge.issuedAt > CHALLENGE_LIFETIME) return false
        return AttendanceCrypto.verifyStudentProof(classCode, challenge.nonce, payload)
    }

    fun clear() {
        challenges.clear()
    }
}
