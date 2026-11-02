package com.ali_1200.presence

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Locale

// امضای دیجیتال گوشی استاد (ECDSA روی منحنی P-256)
// کد کلاس را همه دانشجوهای کلاس می‌دانند، پس اثبات HMAC استاد را هر هم‌کلاسی هم می‌تواند بسازد.
// کلید خصوصی امضا فقط در گوشی استاد است؛ گوشی دانشجو کلید عمومی استاد را در اولین ثبت حضور موفق
// نگه می‌دارد (Trust On First Use، مثل SSH) و از آن به بعد فقط به گوشی‌ای با همان کلید داده می‌فرستد.
object TeacherSignature {
    private const val ALGORITHM = "SHA256withECDSA"
    private val LABEL = "presence-teacher-signature".toByteArray()

    // کلید استاد به‌صورت base64 (خصوصی: PKCS#8، عمومی: X.509) برای ذخیره و پشتیبان
    data class TeacherKey(val privateKey: String, val publicKey: String) {
        val publicKeyBytes: ByteArray get() = decode(publicKey)
        val fingerprint: String get() = fingerprint(publicKeyBytes)
    }

    fun generate(): TeacherKey {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        return TeacherKey(encode(pair.private.encoded), encode(pair.public.encoded))
    }

    // امضا روی nonce و اثبات HMAC همان چالش؛ پس امضا را نمی‌شود برای چالش دیگری به کار برد
    fun sign(key: TeacherKey, nonce: ByteArray, proof: ByteArray): ByteArray =
        Signature.getInstance(ALGORITHM).run {
            initSign(privateKey(key.privateKey))
            signedData(nonce, proof).forEach(::update)
            sign()
        }

    fun verify(publicKey: ByteArray, nonce: ByteArray, proof: ByteArray, signature: ByteArray): Boolean =
        runCatching {
            Signature.getInstance(ALGORITHM).run {
                initVerify(publicKey(publicKey))
                signedData(nonce, proof).forEach(::update)
                verify(signature)
            }
        }.getOrDefault(false) // کلید یا امضای خراب

    // ۱۶ رقم مبنای ۱۶ از SHA-256 کلید عمومی؛ استاد و دانشجو می‌توانند آن را با هم مقایسه کنند
    fun fingerprint(publicKey: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(publicKey)
            .take(8)
            .joinToString("") { String.format(Locale.US, "%02X", it) } // اعداد لاتین حتی با زبان فارسی
            .chunked(4)
            .joinToString(" ")

    fun fingerprint(publicKey: String): String = fingerprint(decode(publicKey))

    fun isValidKey(key: TeacherKey): Boolean = runCatching {
        val nonce = AttendanceCrypto.newNonce()
        verify(key.publicKeyBytes, nonce, nonce, sign(key, nonce, nonce))
    }.getOrDefault(false)

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private fun signedData(nonce: ByteArray, proof: ByteArray): List<ByteArray> =
        listOf(LABEL, nonce, proof).flatMap { listOf(ByteBuffer.allocate(4).putInt(it.size).array(), it) }

    private fun privateKey(base64: String): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(decode(base64)))

    private fun publicKey(bytes: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
}

// پاسخ خواندن چالش از گوشی استاد:
// nonce (۱۶ بایت) | اثبات HMAC (۱۶ بایت) | طول کلید عمومی (۱ بایت) | کلید عمومی | امضا (باقی بایت‌ها)
// حدود ۲۰۰ بایت است و از MTU بزرگ‌تر؛ برای همین دانشجو آن را با Long Read تکه‌تکه می‌خواند
class TeacherChallenge(
    val nonce: ByteArray,
    val proof: ByteArray,
    val publicKey: ByteArray,
    val signature: ByteArray
) {
    fun toBytes(): ByteArray = nonce + proof + byteArrayOf(publicKey.size.toByte()) + publicKey + signature

    fun verifySignature(): Boolean = TeacherSignature.verify(publicKey, nonce, proof, signature)

    companion object {
        private const val HEADER = AttendanceCrypto.NONCE_SIZE + AttendanceCrypto.PROOF_SIZE

        fun create(classCode: String, key: TeacherSignature.TeacherKey): TeacherChallenge {
            val nonce = AttendanceCrypto.newNonce()
            val proof = AttendanceCrypto.teacherProof(classCode, nonce)
            return TeacherChallenge(nonce, proof, key.publicKeyBytes, TeacherSignature.sign(key, nonce, proof))
        }

        // اگر قالب درست نباشد (مثلاً نسخه قدیمی برنامه استاد بدون امضا) null
        fun parse(bytes: ByteArray): TeacherChallenge? {
            if (bytes.size <= HEADER) return null
            val keySize = bytes[HEADER].toInt() and 0xFF
            val keyEnd = HEADER + 1 + keySize
            if (keySize == 0 || bytes.size <= keyEnd) return null
            return TeacherChallenge(
                bytes.copyOfRange(0, AttendanceCrypto.NONCE_SIZE),
                bytes.copyOfRange(AttendanceCrypto.NONCE_SIZE, HEADER),
                bytes.copyOfRange(HEADER + 1, keyEnd),
                bytes.copyOfRange(keyEnd, bytes.size)
            )
        }
    }
}
