package com.ali_1200.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherSignatureTest {
    private val code = "ABCDE23456"
    private val key = TeacherSignature.generate()

    @Test
    fun validSignatureIsAccepted() {
        val challenge = TeacherChallenge.parse(TeacherChallenge.create(code, key, SessionPolicy()).toBytes())!!
        assertTrue(challenge.verifySignature())
        assertTrue(AttendanceCrypto.verifyTeacherProof(code, challenge.nonce, challenge.proof))
    }

    // هم‌کلاسی که کد کلاس را دارد می‌تواند HMAC درست بسازد، ولی امضای استاد را نه
    @Test
    fun classmateWithClassCodeCannotImpersonateTeacher() {
        val fake = TeacherSignature.generate()
        val real = TeacherChallenge.create(code, key, SessionPolicy())
        // کلید عمومی واقعی استاد با امضای کلید جعلی
        val forged = TeacherChallenge(
            real.nonce, real.proof, real.policy, key.publicKeyBytes,
            TeacherSignature.sign(fake, real.nonce, real.proof, real.policy.toBytes())
        )
        assertFalse(forged.verifySignature())
        // با کلید جعلی خودش امضا درست است، ولی کلید با کلید ذخیره‌شده در گوشی دانشجو فرق دارد
        val own = TeacherChallenge.create(code, fake, SessionPolicy())
        assertTrue(own.verifySignature())
        assertNotEquals(TeacherSignature.encode(key.publicKeyBytes), TeacherSignature.encode(own.publicKey))
    }

    @Test
    fun signatureCannotBeReusedForAnotherNonce() {
        val first = TeacherChallenge.create(code, key, SessionPolicy())
        val other = TeacherChallenge.create(code, key, SessionPolicy())
        assertFalse(TeacherChallenge(other.nonce, other.proof, other.policy, first.publicKey, first.signature).verifySignature())
    }

    @Test
    fun tamperedChallengeIsRejected() {
        val bytes = TeacherChallenge.create(code, key, SessionPolicy()).toBytes()
        bytes[3] = (bytes[3].toInt() xor 1).toByte() // یک بیت nonce
        assertFalse(TeacherChallenge.parse(bytes)!!.verifySignature())
    }

    // برنامه استاد قدیمی فقط nonce و HMAC (۳۲ بایت) می‌فرستاد
    @Test
    fun oldChallengeWithoutSignatureIsRejected() {
        assertNull(TeacherChallenge.parse(ByteArray(AttendanceCrypto.NONCE_SIZE + AttendanceCrypto.PROOF_SIZE + SessionPolicy.SIZE)))
        assertNull(TeacherChallenge.parse(ByteArray(10)))
    }

    @Test
    fun fingerprintIsStableAndReadable() {
        val fingerprint = key.fingerprint
        assertEquals(fingerprint, TeacherSignature.fingerprint(key.publicKey))
        assertTrue(Regex("[0-9A-F]{4}( [0-9A-F]{4}){3}").matches(fingerprint))
        assertNotEquals(fingerprint, TeacherSignature.generate().fingerprint)
    }

    @Test
    fun keyCheck() {
        assertTrue(TeacherSignature.isValidKey(key))
        assertFalse(TeacherSignature.isValidKey(TeacherSignature.TeacherKey("abc", key.publicKey)))
        assertFalse(TeacherSignature.isValidKey(TeacherSignature.TeacherKey(key.privateKey, TeacherSignature.generate().publicKey)))
    }
}
