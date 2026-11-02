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

    // امضا روی بخش‌های چالش (nonce، اثبات HMAC و تنظیمات جلسه)؛ پس امضا را نمی‌شود برای چالش دیگری
    // یا با تنظیمات دیگری به کار برد
    fun sign(key: TeacherKey, vararg parts: ByteArray): ByteArray =
        Signature.getInstance(ALGORITHM).run {
            initSign(privateKey(key.privateKey))
            signedData(parts.toList()).forEach(::update)
            sign()
        }

    fun verify(publicKey: ByteArray, signature: ByteArray, vararg parts: ByteArray): Boolean =
        runCatching {
            Signature.getInstance(ALGORITHM).run {
                initVerify(publicKey(publicKey))
                signedData(parts.toList()).forEach(::update)
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
        verify(key.publicKeyBytes, sign(key, nonce), nonce)
    }.getOrDefault(false)

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private fun signedData(parts: List<ByteArray>): List<ByteArray> =
        (listOf(LABEL) + parts).flatMap { listOf(ByteBuffer.allocate(4).putInt(it.size).array(), it) }

    private fun privateKey(base64: String): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(decode(base64)))

    private fun publicKey(bytes: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
}

// پاسخ خواندن چالش از گوشی استاد، با اندازه ثابت ۱۶۴ بایت تا در یک بسته بلوتوث (MTU ۱۸۵) جا شود:
// نسخه پروتکل (۱) | nonce (۱۶) | اثبات HMAC (۱۶) | تنظیمات جلسه (۲) | کلید عمومی خام (۶۵) | امضای خام r‖s (۶۴)
// کلید و امضا در حافظه به همان شکل استاندارد (X.509 و DER) نگه داشته می‌شوند و فقط برای ارسال فشرده می‌شوند
class TeacherChallenge(
    val nonce: ByteArray,
    val proof: ByteArray,
    val policy: SessionPolicy,
    val publicKey: ByteArray,
    val signature: ByteArray
) {
    fun toBytes(): ByteArray =
        byteArrayOf(BleConstants.PROTOCOL_VERSION.toByte()) + nonce + proof + policy.toBytes() +
            EcFormat.rawPublicKey(publicKey) + EcFormat.derToRaw(signature)

    fun verifySignature(): Boolean = TeacherSignature.verify(publicKey, signature, *signedParts(nonce, proof, policy))

    // چرا خواندن چالش ناموفق بود؛ برای پیام دقیق به دانشجو
    enum class Problem { WRONG_VERSION, INCOMPLETE }

    companion object {
        private const val PROOF_END = 1 + AttendanceCrypto.NONCE_SIZE + AttendanceCrypto.PROOF_SIZE
        private const val POLICY_END = PROOF_END + SessionPolicy.SIZE
        private const val KEY_END = POLICY_END + EcFormat.RAW_PUBLIC_KEY_SIZE
        const val SIZE = KEY_END + EcFormat.RAW_SIGNATURE_SIZE

        // نسخه پروتکل هم امضا می‌شود
        private fun signedParts(nonce: ByteArray, proof: ByteArray, policy: SessionPolicy) =
            arrayOf(byteArrayOf(BleConstants.PROTOCOL_VERSION.toByte()), nonce, proof, policy.toBytes())

        fun create(classCode: String, key: TeacherSignature.TeacherKey, policy: SessionPolicy): TeacherChallenge {
            val nonce = AttendanceCrypto.newNonce()
            val proof = AttendanceCrypto.teacherProof(classCode, nonce)
            val signature = TeacherSignature.sign(key, *signedParts(nonce, proof, policy))
            return TeacherChallenge(nonce, proof, policy, key.publicKeyBytes, signature)
        }

        fun problem(bytes: ByteArray): Problem? = when {
            bytes.isEmpty() || bytes[0].toInt() != BleConstants.PROTOCOL_VERSION -> Problem.WRONG_VERSION
            bytes.size != SIZE -> Problem.INCOMPLETE
            else -> null
        }

        // اگر نسخه یا اندازه درست نباشد null (دلیلش با problem)
        fun parse(bytes: ByteArray): TeacherChallenge? {
            if (problem(bytes) != null) return null
            return TeacherChallenge(
                bytes.copyOfRange(1, 1 + AttendanceCrypto.NONCE_SIZE),
                bytes.copyOfRange(1 + AttendanceCrypto.NONCE_SIZE, PROOF_END),
                SessionPolicy.fromBytes(bytes.copyOfRange(PROOF_END, POLICY_END)),
                EcFormat.x509PublicKey(bytes.copyOfRange(POLICY_END, KEY_END)),
                EcFormat.rawToDer(bytes.copyOfRange(KEY_END, SIZE))
            )
        }
    }
}

// تبدیل کلید عمومی و امضای P-256 بین قالب استاندارد جاوا و قالب خام کوتاه‌تر برای بلوتوث
object EcFormat {
    const val RAW_PUBLIC_KEY_SIZE = 65 // 0x04 ‖ X ‖ Y
    const val RAW_SIGNATURE_SIZE = 64 // r ‖ s
    private const val COORDINATE_SIZE = 32

    // ابتدای ثابت X.509 همه کلیدهای عمومی P-256 (الگوریتم EC و منحنی secp256r1)
    private val X509_PREFIX = byteArrayOf(
        0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x02, 0x01,
        0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00
    )

    fun rawPublicKey(x509: ByteArray): ByteArray {
        require(x509.size == X509_PREFIX.size + RAW_PUBLIC_KEY_SIZE && x509.copyOf(X509_PREFIX.size).contentEquals(X509_PREFIX))
        return x509.copyOfRange(X509_PREFIX.size, x509.size)
    }

    fun x509PublicKey(raw: ByteArray): ByteArray = X509_PREFIX + raw

    // DER: 30 len 02 lenR R 02 lenS S ← r و s هر کدام دقیقاً ۳۲ بایت
    fun derToRaw(der: ByteArray): ByteArray {
        var index = 2
        fun integer(): ByteArray {
            require(der[index].toInt() == 0x02)
            val length = der[index + 1].toInt()
            val value = der.copyOfRange(index + 2, index + 2 + length)
            index += 2 + length
            val unsigned = value.dropWhile { it.toInt() == 0 }.toByteArray()
            require(unsigned.size <= COORDINATE_SIZE)
            return ByteArray(COORDINATE_SIZE - unsigned.size) + unsigned
        }
        require(der[0].toInt() == 0x30)
        return integer() + integer()
    }

    fun rawToDer(raw: ByteArray): ByteArray {
        fun integer(value: ByteArray): ByteArray {
            var trimmed = value.dropWhile { it.toInt() == 0 }.toByteArray()
            if (trimmed.isEmpty() || trimmed[0] < 0) trimmed = byteArrayOf(0) + trimmed // عدد مثبت بماند
            return byteArrayOf(0x02, trimmed.size.toByte()) + trimmed
        }
        val body = integer(raw.copyOfRange(0, COORDINATE_SIZE)) + integer(raw.copyOfRange(COORDINATE_SIZE, raw.size))
        return byteArrayOf(0x30, body.size.toByte()) + body
    }
}
