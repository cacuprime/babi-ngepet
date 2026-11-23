package id.dompetku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PeriodTest {
    @Test fun periodBoundaryUsesJakarta() {
        val p = Period.thisMonth(Instant.parse("2026-11-15T03:00:00Z"))
        assertTrue(p.contains(Instant.parse("2026-11-30T16:30:00Z")))
        assertFalse(p.contains(Instant.parse("2026-11-30T17:00:00Z")))
        assertTrue(p.contains(Instant.parse("2026-10-31T17:00:00Z")))
    }

    @Test fun weekStartsMondayJakarta() {
        val p = Period.thisWeek(Instant.parse("2026-11-11T05:00:00Z"))
        assertEquals(Instant.parse("2026-11-08T17:00:00Z"), p.start)
        assertEquals(Instant.parse("2026-11-15T17:00:00Z"), p.endExclusive)
    }

    @Test fun weekSpanningMonthIsOnePeriod() {
        val p = Period.week(0, Instant.parse("2026-10-29T05:00:00Z"))
        // 29 Oct 2026 is a Thursday: the week is Mon 26 Oct – Sun 1 Nov (WIB).
        assertEquals(Instant.parse("2026-10-25T17:00:00Z"), p.start)
        assertEquals(Instant.parse("2026-11-01T17:00:00Z"), p.endExclusive)
        assertEquals(Period.week(-1, Instant.parse("2026-11-05T05:00:00Z")), p)
    }

    @Test fun monthOffsetMinusOne() {
        val p = Period.month(-1, Instant.parse("2026-01-10T05:00:00Z"))
        assertEquals(Instant.parse("2025-11-30T17:00:00Z"), p.start)
        assertEquals(Instant.parse("2025-12-31T17:00:00Z"), p.endExclusive)
    }

    @Test fun labelsInIndonesian() {
        assertEquals("9–15 Nov 2026", Period.week(0, Instant.parse("2026-11-11T05:00:00Z")).label(Span.WEEK))
        assertEquals("26 Okt–1 Nov 2026", Period.week(0, Instant.parse("2026-10-29T05:00:00Z")).label(Span.WEEK))
        assertEquals("November 2026", Period.month(0, Instant.parse("2026-11-11T05:00:00Z")).label(Span.MONTH))
    }

    @Test fun lastMonthAndCustom() {
        val lm = Period.lastMonth(Instant.parse("2026-11-15T03:00:00Z"))
        assertEquals(Instant.parse("2026-09-30T17:00:00Z"), lm.start)
        assertEquals(Instant.parse("2026-10-31T17:00:00Z"), lm.endExclusive)
        val c = Period.custom(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1))
        assertEquals(Instant.parse("2026-11-01T17:00:00Z"), c.endExclusive)
    }
}
