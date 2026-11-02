package com.ali_1200.presence

import java.util.concurrent.ConcurrentHashMap

// سمت استاد: به هر گوشی دانشجو یک nonce یک‌بارمصرف می‌دهد و اثبات دانشجو را با آن بررسی می‌کند
class AttendanceVerifier(private val classCode: String) {
    private class Challenge(val nonce: ByteArray, val issuedAt: Long)

    private val challenges = ConcurrentHashMap<String, Challenge>()

    companion object {
        const val CHALLENGE_LIFETIME: Long = 60_000 // nonce فقط یک دقیقه معتبر است
    }

    // پاسخ کامل خواندن چالش: nonce + اثبات استاد
    // خواندن‌های تکه‌ای بعدی (offset > 0) باید همان چالش را بگیرند، نه یک nonce تازه
    fun challengeFor(address: String, offset: Int, now: Long): ByteArray? {
        val challenge = if (offset == 0) {
            Challenge(AttendanceCrypto.newNonce(), now).also { challenges[address] = it }
        } else {
            challenges[address] ?: return null
        }
        return challenge.nonce + AttendanceCrypto.teacherProof(classCode, challenge.nonce)
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
