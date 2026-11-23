package id.dompetku.core

/**
 * Keypad calculator: whole rupiah, + − × ÷ with the usual precedence (× ÷ before + −),
 * division rounds down, ÷0 counts as 0. A trailing operator is ignored ("12000+" = 12000).
 */
object Calc {
    const val OPS = "+-×÷"

    fun eval(expr: String): Long {
        val tokens = Regex("""\d+|[+\-×÷]""").findAll(expr).map { it.value }.toMutableList()
        while (tokens.isNotEmpty() && tokens.last() in OPS.map { it.toString() }) tokens.removeAt(tokens.size - 1)
        if (tokens.isEmpty()) return 0
        // First pass: × and ÷ into terms; second: sum the terms.
        val terms = ArrayList<Long>()
        val signs = ArrayList<Int>()
        var cur = tokens[0].toLongOrNull() ?: return 0
        var sign = 1
        var i = 1
        while (i + 1 < tokens.size) {
            val op = tokens[i]; val n = tokens[i + 1].toLongOrNull() ?: 0
            when (op) {
                "×" -> cur = sat(cur, n)
                "÷" -> cur = if (n == 0L) 0 else cur / n
                else -> { terms += cur; signs += sign; sign = if (op == "-") -1 else 1; cur = n }
            }
            i += 2
        }
        terms += cur; signs += sign
        return terms.indices.sumOf { terms[it] * signs[it] }
    }

    private fun sat(a: Long, b: Long): Long = if (b != 0L && a > Long.MAX_VALUE / b) Long.MAX_VALUE else a * b
}
