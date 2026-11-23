package id.dompetku.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class ReportsTest {
    private fun tx(type: TxType, amount: Long, cat: Long?, iso: String) =
        Tx(0, type, amount, cat, 1, if (type == TxType.TRANSFER) 2 else null, null, Instant.parse(iso), null, null, null)

    private val nov = Period.thisMonth(Instant.parse("2026-11-15T03:00:00Z"))

    @Test fun transfersExcludedFromExpenseByCategory() {
        val txs = listOf(
            tx(TxType.EXPENSE, 50_000, 1, "2026-11-02T03:00:00Z"),
            tx(TxType.EXPENSE, 150_000, 2, "2026-11-03T03:00:00Z"),
            tx(TxType.EXPENSE, 30_000, 1, "2026-11-04T03:00:00Z"),
            tx(TxType.TRANSFER, 999_000, null, "2026-11-04T03:00:00Z"),
            tx(TxType.EXPENSE, 10_000, 3, "2026-10-04T03:00:00Z"),
        )
        assertEquals(listOf(2L to 150_000L, 1L to 80_000L), Reports.expenseByCategory(txs, nov))
    }

    private val now = Instant.parse("2026-11-11T05:00:00Z")

    @Test fun spendingRanksAndExcludesTransfers() {
        val txs = listOf(
            tx(TxType.EXPENSE, 50_000, 1, "2026-11-10T03:00:00Z"),
            tx(TxType.EXPENSE, 150_000, 2, "2026-11-11T03:00:00Z"),
            tx(TxType.EXPENSE, 30_000, 1, "2026-11-12T03:00:00Z"),
            tx(TxType.TRANSFER, 999_000, null, "2026-11-12T03:00:00Z"),
            tx(TxType.INCOME, 8_500_000, 9, "2026-11-12T03:00:00Z"),
            tx(TxType.EXPENSE, 100_000, 1, "2026-11-04T03:00:00Z"),
        )
        val r = Reports.spending(txs, Span.WEEK, 0, now)
        assertEquals(230_000L, r.total)
        assertEquals(100_000L, r.previousTotal)
        assertEquals(130, r.changePercent)
        assertEquals(listOf(2L to 150_000L, 1L to 80_000L), r.categories.map { it.categoryId to it.amount })
        assertEquals(150_000f / 230_000f, r.categories[0].share, 0.0001f)
    }

    @Test fun changeVsZeroPreviousIsNull() {
        val r = Reports.spending(listOf(tx(TxType.EXPENSE, 10_000, 1, "2026-11-10T03:00:00Z")), Span.MONTH, 0, now)
        assertEquals(null, r.changePercent)
        val empty = Reports.spending(emptyList(), Span.WEEK, -3, now)
        assertEquals(0L, empty.total)
        assertEquals(emptyList<CategorySpend>(), empty.categories)
    }

    @Test fun sixMonthsIncludesEmptyMonthsAsZero() {
        val txs = listOf(
            tx(TxType.INCOME, 8_500_000, 9, "2026-11-01T03:00:00Z"),
            tx(TxType.EXPENSE, 100_000, 1, "2026-09-10T03:00:00Z"),
            tx(TxType.TRANSFER, 5_000, null, "2026-09-10T03:00:00Z"),
        )
        val m = Reports.incomeVsExpenseByMonth(txs, 6, Instant.parse("2026-11-15T03:00:00Z"))
        assertEquals(6, m.size)
        assertEquals(YearMonth.of(2026, 6), m.first().month)
        assertEquals(MonthTotals(YearMonth.of(2026, 11), 8_500_000, 0), m.last())
        assertEquals(MonthTotals(YearMonth.of(2026, 9), 0, 100_000), m[3])
        assertEquals(MonthTotals(YearMonth.of(2026, 10), 0, 0), m[4])
    }
}
