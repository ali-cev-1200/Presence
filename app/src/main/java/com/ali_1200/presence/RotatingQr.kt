package com.ali_1200.presence

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// QR چرخشی، عامل دوم حضور: گوشی استاد هر ۱۰ ثانیه QR تازه‌ای نشان می‌دهد و دانشجو باید آن را اسکن کند.
// دانشجوی بیرون کلاس صفحه استاد را نمی‌بیند و عکس QR هم زود کهنه می‌شود.
// کلید QR از کلید خصوصی امضای استاد ساخته می‌شود؛ پس دانشجو با داشتن کد کلاس هم نمی‌تواند QR بسازد.
object RotatingQr {
    const val PERIOD: Long = 10_000

    // QR تا ۳ دوره (حدود ۳۰ ثانیه) پذیرفته می‌شود: اسکن و اتصال دوباره بلوتوث چند ثانیه وقت می‌برد
    private const val ACCEPTED_PERIODS = 3
    private const val PREFIX = "PRESENCE-QR:"
    private const val TOKEN_SIZE = 12
    private val LABEL = "presence-qr".toByteArray()

    fun period(now: Long): Long = now / PERIOD

    // مثل «PRESENCE-QR:178000000:Xy3k...»
    fun content(teacherKey: TeacherSignature.TeacherKey, classCode: String, period: Long): String =
        "$PREFIX$period:${token(teacherKey, classCode, period)}"

    // دانشجو فقط قالب را می‌تواند بررسی کند؛ درستی را گوشی استاد می‌فهمد
    fun isValidFormat(text: String?): Boolean = parse(text) != null

    fun verify(teacherKey: TeacherSignature.TeacherKey, classCode: String, text: String, now: Long): Boolean {
        val (period, token) = parse(text) ?: return false
        val current = period(now)
        if (period > current || period < current - ACCEPTED_PERIODS) return false
        return MessageDigest.isEqual(token.toByteArray(), token(teacherKey, classCode, period).toByteArray())
    }

    // تصویر سیاه و سفید QR برای نمایش روی صفحه استاد
    fun bitmap(text: String, size: Int): Bitmap {
        val matrix = matrix(text, size)
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    fun matrix(text: String, size: Int): BitMatrix {
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    }

    private fun parse(text: String?): Pair<Long, String>? {
        val parts = text?.trim()?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.split(":") ?: return null
        if (parts.size != 2 || parts[1].isEmpty()) return null
        val period = parts[0].toLongOrNull() ?: return null
        return period to parts[1]
    }

    private fun token(teacherKey: TeacherSignature.TeacherKey, classCode: String, period: Long): String {
        val key = MessageDigest.getInstance("SHA-256").run {
            update(LABEL)
            update(TeacherSignature.decode(teacherKey.privateKey))
            digest(classCode.toByteArray())
        }
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val digest = mac.doFinal(ByteBuffer.allocate(8).putLong(period).array())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(TOKEN_SIZE))
    }
}
