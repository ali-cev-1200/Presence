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
    private val verifier = AttendanceVerifier(code)

    // کاری که گوشی دانشجو انجام می‌دهد: خواندن چالش، بررسی استاد، ساختن اثبات
    private fun studentAnswers(challenge: ByteArray, classCode: String = code): AttendancePayload {
        val nonce = challenge.copyOfRange(0, AttendanceCrypto.NONCE_SIZE)
        val teacherProof = challenge.copyOfRange(AttendanceCrypto.NONCE_SIZE, challenge.size)
        assertTrue(AttendanceCrypto.verifyTeacherProof(code, nonce, teacherProof))

        val proof = AttendanceCrypto.studentProof(classCode, nonce, "علی رضایی", "40012345", "a1b2c3")
        return AttendancePayload("علی رضایی", "40012345", "a1b2c3", proof)
    }

    @Test
    fun validAnswerIsAccepted() {
        val challenge = verifier.challengeFor(address, 0, 1_000)!!
        assertEquals(AttendanceCrypto.NONCE_SIZE + AttendanceCrypto.PROOF_SIZE, challenge.size)
        assertTrue(verifier.verify(address, studentAnswers(challenge), 2_000))
    }

    @Test
    fun replayedAnswerIsRejected() {
        val payload = studentAnswers(verifier.challengeFor(address, 0, 1_000)!!)
        assertTrue(verifier.verify(address, payload, 2_000))
        assertFalse(verifier.verify(address, payload, 3_000))
    }

    @Test
    fun expiredChallengeIsRejected() {
        val payload = studentAnswers(verifier.challengeFor(address, 0, 1_000)!!)
        assertFalse(verifier.verify(address, payload, 1_000 + AttendanceVerifier.CHALLENGE_LIFETIME + 1))
    }

    @Test
    fun wrongClassCodeIsRejected() {
        val payload = studentAnswers(verifier.challengeFor(address, 0, 1_000)!!, classCode = "ZZZZZ99999")
        assertFalse(verifier.verify(address, payload, 2_000))
    }

    @Test
    fun answerForAnotherDevicesChallengeIsRejected() {
        val payload = studentAnswers(verifier.challengeFor("11:22:33:44:55:66", 0, 1_000)!!)
        verifier.challengeFor(address, 0, 1_000)
        assertFalse(verifier.verify(address, payload, 2_000))
    }

    @Test
    fun blobReadsReturnSameChallenge() {
        val first = verifier.challengeFor(address, 0, 1_000)!!
        val again = verifier.challengeFor(address, 22, 1_100)!!
        assertArrayEquals(first, again)
        assertNull(verifier.challengeFor("11:22:33:44:55:66", 22, 1_100))
    }
}
