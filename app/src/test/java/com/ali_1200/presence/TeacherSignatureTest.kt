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

    // نسخه دیگر برنامه یا پیام ناقص: دلیلش جدا گزارش می‌شود تا دانشجو پیام درست ببیند
    @Test
    fun wrongVersionAndIncompleteChallengeAreDetected() {
        val bytes = TeacherChallenge.create(code, key, SessionPolicy()).toBytes()
        assertNull(TeacherChallenge.problem(bytes))

        val oldVersion = bytes.copyOf().also { it[0] = 2 }
        assertEquals(TeacherChallenge.Problem.WRONG_VERSION, TeacherChallenge.problem(oldVersion))
        assertNull(TeacherChallenge.parse(oldVersion))
        assertEquals(TeacherChallenge.Problem.WRONG_VERSION, TeacherChallenge.problem(ByteArray(0)))

        // فقط تکه اول خوانده شده (مثلاً MTU کوچک بدون خواندن تکه‌تکه)
        val truncated = bytes.copyOf(100)
        assertEquals(TeacherChallenge.Problem.INCOMPLETE, TeacherChallenge.problem(truncated))
        assertNull(TeacherChallenge.parse(truncated))
    }

    // تبدیل امضا و کلید به قالب کوتاه و برگشت، برای صدها امضای تصادفی (r یا s گاهی کوتاه‌تر از ۳۲ بایت است)
    @Test
    fun compactFormatRoundTrips() {
        repeat(300) {
            val nonce = AttendanceCrypto.newNonce()
            val der = TeacherSignature.sign(key, nonce)
            val raw = EcFormat.derToRaw(der)
            assertEquals(EcFormat.RAW_SIGNATURE_SIZE, raw.size)
            assertTrue(TeacherSignature.verify(key.publicKeyBytes, EcFormat.rawToDer(raw), nonce))
        }
        val raw = EcFormat.rawPublicKey(key.publicKeyBytes)
        assertEquals(EcFormat.RAW_PUBLIC_KEY_SIZE, raw.size)
        assertTrue(EcFormat.x509PublicKey(raw).contentEquals(key.publicKeyBytes))
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
