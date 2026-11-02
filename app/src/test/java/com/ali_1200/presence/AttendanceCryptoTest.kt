package com.ali_1200.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceCryptoTest {
    private val code = "ABCDE23456"

    @Test
    fun generatedClassCodeIsValid() {
        repeat(100) {
            val generated = AttendanceCrypto.generateClassCode()
            assertEquals(generated, AttendanceCrypto.normalizeClassCode(generated))
        }
    }

    @Test
    fun normalizeAcceptsFormattedAndLowercaseInput() {
        assertEquals(code, AttendanceCrypto.normalizeClassCode(" abcde-23456 "))
        assertEquals("ABCDE-23456", AttendanceCrypto.formatClassCode(code))
    }

    @Test
    fun normalizeRejectsWrongLengthOrAmbiguousCharacters() {
        assertNull(AttendanceCrypto.normalizeClassCode("ABCDE2345"))
        assertNull(AttendanceCrypto.normalizeClassCode("ABCDE234560"))
        assertNull(AttendanceCrypto.normalizeClassCode("ABCDE2345O")) // O ممنوع است
        assertNull(AttendanceCrypto.normalizeClassCode("ABCDE23451")) // 1 ممنوع است
    }

    @Test
    fun teacherProofOnlyVerifiesWithSameCodeAndNonce() {
        val nonce = AttendanceCrypto.newNonce()
        val proof = AttendanceCrypto.teacherProof(code, nonce)

        assertTrue(AttendanceCrypto.verifyTeacherProof(code, nonce, proof))
        assertFalse(AttendanceCrypto.verifyTeacherProof("ZZZZZ99999", nonce, proof))
        assertFalse(AttendanceCrypto.verifyTeacherProof(code, AttendanceCrypto.newNonce(), proof))
    }

    @Test
    fun studentProofCoversEveryField() {
        val nonce = AttendanceCrypto.newNonce()
        val proof = AttendanceCrypto.studentProof(code, nonce, "علی رضایی", "40012345", "a1b2c3", -60)
        val payload = AttendancePayload("علی رضایی", "40012345", "a1b2c3", proof, -60)

        assertTrue(AttendanceCrypto.verifyStudentProof(code, nonce, payload))
        assertFalse(AttendanceCrypto.verifyStudentProof(code, nonce, payload.copy(studentId = "40099999")))
        assertFalse(AttendanceCrypto.verifyStudentProof(code, nonce, payload.copy(name = "علی")))
        assertFalse(AttendanceCrypto.verifyStudentProof(code, nonce, payload.copy(deviceId = "ffffff")))
        assertFalse(AttendanceCrypto.verifyStudentProof("ZZZZZ99999", nonce, payload))
    }

    @Test
    fun fieldBoundariesCannotBeShifted() {
        val nonce = AttendanceCrypto.newNonce()
        assertNotEquals(
            AttendanceCrypto.studentProof(code, nonce, "ab", "c", "d", -60),
            AttendanceCrypto.studentProof(code, nonce, "a", "bc", "d", -60)
        )
    }
}
