package com.ali_1200.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxReaderTest {
    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    // فایلی که خود برنامه می‌سازد (متن inline و عدد)
    @Test
    fun readsFileWrittenByXlsxWriter() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(
            out, "کلاس",
            listOf(
                listOf("ردیف", "کد دانشجویی", "نام و نام خانوادگی"),
                listOf(1, 40012345L, "علی رضایی"),
                listOf(2, "40012346", "مریم احمدی")
            )
        )
        val bytes = out.toByteArray()
        assertTrue(XlsxReader.isXlsx(bytes))
        assertEquals(
            listOf(Student("40012345", "علی رضایی"), Student("40012346", "مریم احمدی")),
            RosterParser.parse(bytes)
        )
    }

    // ساختار فایل Excel واقعی: متن مشترک (sharedStrings)، عدد علمی، ستون خالی، برگه با نام دیگر
    @Test
    fun readsExcelStyleFile() {
        val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val bytes = zip(
            "xl/workbook.xml" to """<workbook xmlns="$ns" xmlns:r="$rel"><sheets><sheet name="لیست" sheetId="1" r:id="rId3"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId3" Target="worksheets/list.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst xmlns="$ns"><si><t>نام</t></si><si><r><t>علی </t></r><r><t>رضایی</t></r></si><si><t>40012346</t></si><si><t>مريم احمدي</t></si></sst>""",
            "xl/worksheets/list.xml" to """<worksheet xmlns="$ns"><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c></row>
                <row r="2"><c r="A2" t="s"><v>1</v></c><c r="C2"><v>4.0012345E7</v></c></row>
                <row r="3"><c r="B3" t="s"><v>2</v></c><c r="C3" t="s"><v>3</v></c></row>
            </sheetData></worksheet>"""
        )

        assertEquals(listOf(listOf("نام"), listOf("علی رضایی", "", "40012345"), listOf("", "40012346", "مريم احمدي")),
            XlsxReader.readFirstSheet(bytes))
        assertEquals(
            listOf(Student("40012345", "علی رضایی"), Student("40012346", "مریم احمدی")),
            RosterParser.parse(bytes)
        )
    }

    @Test
    fun rejectsBrokenFiles() {
        assertFalse(XlsxReader.isXlsx("کد,نام".toByteArray()))
        assertThrows(XlsxReader.InvalidXlsxException::class.java) { XlsxReader.readFirstSheet(zip("a.txt" to "x")) }
        // DTD (حمله XXE) پذیرفته نمی‌شود
        val xxe = zip("xl/worksheets/sheet1.xml" to """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><worksheet><sheetData><row><c t="str"><v>&e;</v></c></row></sheetData></worksheet>""")
        assertThrows(XlsxReader.InvalidXlsxException::class.java) { XlsxReader.readFirstSheet(xxe) }
    }
}
