package com.ali_1200.presence

import android.content.Context

// کلید امضای گوشی استاد؛ یک بار ساخته می‌شود و برای همه درس‌ها به کار می‌رود.
// در Android Keystore نیست چون آنجا کلید قابل خروج نیست و با پشتیبان به گوشی جدید منتقل نمی‌شد؛
// به‌جایش در حافظه خصوصی برنامه است که برنامه‌های دیگر به آن دسترسی ندارند.
class TeacherKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("presence", Context.MODE_PRIVATE)

    companion object {
        private const val PRIVATE_KEY = "teacher_private_key"
        private const val PUBLIC_KEY = "teacher_public_key"
    }

    fun get(): TeacherSignature.TeacherKey? {
        val privateKey = prefs.getString(PRIVATE_KEY, null) ?: return null
        val publicKey = prefs.getString(PUBLIC_KEY, null) ?: return null
        return TeacherSignature.TeacherKey(privateKey, publicKey)
    }

    @Synchronized
    fun getOrCreate(): TeacherSignature.TeacherKey = get() ?: TeacherSignature.generate().also(::set)

    fun set(key: TeacherSignature.TeacherKey) {
        prefs.edit().putString(PRIVATE_KEY, key.privateKey).putString(PUBLIC_KEY, key.publicKey).apply()
    }
}
