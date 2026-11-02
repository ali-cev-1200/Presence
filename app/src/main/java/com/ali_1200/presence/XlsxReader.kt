package com.ali_1200.presence

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

// خواندن اولین برگه فایل Excel (.xlsx) بدون کتابخانه؛ فقط مقدار سلول‌ها، بدون فرمول و قالب
// xlsx یک فایل zip است: xl/workbook.xml فهرست برگه‌ها، xl/sharedStrings.xml متن‌ها و xl/worksheets/*.xml سلول‌ها
object XlsxReader {
    // جلوگیری از فایل zip دست‌کاری‌شده که باز شدنش حافظه را پر می‌کند (zip bomb)
    private const val MAX_UNZIPPED_SIZE = 20 * 1024 * 1024

    class InvalidXlsxException : Exception()

    // فایل zip با «PK» شروع می‌شود؛ CSV هیچ‌وقت
    fun isXlsx(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2].toInt() == 3 && bytes[3].toInt() == 4

    fun readFirstSheet(bytes: ByteArray): List<List<String>> {
        val files = unzip(bytes)
        val sharedStrings = files["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheet = files[firstSheetPath(files)] ?: throw InvalidXlsxException()
        return parseSheet(sheet, sharedStrings)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val files = mutableMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.endsWith(".xml") && !entry.name.endsWith(".rels")) continue
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_UNZIPPED_SIZE) throw InvalidXlsxException()
                    out.write(buffer, 0, read)
                }
                files[entry.name.removePrefix("/")] = out.toByteArray()
            }
        }
        return files
    }

    // اولین برگه workbook.xml و مسیر فایلش از workbook.xml.rels
    private fun firstSheetPath(files: Map<String, ByteArray>): String {
        val fallback = "xl/worksheets/sheet1.xml"
        val workbook = files["xl/workbook.xml"]?.let(::parse) ?: return fallback
        val relationId = workbook.elements("sheet").firstOrNull()
            ?.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            ?.takeIf { it.isNotEmpty() } ?: return fallback
        val rels = files["xl/_rels/workbook.xml.rels"]?.let(::parse) ?: return fallback
        val target = rels.elements("Relationship").firstOrNull { it.getAttribute("Id") == relationId }
            ?.getAttribute("Target") ?: return fallback
        return if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
    }

    // هر <si> یک متن است؛ متن چندتکه (رنگی/پررنگ) از چند <t> تشکیل می‌شود
    private fun parseSharedStrings(bytes: ByteArray): List<String> =
        parse(bytes).elements("si").map(::cellText)

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> =
        parse(bytes).elements("row").map { row ->
            val cells = sortedMapOf<Int, String>()
            row.elements("c").forEachIndexed { position, cell ->
                // ستون از روی مرجع سلول (مثل C7)؛ سلول‌های خالی در فایل نیستند
                val column = columnIndex(cell.getAttribute("r")) ?: position
                cells[column] = cellValue(cell, sharedStrings)
            }
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            (0 until width).map { cells[it].orEmpty() }
        }

    private fun cellValue(cell: Element, sharedStrings: List<String>): String {
        val value = cell.elements("v").firstOrNull()?.textContent.orEmpty().trim()
        return when (cell.getAttribute("t")) {
            "s" -> value.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
            "inlineStr" -> cell.elements("is").firstOrNull()?.let(::cellText).orEmpty()
            "str", "b", "e" -> value
            // کد دانشجویی عددی ممکن است «4.0012345E7» یا «40012345.0» ذخیره شده باشد
            else -> value.toBigDecimalOrNull()?.let(::plainNumber) ?: value
        }
    }

    private fun plainNumber(number: BigDecimal): String =
        if (number.signum() == 0) "0" else number.stripTrailingZeros().toPlainString()

    // متن‌های <t> به جز تلفظ (<rPh>) که Excel ژاپنی اضافه می‌کند
    private fun cellText(element: Element): String =
        element.elements("t").filter { (it.parentNode as? Element)?.localName != "rPh" }
            .joinToString("") { it.textContent }

    // «AB12» ← ۲۷
    private fun columnIndex(reference: String): Int? {
        val letters = reference.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        return letters.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
    }

    private fun parse(bytes: ByteArray): Element = try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // فایل xlsx واقعی DTD ندارد؛ جلوی حمله XXE گرفته می‌شود
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    } catch (e: Exception) {
        throw InvalidXlsxException()
    }

    // همه زیرعنصرها با این نام (بدون توجه به پیشوند فضای نام مثل x:row)
    private fun Element.elements(localName: String): List<Element> {
        val list = getElementsByTagNameNS("*", localName)
        return (0 until list.length).mapNotNull { list.item(it) as? Element }
            .filter { it.nodeType == Node.ELEMENT_NODE }
    }
}
