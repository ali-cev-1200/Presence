package com.ali_1200.presence

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

// خواندن لیست کلاس از فایل CSV (ذخیره‌شده از Excel)
// هر سطر: کد دانشجویی و نام، به هر ترتیبی؛ سطر عنوان و سطرهای بدون کد دانشجویی نادیده گرفته می‌شوند
object RosterParser {
    private val SEPARATORS = charArrayOf(',', ';', '\t', '؛', '،')

    fun parse(bytes: ByteArray): List<Student> = parse(decode(bytes))

    fun parse(text: String): List<Student> =
        text.removePrefix("\uFEFF")
            .lineSequence()
            .mapNotNull(::parseLine)
            .distinctBy { it.studentID } // اگر کدی دو بار آمده باشد، اولی می‌ماند
            .toList()

    private fun parseLine(line: String): Student? {
        val cells = line.split(*SEPARATORS)
            .map { PersianDate.normalizeDigits(it.trim().trim('"').trim()) }
            .filter { it.isNotEmpty() }

        // بلندترین عدد سطر کد دانشجویی است (نه ستون «ردیف»)
        val id = cells.filter { cell -> cell.all { it.isDigit() } }.maxByOrNull { it.length } ?: return null
        val name = cells.firstOrNull { cell -> cell != id && cell.any { it.isLetter() } } ?: return null
        return Student(id, normalizeLetters(name))
    }

    // «ي» و «ك» عربی (مثلاً از کدگذاری 1256 که «ی» فارسی ندارد) به حروف فارسی تبدیل می‌شوند
    private fun normalizeLetters(text: String): String =
        text.replace('ي', 'ی').replace('ك', 'ک')

    // Excel فایل CSV را گاهی UTF-8 و گاهی با کدگذاری ویندوز فارسی (1256) ذخیره می‌کند
    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1256"))
    }
}
