package id.dompetku.ui

import id.dompetku.data.AccountView
import id.dompetku.data.db.GoalEntity
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.JAKARTA
import id.dompetku.ui.goals.Goals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GoalsTest {
    private val today = LocalDate.of(2026, 11, 20)
    private fun day(d: LocalDate) = d.atStartOfDay(JAKARTA).toInstant().toEpochMilli()
    private val view = AccountView(Account(5, "Tabungan nikah", AccountType.SAVINGS, null, 0, true, false, Instant.EPOCH), 3_000_000, 3_000_000, null)

    @Test fun manualGoalPerMonthRoundsUp() {
        // Nov..Apr = 6 months for 10 jt left → 1 666 667 a month.
        val p = Goals.progress(GoalEntity(1, "Liburan", 10_000_000, day(LocalDate.of(2027, 4, 1)), null, 0, 0), emptyList(), today)
        assertEquals(6L, p.monthsLeft)
        assertEquals(1_666_667L, p.perMonth)
    }

    @Test fun linkedAccountIsProgress() {
        val p = Goals.progress(GoalEntity(1, "Nikah", 6_000_000, day(LocalDate.of(2026, 12, 31)), 5, 999, 0), listOf(view), today)
        assertEquals(3_000_000L, p.saved)
        assertEquals(50, p.percent)
        assertEquals(1_500_000L, p.perMonth)
    }

    @Test fun pastDeadlineAndDone() {
        val late = Goals.progress(GoalEntity(1, "x", 1_000, day(LocalDate.of(2026, 1, 1)), null, 400, 0), emptyList(), today)
        assertEquals(600L, late.perMonth) // overdue: everything left, now
        val done = Goals.progress(GoalEntity(1, "x", 1_000, day(LocalDate.of(2027, 1, 1)), null, 1_200, 0), emptyList(), today)
        assertTrue(done.done)
        assertEquals(100, done.percent)
    }
}
