package com.ali_1200.presence

import java.util.concurrent.ConcurrentHashMap

// سمت استاد: به هر گوشی دانشجو یک nonce یک‌بارمصرف (با اثبات HMAC، تنظیمات جلسه و امضای استاد) می‌دهد
// و اثبات دانشجو را با آن بررسی می‌کند. تنظیمات هر چالش همان است که موقع ساختنش بوده
class AttendanceVerifier(
    private val classCode: String,
    private val teacherKey: TeacherSignature.TeacherKey,
    private val policy: () -> SessionPolicy = { SessionPolicy() }
) {
    private class Challenge(val nonce: ByteArray, val policy: SessionPolicy, val response: ByteArray, val issuedAt: Long)

    enum class Result { ACCEPTED, AUTH_FAILED, TOO_FAR, QR_INVALID }

    private val challenges = ConcurrentHashMap<String, Challenge>()

    companion object {
        const val CHALLENGE_LIFETIME: Long = 60_000 // nonce فقط یک دقیقه معتبر است
    }

    // پاسخ کامل خواندن چالش (TeacherChallenge)
    // خواندن‌های تکه‌ای بعدی (offset > 0) باید همان بایت‌ها را بگیرند؛ امضای ECDSA هر بار فرق می‌کند
    fun challengeFor(address: String, offset: Int, now: Long): ByteArray? {
        val challenge = if (offset == 0) {
            val created = TeacherChallenge.create(classCode, teacherKey, policy())
            Challenge(created.nonce, created.policy, created.toBytes(), now).also { challenges[address] = it }
        } else {
            challenges[address] ?: return null
        }
        return challenge.response
    }

    fun verify(address: String, payload: AttendancePayload, now: Long): Result {
        val challenge = challenges.remove(address) ?: return Result.AUTH_FAILED // هر nonce فقط یک بار
        if (now - challenge.issuedAt > CHALLENGE_LIFETIME) return Result.AUTH_FAILED
        if (!AttendanceCrypto.verifyStudentProof(classCode, challenge.nonce, payload)) return Result.AUTH_FAILED
        // گوشی دانشجو هم خودش بررسی می‌کند؛ این بررسی دوم برای برنامه دست‌کاری‌شده است
        if (!challenge.policy.allowsRssi(payload.rssi)) return Result.TOO_FAR
        if (challenge.policy.qrRequired && !RotatingQr.verify(teacherKey, classCode, payload.qr, now)) {
            return Result.QR_INVALID
        }
        return Result.ACCEPTED
    }

    fun clear() {
        challenges.clear()
    }
}
