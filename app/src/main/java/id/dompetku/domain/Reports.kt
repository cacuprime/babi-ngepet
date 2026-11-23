package id.dompetku.domain

import java.time.Instant
import java.time.YearMonth

data class MonthTotals(val month: YearMonth, val income: Long, val expense: Long)

data class CategorySpend(val categoryId: Long, val amount: Long, val share: Float)

data class SpendingReport(
    val period: Period,
    val total: Long,
    val previousTotal: Long,
    /** Null when the previous period had no spending (no meaningful percentage). */
    val changePercent: Int?,
    val categories: List<CategorySpend>,
)

object Reports {
    /** Spending by category for a week or month, compared with the period before it. */
    fun spending(txs: List<Tx>, span: Span, offset: Int, now: Instant): SpendingReport {
        fun periodAt(o: Int) = if (span == Span.WEEK) Period.week(o, now) else Period.month(o, now)
        val period = periodAt(offset)
        val byCat = expenseByCategory(txs, period)
        val total = byCat.sumOf { it.second }
        val previous = expenseByCategory(txs, periodAt(offset - 1)).sumOf { it.second }
        return SpendingReport(
            period, total, previous,
            if (previous == 0L) null else ((total - previous) * 100 / previous).toInt(),
            byCat.map { (id, v) -> CategorySpend(id, v, if (total == 0L) 0f else v.toFloat() / total) },
        )
    }

    /** Expense totals per category within the period, largest first. Transfers never count. */
    fun expenseByCategory(txs: List<Tx>, period: Period): List<Pair<Long, Long>> =
        txs.asSequence()
            .filter { it.type == TxType.EXPENSE && it.categoryId != null && period.contains(it.date) }
            .groupBy { it.categoryId!! }
            .map { (cat, l) -> cat to l.sumOf { it.amount } }
            .sortedByDescending { it.second }

    /** Income and expense for the last [months] months ending with the current one (Jakarta), oldest first. */
    fun incomeVsExpenseByMonth(txs: List<Tx>, months: Int, now: Instant): List<MonthTotals> {
        val current = YearMonth.from(now.atZone(JAKARTA))
        val byMonth = txs.groupBy { YearMonth.from(it.date.atZone(JAKARTA)) }
        return (months - 1 downTo 0).map { back ->
            val m = current.minusMonths(back.toLong())
            val l = byMonth[m].orEmpty()
            MonthTotals(
                m,
                l.filter { it.type == TxType.INCOME }.sumOf { it.amount },
                l.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
            )
        }
    }
}
