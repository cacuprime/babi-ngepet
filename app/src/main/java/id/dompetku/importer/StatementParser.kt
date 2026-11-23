package id.dompetku.importer

import id.dompetku.core.Rupiah
import id.dompetku.domain.JAKARTA
import id.dompetku.domain.TxType
import java.time.LocalDate
import java.time.LocalTime

/**
 * Reads bank statement text (BSI, SeaBank and similar layouts) into drafts.
 *
 * A row starts at a date line ("25 Okt 2026" or "01 OCT"), may have a time line, then description
 * lines, and ends at a line finishing with amounts: "debit credit balance" or "amount balance".
 * Every row is checked against the running balance (from "Saldo Awal"), in sen so cents never break
 * the check. A line that fails the check is treated as more description, never as a row, so page
 * headers and numbers inside descriptions can't create, flip or drop rows.
 */
object StatementParser {
    private val DATE_LINE = Regex("""^(\d{1,2}) ([A-Za-z]{3,9})\.?(?: (\d{4}))?$""")
    private val TIME_LINE = Regex("""^\d{1,2}[:.]\d{2}(?::\d{2})?$""")
    private val TABLE_HEADER = Regex("""(tanggal|date).*(saldo|balance)""", RegexOption.IGNORE_CASE)
    private val YEAR_AFTER_MONTH = Regex("""([A-Za-z]{3,9})\.? (\d{4})""")
    private val DAY_MONTH_YEAR = Regex("""(\d{1,2})[ \-]([A-Za-z]{3,9})\.?(?:[ \-](\d{4}))?""")
    private val PERIOD_WORDS = Regex("""sampai|periode|period|s/d|hingga| - |–""", RegexOption.IGNORE_CASE)
    private val NOISE = listOf("halaman", "s/n ", "rekening koran", "laporan rekening", "page ")
    private val OUT_WORDS = Regex("""keluar|transfer ke|trf ke|pembayaran|pajak|debit|tarik|biaya""", RegexOption.IGNORE_CASE)
    private val REF_TOKEN = Regex("""^(?=.*\d)(?=.*[A-Za-z])[A-Za-z0-9/\-]{6,}$""")
    private val STATEMENT_WORDS = Regex(
        """saldo awal|rekening koran|e-statement|mutasi rekening|laporan rekening|account statement|ringkasan rekening""",
        RegexOption.IGNORE_CASE,
    )

    private class Row(val date: LocalDate, var time: LocalTime? = null, val desc: MutableList<String> = mutableListOf())

    /** True for bank statement text, so an unreadable statement is reported instead of read as a receipt. */
    fun looksLikeStatement(lines: List<String>): Boolean =
        lines.any { STATEMENT_WORDS.containsMatchIn(it) || TABLE_HEADER.containsMatchIn(it) }

