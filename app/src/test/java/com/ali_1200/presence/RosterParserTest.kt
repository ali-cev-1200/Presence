package com.ali_1200.presence

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class RosterParserTest {
    @Test
    fun skipsHeaderAndRowNumberColumn() {
        val csv = """
            ردیف,کد دانشجویی,نام و نام خانوادگی
            1,40012345,علی رضایی
            2,40012346,"مریم احمدی"
        """.trimIndent()

        assertEquals(
            listOf(Student("40012345", "علی رضایی"), Student("40012346", "مریم احمدی")),
            RosterParser.parse(csv)
        )
    }

    @Test
    fun acceptsAnyColumnOrderSeparatorAndPersianDigits() {
        val csv = "﻿علی رضایی;۴۰۰۱۲۳۴۵\r\nمریم احمدی\t40012346\n\n"
        assertEquals(
            listOf(Student("40012345", "علی رضایی"), Student("40012346", "مریم احمدی")),
            RosterParser.parse(csv)
        )
    }

    @Test
    fun keepsFirstOfDuplicateIds() {
        val csv = "40012345,علی رضایی\n40012345,علی"
        assertEquals(listOf(Student("40012345", "علی رضایی")), RosterParser.parse(csv))
    }

    // کدگذاری 1256 «ی» فارسی ندارد و Excel «ي» عربی ذخیره می‌کند
    @Test
    fun readsWindows1256FilesAndFixesArabicLetters() {
        val bytes = "40012345,علي رضايي كاظمي".toByteArray(Charset.forName("windows-1256"))
        assertEquals(listOf(Student("40012345", "علی رضایی کاظمی")), RosterParser.parse(bytes))
    }
}
