package id.dompetku.domain

import id.dompetku.core.Grams
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LedgerTest {
    private val day1 = Instant.parse("2026-11-01T03:00:00Z")
    private val day2 = Instant.parse("2026-11-02T03:00:00Z")
    private val day3 = Instant.parse("2026-11-03T03:00:00Z")
    private val month = Period.thisMonth(Instant.parse("2026-11-15T03:00:00Z"))

    private fun acc(id: Long, type: AccountType, opening: Long = 0) =
        Account(id, "A$id", type, null, opening, type.defaultLiquid, false, day1)

    private fun tx(
        id: Long, type: TxType, amount: Long, src: Long, dst: Long? = null,
        date: Instant = day2, cat: Long? = 1, gold: Long? = null,
    ) = Tx(id, type, amount, if (type == TxType.TRANSFER || type == TxType.ADJUSTMENT) null else cat,
        src, dst, gold, date, null, null, null)

    @Test fun expenseReducesBalance() {
        val gopay = acc(1, AccountType.EWALLET, 100_000)
        assertEquals(65_000L, Ledger.balance(gopay, listOf(tx(1, TxType.EXPENSE, 35_000, 1))))
    }

    @Test fun transferMovesMoneyNotAssets() {
        val bca = acc(1, AccountType.BANK, 5_000_000)
        val bibit = acc(2, AccountType.INVESTMENT)
        val txs = listOf(tx(1, TxType.TRANSFER, 500_000, 1, 2))
        assertEquals(4_500_000L, Ledger.balance(bca, txs))
        assertEquals(500_000L, Ledger.currentValue(bibit, txs, null))
        val s = Ledger.summary(listOf(bca, bibit), txs, emptyMap(), month)
        assertEquals(5_000_000L, s.totalAssets)
        assertEquals(0L, s.income)
        assertEquals(0L, s.expense)
    }

    @Test fun snapshotPlusLaterTransfers() {
        val bca = acc(1, AccountType.BANK, 5_000_000)
        val bibit = acc(2, AccountType.INVESTMENT)
        val snap = Snapshot(2, day1, 3_250_000, null, null)
        val txs = listOf(tx(1, TxType.TRANSFER, 500_000, 1, 2, date = day2))
        assertEquals(3_750_000L, Ledger.currentValue(bibit, txs, snap))
        val before = Ledger.summary(listOf(bca, bibit), emptyList(), mapOf(2L to snap), month).totalAssets
        val after = Ledger.summary(listOf(bca, bibit), txs, mapOf(2L to snap), month).totalAssets
        assertEquals(before, after)
    }

    @Test fun withdrawalAfterSnapshotReducesValue() {
        val bibit = acc(2, AccountType.INVESTMENT)
        val snap = Snapshot(2, day1, 3_250_000, null, null)
        val txs = listOf(tx(1, TxType.TRANSFER, 250_000, 2, 1, date = day2))
        assertEquals(3_000_000L, Ledger.currentValue(bibit, txs, snap))
    }

    @Test fun transferBeforeSnapshotIgnored() {
        val bibit = acc(2, AccountType.INVESTMENT)
        val snap = Snapshot(2, day2, 3_250_000, null, null)
        val txs = listOf(tx(1, TxType.TRANSFER, 500_000, 1, 2, date = day1))
        assertEquals(3_250_000L, Ledger.currentValue(bibit, txs, snap))
    }

    @Test fun goldValueFromGramsTimesPrice() {
        val gold = acc(3, AccountType.GOLD)
        val snap = Snapshot(3, day1, Grams.valueOf(105_000, 2_334_000), 105_000, 2_334_000)
        val txs = listOf(tx(1, TxType.TRANSFER, 1_003_620, 1, 3, date = day2, gold = 4_300))
        assertEquals(109_300L, Ledger.goldGrams(gold, txs, snap))
        assertEquals(Grams.valueOf(109_300, 2_334_000), Ledger.currentValue(gold, txs, snap))
    }

    @Test fun valuationChangeChangesAssetsNotCashFlow() {
        val bibit = acc(2, AccountType.INVESTMENT)
        val s1 = Ledger.summary(listOf(bibit), emptyList(), mapOf(2L to Snapshot(2, day1, 1_000_000, null, null)), month)
        val s2 = Ledger.summary(listOf(bibit), emptyList(), mapOf(2L to Snapshot(2, day2, 1_150_000, null, null)), month)
        assertEquals(150_000L, s2.totalAssets - s1.totalAssets)
        assertEquals(s1.income, s2.income)
    }

    @Test fun adjustmentNotIncome() {
        val cash = acc(1, AccountType.CASH, 100_000)
        val txs = listOf(tx(1, TxType.ADJUSTMENT, 20_000, 1))
        assertEquals(120_000L, Ledger.balance(cash, txs))
        assertEquals(0L, Ledger.summary(listOf(cash), txs, emptyMap(), month).income)
        assertEquals(80_000L, Ledger.balance(cash, listOf(tx(2, TxType.ADJUSTMENT, -20_000, 1))))
    }

    @Test fun liquidExcludesNonLiquid() {
        val accounts = listOf(
            acc(1, AccountType.BANK, 1_000_000),
            acc(2, AccountType.DEPOSIT, 25_000_000),
            acc(3, AccountType.GOLD, 2_000_000),
        )
        val s = Ledger.summary(accounts, emptyList(), emptyMap(), month)
        assertEquals(1_000_000L, s.liquid)
        assertEquals(2_000_000L, s.investmentValue)
        assertEquals(28_000_000L, s.totalAssets)
    }

    @Test fun netCashFlowIsIncomeMinusExpenseInPeriod() {
        val bank = acc(1, AccountType.BANK, 0)
        val txs = listOf(
            tx(1, TxType.INCOME, 8_500_000, 1, date = day2),
            tx(2, TxType.EXPENSE, 4_200_000, 1, date = day3),
            tx(3, TxType.EXPENSE, 999_000, 1, date = Instant.parse("2026-10-20T03:00:00Z")),
        )
        val s = Ledger.summary(listOf(bank), txs, emptyMap(), month)
        assertEquals(8_500_000L, s.income)
        assertEquals(4_200_000L, s.expense)
        assertEquals(4_300_000L, s.netCashFlow)
    }
}
