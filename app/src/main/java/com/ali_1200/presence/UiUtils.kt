package com.ali_1200.presence

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlin.math.abs

// ابزارهای کوچک مشترک بین صفحه‌ها

private val AVATAR_COLORS = intArrayOf(
    R.color.avatar_1, R.color.avatar_2, R.color.avatar_3,
    R.color.avatar_4, R.color.avatar_5, R.color.avatar_6
)

// آواتار دایره‌ای با حرف اول نام؛ رنگ از روی کد دانشجویی ثابت می‌ماند
fun TextView.bindAvatar(name: String, key: String) {
    text = name.trim().firstOrNull()?.toString() ?: "؟"
    val color = AVATAR_COLORS[abs(key.hashCode()) % AVATAR_COLORS.size]
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
}

// برچسب گرد رنگی (مثل «غایب» یا ساعت حضور)
fun TextView.bindBadge(label: String, @ColorRes background: Int, @ColorRes foreground: Int) {
    text = label
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, background))
    setTextColor(ContextCompat.getColor(context, foreground))
}

fun Fragment.showMessage(message: String) {
    (activity as? MainActivity)?.showMessage(message)
}

fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

// حلقه تپنده (بزرگ‌شدن و محو شدن پشت‌سرهم) برای نشان دادن فعال بودن بلوتوث
fun View.startPulse(): ValueAnimator {
    visibility = View.VISIBLE
    return ObjectAnimator.ofPropertyValuesHolder(
        this,
        PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1f),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1f),
        PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f)
    ).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        start()
    }
}

fun View.stopPulse(animator: ValueAnimator?) {
    animator?.cancel()
    visibility = View.INVISIBLE
}

// «۵ دقیقه پیش» و مانند آن
fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "لحظاتی پیش"
        diff < 60 * 60_000 -> "${diff / 60_000} دقیقه پیش"
        diff < 24 * 60 * 60_000 -> "${diff / (60 * 60_000)} ساعت پیش"
        else -> "${diff / (24 * 60 * 60_000)} روز پیش"
    }
}
