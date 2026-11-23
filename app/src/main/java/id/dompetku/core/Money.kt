package id.dompetku.core

import kotlin.math.abs

/** Rupiah formatting. All amounts are whole rupiah as Long. */
object Rupiah {
    /** Shown instead of an amount when balances are hidden. */
    const val MASK = "Rp •••••"

    fun full(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        return "${sign}Rp${groupDots(abs(amount))}"
    }

    fun signed(amount: Long): String = if (amount > 0) "+${full(amount)}" else full(amount)

    /** Compact form for tight spots: 950, 500rb, 8,5jt, 1,25M. */
    fun compact(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        val a = abs(amount)
        return sign + when {
            a >= 1_000_000_000L -> decimal(a, 1_000_000_000L) + "M"
            a >= 1_000_000L -> decimal(a, 1_000_000L) + "jt"
            a >= 1_000L -> decimal(a, 1_000L) + "rb"
            else -> a.toString()
        }
    }

    /** Digits only (separators ignored). Null when there are no digits or any other character. */
    fun parseDigits(input: String): Long? {
        val cleaned = input.trim().replace(".", "").replace(" ", "")
        if (cleaned.isEmpty() || cleaned.any { !it.isDigit() } || cleaned.length > 15) return null
        return cleaned.toLong()
    }

    fun groupDots(n: Long): String {
        val s = n.toString()
        val sb = StringBuilder()
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }

    /** Up to 2 decimals, truncated, trailing zeros dropped, comma separator. */
    private fun decimal(a: Long, unit: Long): String {
        val whole = a / unit
        val hundredths = (a % unit) * 100 / unit
        if (hundredths == 0L) return whole.toString()
        val frac = if (hundredths % 10 == 0L) (hundredths / 10).toString() else "%02d".format(hundredths)
        return "$whole,$frac"
    }
}

/**
 * Fund units as Long ×10 000, typed the way Bibit shows them: "1.234,5678" (dot = thousands, comma = decimal).
 * "1.234" alone is read as 1 234 units (thousands dots); "1234.5678" (no grouping) as a decimal.
 */
object Units {
    private val GROUPED = Regex("""^\d{1,3}(\.\d{3})+$""")

    fun parse(input: String): Long? {
        var s = input.trim().replace(" ", "")
        if (s.isEmpty() || s.count { it == ',' } > 1) return null
        s = when {
            s.contains(',') -> s.replace(".", "").replace(',', '.')
            GROUPED.matches(s) -> s.replace(".", "")
            else -> s
        }
        val parts = s.split('.')
        if (parts.size > 2 || parts.any { p -> p.any { !it.isDigit() } }) return null
        val whole = parts[0].ifEmpty { "0" }; val frac = parts.getOrElse(1) { "" }
        if (frac.length > 4 || whole.length > 12) return null
        return whole.toLong() * 10_000 + frac.padEnd(4, '0').toLong()
    }

    /** Ungrouped "1234,5678" for editing (re-parses to the same value). */
    fun plain(e4: Long): String {
        val frac = (e4 % 10_000).toString().padStart(4, '0').trimEnd('0')
        return (e4 / 10_000).toString() + if (frac.isEmpty()) "" else ",$frac"
    }
}

/** Gold quantities as Long ten-thousandths of a gram (10,5000 gr = 105000). */
object Grams {
    private const val SCALE = 10_000L

    fun format(tenThousandths: Long): String {
        val whole = tenThousandths / SCALE
        val frac = abs(tenThousandths % SCALE)
        return "${Rupiah.groupDots(whole)},${"%04d".format(frac)} gr"
    }

    fun parse(input: String): Long? {
        val s = input.trim().removeSuffix("gr").trim().replace(',', '.')
        if (s.isEmpty()) return null
        val parts = s.split('.')
        if (parts.size > 2) return null
        val whole = parts[0].ifEmpty { "0" }
        val frac = parts.getOrElse(1) { "" }
        if (whole.any { !it.isDigit() } || frac.any { !it.isDigit() } || frac.length > 4 || whole.length > 9) return null
        return whole.toLong() * SCALE + frac.padEnd(4, '0').toLong()
    }

    /** grams × price per gram, rounded half-up to whole rupiah. */
    fun valueOf(tenThousandths: Long, pricePerGram: Long): Long =
        (tenThousandths * pricePerGram + SCALE / 2) / SCALE
}