    fun parse(lines: List<String>): List<Draft> {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val baseYear = statementYear(clean) ?: return emptyList()
        var prevBalance = openingCents(clean)
        var year = baseYear
        var lastMonth = -1
        var started = false
        var row: Row? = null
        val out = ArrayList<Draft>()

        for (line in clean) {
            if (TABLE_HEADER.containsMatchIn(line)) { started = true; row = null; continue }
            if (!started) continue
            val lower = line.lowercase()
            if (NOISE.any { lower.contains(it) }) { row = null; continue }

            val dm = DATE_LINE.matchEntire(line)
            val dMonth = dm?.let { Tokens.month(it.groupValues[2]) }
            if (dm != null && dMonth != null) {
                val y = dm.groupValues[3].toIntOrNull() ?: if (lastMonth != -1 && dMonth < lastMonth) year + 1 else year
                row = runCatching { Row(LocalDate.of(y, dMonth, dm.groupValues[1].toInt())) }.getOrNull()
                continue
            }

            val r = row ?: continue
            if (r.time == null && r.desc.isEmpty() && TIME_LINE.matches(line)) { r.time = Tokens.time(line); continue }

            val nums = Tokens.centsAtEnd(line)
            if (nums.size < 2) { r.desc.add(line); continue }

            val balance = nums.last()
            val parsed: Pair<TxType, Long>? = if (nums.size >= 3) {
                val debit = nums[nums.size - 3]; val credit = nums[nums.size - 2]
                when {
                    prevBalance != null && prevBalance - debit + credit != balance -> null
                    debit > 0 -> TxType.EXPENSE to debit
                    else -> TxType.INCOME to credit
                }
            } else {
                val amount = nums[0]
                when {
                    prevBalance == null -> null
                    prevBalance - amount == balance -> TxType.EXPENSE to amount
                    prevBalance + amount == balance -> TxType.INCOME to amount
                    else -> null
                }
            }
            val tokens = line.split(Regex("\\s+"))
            val lead = tokens.dropLast(nums.size)
            val reference = lead.singleOrNull()?.takeIf { REF_TOKEN.matches(it) }

            if (parsed == null && prevBalance != null) {
                // Doesn't fit the running balance: it's part of the description (e.g. "BI-FAST 014 1234567").
                r.desc.add(line); continue
            }
            val extraText = if (reference == null && lead.isNotEmpty()) lead.joinToString(" ") else null
            val description = (r.desc + listOfNotNull(extraText)).joinToString(" ")
            val result = parsed ?: if (nums.size == 2) {
                // No running balance known at all: fall back to wording, with low confidence.
                (if (OUT_WORDS.containsMatchIn(description)) TxType.EXPENSE else TxType.INCOME) to nums[0]
            } else null
            row = null
            if (result == null || result.second <= 0) continue

            val confidence = if (parsed != null && prevBalance != null) 0.9f else if (parsed != null) 0.8f else 0.5f
            out.add(
                Draft(
                    type = result.first, amount = result.second / 100,
                    date = r.date.atTime(r.time ?: LocalTime.NOON).atZone(JAKARTA).toInstant(),
                    description = description, merchant = r.desc.firstOrNull(), reference = reference,
                    balanceAfter = balance / 100, confidence = confidence,
                )
            )
            prevBalance = balance
            year = r.date.year
            lastMonth = r.date.monthValue
        }
        return out
    }

    /**
     * A warning when the last balance read differs from the statement's closing balance
     * (some rows were not read). Null when they match or no closing balance is printed.
     */
    fun check(lines: List<String>, drafts: List<Draft>): String? {
        val closing = closingBalance(lines.map { it.trim() }.filter { it.isNotEmpty() }) ?: return null
        val last = drafts.lastOrNull()?.balanceAfter ?: return null
        if (last == closing) return null
        return "Saldo akhir hasil baca ${Rupiah.full(last)} tidak sama dengan rekening koran ${Rupiah.full(closing)}. " +
            "Mungkin ada transaksi yang terlewat — periksa lagi."
    }

    /** Year of the statement period ("01 DEC 2026 sampai 31 DEC 2026", ": 01 Okt - 31 Okt 2026"), not the print date. */
    private fun statementYear(lines: List<String>): Int? {
        for (l in lines) {
            if (!PERIOD_WORDS.containsMatchIn(l)) continue
            val dates = DAY_MONTH_YEAR.findAll(l).mapNotNull { m ->
                Tokens.month(m.groupValues[2])?.let { Triple(m.groupValues[1].toInt(), it, m.groupValues[3].toIntOrNull()) }
            }.toList()
            if (dates.size < 2) continue
            val (_, startMonth, startYear) = dates.first()
            val endYear = dates.last().third ?: startYear ?: continue
            return startYear ?: if (startMonth > dates.last().second) endYear - 1 else endYear
        }
        return lines.firstNotNullOfOrNull { l ->
            YEAR_AFTER_MONTH.findAll(l).firstOrNull { Tokens.month(it.groupValues[1]) != null }?.groupValues?.get(2)?.toInt()
        }
    }

    /** "Saldo Awal : 5.000.000,00" on one line, or a "SALDO AWAL" column header followed by a row of numbers (sen). */
    private fun openingCents(lines: List<String>): Long? = labelledBalance(lines, "saldo awal") { it.first() }

    private fun closingBalance(lines: List<String>): Long? = labelledBalance(lines, "saldo akhir") { it.last() }?.div(100)

    private fun labelledBalance(lines: List<String>, label: String, pick: (List<Long>) -> Long): Long? {
        lines.forEachIndexed { i, l ->
            if (!l.contains(label, ignoreCase = true) || TABLE_HEADER.containsMatchIn(l)) return@forEachIndexed
            val same = Tokens.centsAtEnd(l)
            if (same.size == 1) return same[0]
            for (j in i + 1 until minOf(i + 4, lines.size)) {
                val nums = Tokens.centsAtEnd(lines[j])
                if (nums.size >= 2) return pick(nums)
                if (nums.size == 1 && lines[j].split(' ').size == 1) return nums[0]
            }
        }
        return null
    }
}
