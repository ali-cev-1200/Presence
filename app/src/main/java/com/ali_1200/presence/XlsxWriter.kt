package com.ali_1200.presence

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// ساخت فایل Excel واقعی (.xlsx) بدون کتابخانه؛ xlsx یک فایل zip شامل چند XML است
// سلول عددی (Number) در Excel عدد می‌ماند و بقیه متن هستند
object XlsxWriter {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun write(output: OutputStream, sheetName: String, rows: List<List<Any>>, rightToLeft: Boolean = true) {
        ZipOutputStream(output).use { zip ->
            zip.putFile("[Content_Types].xml", CONTENT_TYPES)
            zip.putFile("_rels/.rels", ROOT_RELS)
            zip.putFile("xl/workbook.xml", workbook(sheetName))
            zip.putFile("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.putFile("xl/worksheets/sheet1.xml", sheet(rows, rightToLeft))
        }
    }

    private fun ZipOutputStream.putFile(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun sheet(rows: List<List<Any>>, rightToLeft: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"""")
        if (rightToLeft) append(""" rightToLeft="1"""")
        append("/></sheetViews><sheetData>")
        rows.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 1
            append("""<row r="$rowNumber">""")
            row.forEachIndexed { columnIndex, value ->
                val ref = columnName(columnIndex) + rowNumber
                if (value is Number) {
                    append("""<c r="$ref"><v>$value</v></c>""")
                } else {
                    append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">""")
                    append(escape(value.toString()))
                    append("</t></is></c>")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun workbook(sheetName: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            """<sheets><sheet name="${escape(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    // ستون صفر ← A، ستون ۲۶ ← AA
    fun columnName(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            val remainder = (n - 1) % 26
            name.insert(0, 'A' + remainder)
            n = (n - 1) / 26
        }
        return name.toString()
    }

    // کاراکترهای ویژه XML و کاراکترهای کنترلی (که فایل را خراب می‌کنند)
    fun escape(text: String): String = buildString {
        text.forEach { c ->
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c < ' ' && c != '\t' && c != '\n' && c != '\r' -> Unit
                else -> append(c)
            }
        }
    }

    private const val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
            """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
            """</Types>"""

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private const val WORKBOOK_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
            """</Relationships>"""
}
