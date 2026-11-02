package com.ali_1200.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {
    private fun unzip(bytes: ByteArray): Map<String, String> {
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return files
    }

    @Test
    fun writesAllPartsOfWorkbook() {
        val output = ByteArrayOutputStream()
        XlsxWriter.write(output, "حضور و غیاب", listOf(listOf("نام", "تعداد"), listOf("علی & <مریم>", 3)))
        val files = unzip(output.toByteArray())

        assertEquals(
            setOf(
                "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml"
            ),
            files.keys
        )

        val sheet = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("""rightToLeft="1""""))
        assertTrue(sheet.contains("علی &amp; &lt;مریم&gt;"))
        assertTrue(sheet.contains("""<c r="B2"><v>3</v></c>"""))
        assertTrue(files.getValue("xl/workbook.xml").contains("حضور و غیاب"))
    }

    @Test
    fun namesColumnsLikeExcel() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AZ", XlsxWriter.columnName(51))
        assertEquals("BA", XlsxWriter.columnName(52))
    }

    @Test
    fun dropsControlCharacters() {
        assertEquals("ab", XlsxWriter.escape("a\u0001b"))
    }
}
