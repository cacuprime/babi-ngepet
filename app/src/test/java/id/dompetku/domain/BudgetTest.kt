package id.dompetku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BudgetTest {
    private fun tx(type: TxType, amount: Long, cat: Long?, iso: String, src: Long = 1, dst: Long? = null) =
        Tx(0, type, amount, cat, src, dst, null, Instant.parse(iso), null, null, null)

    // 1 = Makan (NEEDS), 2 = Hiburan (null → WANTS), 3 = Bakso (child of 1), 4 = Investasi (SAVINGS)
    private val buckets = Budgets.bucketMap(listOf(
        CategoryInfo(1, null, "NEEDS"), CategoryInfo(2, null, null), CategoryInfo(3, 1, "WANTS"), CategoryInfo(4, null, "SAVINGS"),
    ))
    private val accounts = mapOf(1L to AccountType.BANK, 2L to AccountType.SAVINGS, 3L to AccountType.EWALLET)
    private val nov = Period.custom(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
    private val budget = Budget(1, nov, 1, BudgetMode.FIXED, 0, emptyMap(),
        mapOf(Bucket.NEEDS to 3_000_000, Bucket.WANTS to 1_500_000, Bucket.SAVINGS to 1_000_000))

    @Test fun subcategoryFollowsParentAndNullIsWants() {
        assertEquals(Bucket.NEEDS, buckets[3])
        assertEquals(Bucket.WANTS, buckets[2])
    }

    @Test fun monthlyPeriodFromPayday() {
        assertEquals(Period.custom(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24)),
            Budgets.monthlyPeriod(25, Instant.parse("2026-11-20T03:00:00Z")))
        assertEquals(Period.custom(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 24)),
            Budgets.monthlyPeriod(25, Instant.parse("2026-11-25T03:00:00Z")))
    }

    @Test fun nextPeriodMonthlyAndCustom() {
        assertEquals(Period.custom(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)), Budgets.nextPeriod(budget))
        val twoWeeks = budget.copy(startDay = null, period = Period.custom(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14)))
        assertEquals(Period.custom(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 28)), Budgets.nextPeriod(twoWeeks))
    }

    @Test fun usageCountsExpensesAndSavingsTransfers() {
        val txs = listOf(
            tx(TxType.EXPENSE, 100_000, 1, "2026-11-02T03:00:00Z"),
            tx(TxType.EXPENSE, 50_000, 3, "2026-11-02T04:00:00Z"),
            tx(TxType.EXPENSE, 1_600_000, 2, "2026-11-03T03:00:00Z"),
            tx(TxType.EXPENSE, 200_000, 4, "2026-11-03T03:00:00Z"),
            tx(TxType.TRANSFER, 300_000, null, "2026-11-04T03:00:00Z", src = 1, dst = 2), // bank → savings
            tx(TxType.TRANSFER, 70_000, null, "2026-11-04T03:00:00Z", src = 1, dst = 3), // bank → e-wallet: not saving
            tx(TxType.EXPENSE, 999_000, 1, "2026-10-31T03:00:00Z"), // before the period
        )
        val u = Budgets.usage(budget, txs, buckets, accounts).associateBy { it.bucket }
        assertEquals(150_000L, u.getValue(Bucket.NEEDS).spent)
        assertEquals(1_600_000L, u.getValue(Bucket.WANTS).spent)
        assertEquals(true, u.getValue(Bucket.WANTS).over)
        assertEquals(106, u.getValue(Bucket.WANTS).percent)
        assertEquals(500_000L, u.getValue(Bucket.SAVINGS).spent)
    }

    @Test fun dailyAllowanceSplitsWhatIsLeft() {
        // 4.5 jt for needs + wants, 1.2 jt spent before 21 Nov, 10 days left (21–30) → 330 000 a day.
        val txs = listOf(
            tx(TxType.EXPENSE, 1_200_000, 1, "2026-11-05T03:00:00Z"),
            tx(TxType.EXPENSE, 12_000, 2, "2026-11-21T02:00:00Z"),
        )
        val d = Budgets.daily(budget, txs, buckets, accounts, Instant.parse("2026-11-21T05:00:00Z"))!!
        assertEquals(10, d.daysLeft)
        assertEquals(330_000L, d.allowance)
        assertEquals(318_000L, d.leftToday)
        assertNull(Budgets.daily(budget, txs, buckets, accounts, Instant.parse("2026-12-02T05:00:00Z")))
    }

    @Test fun allowanceNeverNegative() {
        val txs = listOf(tx(TxType.EXPENSE, 9_000_000, 1, "2026-11-05T03:00:00Z"))
        assertEquals(0L, Budgets.daily(budget, txs, buckets, accounts, Instant.parse("2026-11-21T05:00:00Z"))!!.allowance)
    }

    @Test fun newlyOverOnlyWhenCrossing() {
        val before = listOf(tx(TxType.EXPENSE, 1_400_000, 2, "2026-11-03T03:00:00Z"))
        val push = tx(TxType.EXPENSE, 200_000, 2, "2026-11-04T03:00:00Z")
        assertNotNull(Budgets.newlyOver(budget, before, push, buckets, accounts))
        assertNull(Budgets.newlyOver(budget, before + push, tx(TxType.EXPENSE, 1_000, 2, "2026-11-05T03:00:00Z"), buckets, accounts))
    }

    @Test fun limitsFromPercents() {
        assertEquals(mapOf(Bucket.NEEDS to 4_000_000L, Bucket.WANTS to 2_400_000L, Bucket.SAVINGS to 1_600_000L),
            Budgets.limitsFrom(8_000_000, Budgets.DEFAULT_PERCENTS))
    }
}
