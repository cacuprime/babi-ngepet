package id.dompetku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class AnalysisTest {
    private fun tx(type: TxType, amount: Long, iso: String, cat: Long? = 1, merchant: String? = null) =
        Tx(0, type, amount, cat, 1, null, null, Instant.parse(iso), null, merchant, null)

    private val now = Instant.parse("2026-11-20T05:00:00Z")
    private val history = listOf("08", "09", "10").flatMap { m ->
        listOf(tx(TxType.INCOME, 10_000_000, "2026-$m-01T03:00:00Z"), tx(TxType.EXPENSE, 8_000_000, "2026-$m-10T03:00:00Z"))
    }

    @Test fun emergencyMonthsFromLastThreeFullMonths() {
        val e = Analysis.emergency(history, 24_000_000, now)
        assertEquals(8_000_000L, e.avgMonthlyExpense)
        assertEquals(3f, e.months, 0.001f)
        assertEquals(48_000_000L, e.target)
    }

    @Test fun healthScoreAddsUp() {
        val e = Analysis.emergency(history, 48_000_000, now) // 6 months → 30
        val h = Analysis.health(history, e, emptyList(), now) // 20% saved → 30, budget kept → 20, 3 of 3 positive → 20
        assertEquals(20, h.savingsRate)
        assertEquals(100, h.score)
        val bad = Analysis.health(history, Emergency(0, 8_000_000), listOf(
            BucketUse(Bucket.NEEDS, 1, 2), BucketUse(Bucket.WANTS, 1, 2), BucketUse(Bucket.SAVINGS, 1, 0)), now)
        assertEquals(30 + 0 + 0 + 20, bad.score)
    }

    @Test fun newUserGetsScoreWithoutCrash() {
        val h = Analysis.health(emptyList(), Analysis.emergency(emptyList(), 0, now), null, now)
        assertEquals(10, h.score) // only the neutral "no budget" points
    }

    @Test fun dailySpendByDay() {
        val txs = listOf(tx(TxType.EXPENSE, 5_000, "2026-11-02T03:00:00Z"), tx(TxType.EXPENSE, 7_000, "2026-11-02T09:00:00Z"),
            tx(TxType.INCOME, 1, "2026-11-02T09:00:00Z"))
        assertEquals(mapOf(2 to 12_000L), Analysis.dailySpend(txs, YearMonth.of(2026, 11)))
    }

    @Test fun notesSpotCategoryJumpAndMerchant() {
        val txs = listOf(
            tx(TxType.EXPENSE, 200_000, "2026-10-05T03:00:00Z"),
            tx(TxType.EXPENSE, 600_000, "2026-11-05T03:00:00Z", merchant = "Kopi Kenangan"),
            tx(TxType.EXPENSE, 10_000, "2026-11-06T03:00:00Z", cat = 2, merchant = "kopi kenangan"),
            tx(TxType.EXPENSE, 10_000, "2026-11-07T03:00:00Z", cat = 2, merchant = "Kopi Kenangan"),
        )
        val notes = Analysis.notes(txs, mapOf(1L to "Makan", 2L to "Jajan"), emptyList(), now, roast = false)
        assertTrue(notes.toString(), notes.any { "Makan naik 200%" in it })
        assertTrue(notes.toString(), notes.any { "Kopi Kenangan: 3 kali" in it })
    }

    @Test fun notesNeverEmpty() {
        assertEquals(1, Analysis.notes(emptyList(), emptyMap(), emptyList(), now, roast = true).size)
    }
}
