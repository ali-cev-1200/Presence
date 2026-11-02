package com.ali_1200.presence

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianDateTest {
    @Test
    fun convertsKnownDates() {
        assertEquals(JalaliDate(1357, 11, 22), PersianDate.fromGregorian(1979, 2, 11))
        assertEquals(JalaliDate(1403, 1, 1), PersianDate.fromGregorian(2024, 3, 20))
        assertEquals(JalaliDate(1404, 1, 1), PersianDate.fromGregorian(2025, 3, 21))
        assertEquals(JalaliDate(1405, 8, 11), PersianDate.fromGregorian(2026, 11, 2))
        assertEquals(JalaliDate(1403, 12, 30), PersianDate.fromGregorian(2025, 3, 20)) // اسفند کبیسه
    }

    @Test
    fun formatsWithPaddedLatinDigits() {
        assertEquals("1405/01/09", JalaliDate(1405, 1, 9).toString())
    }

    @Test
    fun formatsLongDateWithWeekday() {
        // دوشنبه ۱۱ آبان ۱۴۰۵ = ۲ نوامبر ۲۰۲۶
        val timestamp = java.util.Calendar.getInstance().apply { set(2026, 10, 2, 10, 32) }.timeInMillis
        assertEquals("دوشنبه ۱۱ آبان ۱۴۰۵، ساعت ۱۰:۳۲", PersianDate.formatLong(timestamp))
    }

    @Test
    fun convertsDigitsBothWays() {
        assertEquals("۱۴۰۵/۰۸/۱۱", PersianDate.toPersianDigits("1405/08/11"))
        assertEquals("40012345", PersianDate.normalizeDigits("۴۰۰۱۲۳۴۵"))
        assertEquals("40012345", PersianDate.normalizeDigits("٤٠٠١٢٣٤٥"))
    }
}
