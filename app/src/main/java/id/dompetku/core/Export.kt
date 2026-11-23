package id.dompetku.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a table (first row = header) as CSV or a minimal Excel .xlsx. Numbers stay numbers in Excel. */
object Export {
    fun csv(rows: List<List<Any?>>): ByteArray {
        fun cell(v: Any?): String {
            val s = v?.toString() ?: ""
            return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        }
        // BOM so Excel opens UTF-8 (Rp, é, emoji) correctly.
        return ("﻿" + rows.joinToString("\r\n") { r -> r.joinToString(",") { cell(it) } } + "\r\n").toByteArray()
    }

    fun xlsx(rows: List<List<Any?>>, sheetName: String = "Transaksi"): ByteArray {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            .filter { it == '\t' || it == '\n' || it == '\r' || it >= ' ' }
        fun col(i: Int): String { var n = i + 1; var s = ""; while (n > 0) { s = ('A' + (n - 1) % 26) + s; n = (n - 1) / 26 }; return s }
        val sheet = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { r, row ->
            sheet.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, v ->
                val ref = col(c) + (r + 1)
                when (v) {
                    null -> {}
                    is Number -> sheet.append("<c r=\"$ref\"><v>$v</v></c>")
                    else -> sheet.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v.toString())}</t></is></c>")
                }
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")
        val files = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${esc(sheetName)}" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheet.toString(),
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (n, s) -> z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() } }
        return out.toByteArray()
    }
}
