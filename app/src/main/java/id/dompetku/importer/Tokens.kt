package id.dompetku.importer

import java.time.LocalDate
import java.time.LocalTime

/** Low-level readers for amounts, dates and times in Indonesian bank/e-wallet text. */
object Tokens {
    private val ID_AMOUNT = Regex("""^\d{1,3}(\.\d{3})+(,\d{1,2})?$|^\d+,\d{1,2}$""")
    private val EN_AMOUNT = Regex("""^\d{1,3}(,\d{3})+(\.\d{1,2})?$""")
    private val PLAIN = Regex("""^\d{1,9}$""")

    private val DEC = Regex("""^\d+\.\d{1,2}$""")

    /** Value in sen (1/100 rupiah) of one token like "Rp125.000", "50.000,00", "1,250,000", "150000.00", "266"; null otherwise. */
    fun cents(s: String): Long? {
        val t = s.trim().removePrefix("Rp").removePrefix("RP").removePrefix("rp").trim().removePrefix("-").trim()
            .replace(" ", "").trimEnd('.', ',', '-')
        if (t.isEmpty()) return null
        fun join(whole: String, frac: String) = whole.toLongOrNull()?.let { it * 100 + frac.padEnd(2, '0').take(2).toLong() }
        return when {
            ID_AMOUNT.matches(t) -> join(t.substringBefore(',').replace(".", ""), t.substringAfter(',', ""))
            EN_AMOUNT.matches(t) -> join(t.substringBefore('.').replace(",", ""), t.substringAfter('.', ""))
            DEC.matches(t) -> join(t.substringBefore('.'), t.substringAfter('.'))
            PLAIN.matches(t) -> t.toLongOrNull()?.times(100)
            else -> null
        }
    }

    /** Whole-rupiah value of one amount token (sen dropped); null if it isn't an amount. */
    fun amount(s: String): Long? = cents(s)?.div(100)

    /** Consecutive amount tokens at the end of a line, in sen (left to right). Long digit runs (account numbers) stop it. */
    fun centsAtEnd(line: String): List<Long> {
        val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = ArrayList<Long>()
        for (tok in tokens.asReversed()) {
            val v = cents(tok) ?: break
            out.add(v)
        }
        return out.asReversed()
    }

    /** Same as [centsAtEnd], in whole rupiah. */
    fun amountsAtEnd(line: String): List<Long> = centsAtEnd(line).map { it / 100 }

    private val MONTHS: Map<String, Int> = buildMap {
        listOf(
            1 to listOf("jan", "januari", "january"), 2 to listOf("feb", "februari", "february", "peb"),
            3 to listOf("mar", "maret", "march"), 4 to listOf("apr", "april"),
            5 to listOf("mei", "may"), 6 to listOf("jun", "juni", "june"), 7 to listOf("jul", "juli", "july"),
            8 to listOf("agu", "agt", "aug", "agustus", "august"),
            9 to listOf("sep", "sept", "september"), 10 to listOf("okt", "oct", "oktober", "october"),
            11 to listOf("nov", "nopember", "november"), 12 to listOf("des", "dec", "desember", "december"),
        ).forEach { (m, names) -> names.forEach { put(it, m) } }
    }

    fun month(s: String): Int? = MONTHS[s.trim().trimEnd('.').lowercase()]

    private val DMY_NAME = Regex("""(?<!\d)(\d{1,2})[ \-]([A-Za-z]{3,9})\.?(?:[ \-](\d{4}))?(?!\w)""")
    private val DMY_SLASH = Regex("""(?<!\d)(\d{1,2})/(\d{1,2})/(\d{2,4})(?!\d)""")
    private val ISO = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")

    /** First date in the text. A month-name date without a year uses [defaultYear] (null → not a date). */
    fun date(s: String, defaultYear: Int?): LocalDate? {
        ISO.find(s)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        DMY_SLASH.find(s)?.let { m ->
            val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            return runCatching { LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        for (m in DMY_NAME.findAll(s)) {
            val month = month(m.groupValues[2]) ?: continue
            val year = m.groupValues[3].toIntOrNull() ?: defaultYear ?: return null
            return runCatching { LocalDate.of(year, month, m.groupValues[1].toInt()) }.getOrNull()
        }
        return null
    }

    private val TIME = Regex("""(?<![\d.,])(\d{1,2})[:.](\d{2})(?::\d{2})?(?![\d.,])""")

    fun time(s: String): LocalTime? = TIME.findAll(s).firstNotNullOfOrNull { m ->
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        if (h < 24 && min < 60) LocalTime.of(h, min) else null
    }
}
