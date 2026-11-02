package com.ali_1200.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotatingQrTest {
    private val code = "ABCDE23456"
    private val key = TeacherSignature.generate()
    private val now = 1_800_000_000_000

    private fun qrAt(time: Long) = RotatingQr.content(key, code, RotatingQr.period(time))

    @Test
    fun freshQrIsAcceptedForAboutThirtySeconds() {
        val qr = qrAt(now)
        assertTrue(RotatingQr.isValidFormat(qr))
        assertTrue(RotatingQr.verify(key, code, qr, now))
        assertTrue(RotatingQr.verify(key, code, qr, now + 3 * RotatingQr.PERIOD))
        assertFalse(RotatingQr.verify(key, code, qr, now + 4 * RotatingQr.PERIOD))
        // QR آینده (ساعت دست‌کاری‌شده) پذیرفته نمی‌شود
        assertFalse(RotatingQr.verify(key, code, qrAt(now + RotatingQr.PERIOD), now))
    }

    // دانشجو کد کلاس را دارد ولی کلید خصوصی استاد را نه
    @Test
    fun studentCannotForgeQr() {
        val forged = RotatingQr.content(TeacherSignature.generate(), code, RotatingQr.period(now))
        assertFalse(RotatingQr.verify(key, code, forged, now))
        // QR درس دیگر همین استاد
        assertFalse(RotatingQr.verify(key, code, RotatingQr.content(key, "FGHJK78923", RotatingQr.period(now)), now))
    }

    @Test
    fun rejectsOtherTexts() {
        assertFalse(RotatingQr.isValidFormat(null))
        assertFalse(RotatingQr.isValidFormat("https://example.com"))
        assertFalse(RotatingQr.isValidFormat("PRESENCE-QR:abc:xyz"))
        assertFalse(RotatingQr.verify(key, code, "", now))
    }

    @Test
    fun qrFlagIsSignedAndEnforcedByTeacher() {
        val verifier = AttendanceVerifier(code, key) { SessionPolicy(qrRequired = true) }
        val address = "AA:BB:CC:DD:EE:FF"

        fun answer(qr: String): AttendancePayload {
            val challenge = TeacherChallenge.parse(verifier.challengeFor(address, 0, now)!!)!!
            assertTrue(challenge.policy.qrRequired)
            assertTrue(challenge.verifySignature())
            val proof = AttendanceCrypto.studentProof(code, challenge.nonce, "علی", "40012345", "d1", -60, qr)
            return AttendancePayload("علی", "40012345", "d1", proof, -60, qr)
        }

        assertEquals(AttendanceVerifier.Result.QR_INVALID, verifier.verify(address, answer(""), now))
        assertEquals(AttendanceVerifier.Result.QR_INVALID, verifier.verify(address, answer(qrAt(now - 5 * RotatingQr.PERIOD)), now))
        assertEquals(AttendanceVerifier.Result.ACCEPTED, verifier.verify(address, answer(qrAt(now)), now))
        // QR داخل اثبات دانشجوست و در مسیر عوض نمی‌شود
        val payload = answer(qrAt(now))
        assertEquals(AttendanceVerifier.Result.AUTH_FAILED, verifier.verify(address, payload.copy(qr = qrAt(now - RotatingQr.PERIOD)), now))
    }
}
