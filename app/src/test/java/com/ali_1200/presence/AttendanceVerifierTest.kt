package com.ali_1200.presence

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceVerifierTest {
    private val code = "ABCDE23456"
    private val address = "AA:BB:CC:DD:EE:FF"
    private val teacherKey = TeacherSignature.generate()
    private var policy = SessionPolicy()
    private val verifier = AttendanceVerifier(code, teacherKey) { policy }

    // کاری که گوشی دانشجو انجام می‌دهد: خواندن چالش، بررسی استاد و امضایش، ساختن اثبات
    private fun studentAnswers(bytes: ByteArray, classCode: String = code, rssi: Int = -60): AttendancePayload {
        val challenge = TeacherChallenge.parse(bytes)!!
        val nonce = challenge.nonce
        assertTrue(AttendanceCrypto.verifyTeacherProof(code, nonce, challenge.proof))
        assertTrue(challenge.verifySignature())
        assertArrayEquals(teacherKey.publicKeyBytes, challenge.publicKey)

        val proof = AttendanceCrypto.studentProof(classCode, nonce, "علی رضایی", "40012345", "a1b2c3", rssi)
        return AttendancePayload("علی رضایی", "40012345", "a1b2c3", proof, rssi)
    }

    @Test
    fun validAnswerIsAccepted() {
        val challenge = verifier.challengeFor(address, 0, 1_000)!!
        // در یک بسته بلوتوث جا می‌شود (MTU منهای ۳ بایت سرآیند)؛ نیازی به خواندن تکه‌تکه نیست
        assertEquals(TeacherChallenge.SIZE, challenge.size)
        assertTrue(challenge.size <= BleConstants.REQUESTED_MTU - 3)
        assertAccepted(verifier.verify(address, studentAnswers(challenge), 2_000))
    }

    @Test
    fun replayedAnswerIsRejected() {
        val payload = studentAnswers(verifier.challengeFor(address, 0, 1_000)!!)
        assertAccepted(verifier.verify(address, payload, 2_000))
        assertRejected(verifier.verify(address, payload, 3_000))
    }

    @Test
    fun expiredChallengeIsRejected() {
        val payload = studentAnswers(verifier.challengeFor(address, 0, 1_000)!!)
        assertRejected(verifier.verify(address, payload, 1_000 + AttendanceVerifier.CHALLENGE_LIFETIME + 1))
    }

    @Test
    fun wrongClassCodeIsRejected() {
        val payload = studentAnswers(verifier.challengeFor(address, 0, 1_000)!!, classCode = "ZZZZZ99999")
        assertRejected(verifier.verify(address, payload, 2_000))
    }

    @Test
    fun answerForAnotherDevicesChallengeIsRejected() {
        val payload = studentAnswers(verifier.challengeFor("11:22:33:44:55:66", 0, 1_000)!!)
        verifier.challengeFor(address, 0, 1_000)
        assertRejected(verifier.verify(address, payload, 2_000))
    }

    @Test
    fun blobReadsReturnSameChallenge() {
        val first = verifier.challengeFor(address, 0, 1_000)!!
        val again = verifier.challengeFor(address, 22, 1_100)!!
        assertArrayEquals(first, again)
        assertNull(verifier.challengeFor("11:22:33:44:55:66", 22, 1_100))
    }

    private fun assertAccepted(result: AttendanceVerifier.Result) = assertEquals(AttendanceVerifier.Result.ACCEPTED, result)

    private fun assertRejected(result: AttendanceVerifier.Result) = assertEquals(AttendanceVerifier.Result.AUTH_FAILED, result)

    // محدوده کلاس: تنظیمات داخل چالش امضاشده است و استاد هم دوباره بررسی می‌کند
    @Test
    fun rangeLimitIsSignedAndEnforced() {
        policy = SessionPolicy(minRssi = -72)
        val bytes = verifier.challengeFor(address, 0, 1_000)!!
        assertEquals(-72, TeacherChallenge.parse(bytes)!!.policy.minRssi)
        assertEquals(AttendanceVerifier.Result.TOO_FAR, verifier.verify(address, studentAnswers(bytes, rssi = -85), 2_000))

        val near = verifier.challengeFor(address, 0, 3_000)!!
        assertAccepted(verifier.verify(address, studentAnswers(near, rssi = -65), 4_000))

        // گوشی جعلی نمی‌تواند محدوده را عوض کند: امضا خراب می‌شود
        val tampered = near.copyOf().also { it[1 + AttendanceCrypto.NONCE_SIZE + AttendanceCrypto.PROOF_SIZE + 1] = 0 }
        assertFalse(TeacherChallenge.parse(tampered)!!.verifySignature())
    }

    // دانشجو نمی‌تواند بعد از ساختن اثبات، عدد سیگنال را عوض کند
    @Test
    fun rssiIsCoveredByStudentProof() {
        val payload = studentAnswers(verifier.challengeFor(address, 0, 1_000)!!, rssi = -90)
        assertRejected(verifier.verify(address, payload.copy(rssi = -50), 2_000))
    }
}
