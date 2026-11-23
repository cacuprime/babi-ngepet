package id.dompetku.importer

import id.dompetku.domain.JAKARTA
import id.dompetku.domain.TxType
import org.w3c.dom.Element
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Reads CSV/XLSX statement exports: a header row, then one transaction per row. */
object TableParser {
    private enum class Col { DATE, BALANCE, TYPE, DEBIT, CREDIT, AMOUNT, DESC }

    private val patterns = linkedMapOf(
        Col.DATE to Regex("tanggal|^date|tgl|posting date|transaction date"),
        Col.BALANCE to Regex("saldo|balance"),
        Col.TYPE to Regex("^d/k$|^db/cr$|^dk$|^tipe|^type$|^jenis$|jenis transaksi|^d/c$"),
        Col.DEBIT to Regex("debit|keluar|withdraw|^db$"),
        Col.CREDIT to Regex("kredit|credit|masuk|deposit|^cr$"),
        Col.AMOUNT to Regex("jumlah|amount|nominal|mutasi"),
        Col.DESC to Regex("keterangan|deskripsi|description|uraian|detail|transaksi|remark|catatan"),
    )

    private fun header(row: List<String>): Map<Col, Int>? {
        val map = HashMap<Col, Int>()
        row.forEachIndexed { i, cell ->
            val c = cell.trim().lowercase()
            patterns.entries.firstOrNull { (col, re) -> col !in map && re.containsMatchIn(c) }?.let { map[it.key] = i }
        }
        val hasMoney = Col.AMOUNT in map || Col.DEBIT in map || Col.CREDIT in map
        return if (Col.DATE in map && hasMoney) map else null
    }

    private fun date(cell: String): LocalDate? {
        val s = cell.trim()
        Regex("""^\d{5}(\.\d+)?$""").matchEntire(s)?.let { return LocalDate.of(1899, 12, 30).plusDays(s.substringBefore('.').toLong()) }
        return Tokens.date(s, null)
    }

    private fun clean(cell: String) = cell.trim().removePrefix("(").removeSuffix(")").removeSuffix("CR").removeSuffix("DB")
        .removeSuffix("-").trim()

    private fun money(cell: String?): Long? = cell?.trim()?.takeIf { it.isNotEmpty() }?.let { Tokens.amount(clean(it)) }

    /** "-50.000", "Rp -50.000", "50.000-", "(50.000)", "50.000 DB" are money out. */
    private fun negative(raw: String): Boolean {
        val s = raw.trim()
        return s.startsWith("(") || s.endsWith("-") || s.endsWith("DB") || s.substringBefore(s.firstOrNull { it.isDigit() } ?: ' ').contains('-')
    }

    private fun typeOf(v: String?): TxType? {
        val s = v?.trim()?.lowercase() ?: return null
        return when {
            s.startsWith("d") -> TxType.EXPENSE
            s.startsWith("k") || s.startsWith("c") -> TxType.INCOME
            else -> null
        }
    }

    fun parse(rows: List<List<String>>): List<Draft> {
        val hi = rows.indexOfFirst { header(it) != null }
        if (hi < 0) return emptyList()
        val h = header(rows[hi])!!
        val body = rows.drop(hi + 1)
        val signed = h[Col.AMOUNT]?.let { col -> body.any { r -> r.getOrNull(col)?.let { negative(it) } == true } } == true
        var prevBalance: Long? = null
        return body.mapNotNull { r ->
            fun cell(c: Col) = h[c]?.let { r.getOrNull(it) }
            val d = cell(Col.DATE)?.let { date(it) } ?: return@mapNotNull null
            val balance = money(cell(Col.BALANCE))
            val debit = money(cell(Col.DEBIT)) ?: 0L
            val credit = money(cell(Col.CREDIT)) ?: 0L
            var confidence = 0.8f
            val (type, amount) = when {
                debit > 0 -> TxType.EXPENSE to debit
                credit > 0 -> TxType.INCOME to credit
                else -> {
                    val raw = cell(Col.AMOUNT)?.trim() ?: return@mapNotNull null
                    val v = money(raw) ?: return@mapNotNull null
                    val byType = typeOf(cell(Col.TYPE))
                    val t = when {
                        byType != null -> byType
                        negative(raw) -> TxType.EXPENSE
                        signed -> TxType.INCOME
                        balance != null && prevBalance != null && prevBalance!! - v == balance -> TxType.EXPENSE
                        balance != null && prevBalance != null && prevBalance!! + v == balance -> TxType.INCOME
                        // No sign, type or balance to go by: guess spending and ask the owner to check.
                        else -> { confidence = 0.5f; TxType.EXPENSE }
                    }
                    t to v
                }
            }
            prevBalance = balance
            if (amount <= 0) return@mapNotNull null
            val desc = cell(Col.DESC)?.trim().orEmpty()
            Draft(
                type, amount, d.atTime(LocalTime.NOON).atZone(JAKARTA).toInstant(), desc,
                desc.ifEmpty { null }, null, balance, confidence,
            )
        }
    }
}

/** Minimal CSV reader: quoted fields, "," or ";" delimiter (auto-detected from the first line). */
object Csv {
    fun rows(text: String): List<List<String>> {
        val first = text.lineSequence().firstOrNull().orEmpty()
        val delim = if (first.count { it == ';' } > first.count { it == ',' }) ';' else ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == delim -> { row.add(cell.toString()); cell.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(cell.toString()); cell.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = ArrayList()
                }
                else -> cell.append(ch)
            }
            i++
        }
        row.add(cell.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }
}

/** Minimal XLSX reader: first worksheet, shared and inline strings, numbers as text. */
object Xlsx {
    fun rows(input: InputStream): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == "xl/sharedStrings.xml" || e.name.startsWith("xl/worksheets/sheet")) files[e.name] = z.readBytes()
            }
        }
        val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val shared = files["xl/sharedStrings.xml"]?.let { bytes ->
            val si = db.parse(bytes.inputStream()).getElementsByTagName("si")
            (0 until si.length).map { (si.item(it) as Element).textContent }
        }.orEmpty()
        val sheetName = files.keys.filter { it.startsWith("xl/worksheets/sheet") }.minByOrNull { it.length * 1000 + it.hashCode() % 1000 }
            ?: return emptyList()
        val rows = db.parse(files[sheetName]!!.inputStream()).getElementsByTagName("row")
        return (0 until rows.length).map { ri ->
            val cells = (rows.item(ri) as Element).getElementsByTagName("c")
            val out = ArrayList<String>()
            for (ci in 0 until cells.length) {
                val c = cells.item(ci) as Element
                val col = c.getAttribute("r").takeWhile { it.isLetter() }.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
                val v = c.getElementsByTagName("v").item(0)?.textContent
                val text = when (c.getAttribute("t")) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> c.getElementsByTagName("t").item(0)?.textContent.orEmpty()
                    else -> v.orEmpty()
                }
                while (out.size < col) out.add("")
                out.add(text)
            }
            out
        }
    }
}
