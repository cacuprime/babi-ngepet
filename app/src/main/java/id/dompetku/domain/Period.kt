package id.dompetku.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

val JAKARTA: ZoneId = ZoneId.of("Asia/Jakarta")

data class Period(val start: Instant, val endExclusive: Instant) {
    fun contains(t: Instant): Boolean = !t.isBefore(start) && t.isBefore(endExclusive)

    companion object {
        private fun of(from: LocalDate, toExclusive: LocalDate) =
            Period(from.atStartOfDay(JAKARTA).toInstant(), toExclusive.atStartOfDay(JAKARTA).toInstant())

        private fun date(now: Instant) = now.atZone(JAKARTA).toLocalDate()

        fun today(now: Instant): Period = date(now).let { of(it, it.plusDays(1)) }

        fun thisWeek(now: Instant): Period {
            val monday = date(now).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return of(monday, monday.plusWeeks(1))
        }

        fun thisMonth(now: Instant): Period {
            val first = date(now).withDayOfMonth(1)
            return of(first, first.plusMonths(1))
        }

        fun lastMonth(now: Instant): Period {
            val first = date(now).withDayOfMonth(1).minusMonths(1)
            return of(first, first.plusMonths(1))
        }

        /** Inclusive of both dates. */
        fun custom(from: LocalDate, to: LocalDate): Period = of(from, to.plusDays(1))

        /** Monday-to-Sunday week; offset 0 = this week, -1 = last week. */
        fun week(offset: Int, now: Instant): Period {
            val monday = date(now).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
            return of(monday, monday.plusWeeks(1))
        }

        /** Calendar month; offset 0 = this month, -1 = last month. */
        fun month(offset: Int, now: Instant): Period {
            val first = date(now).withDayOfMonth(1).plusMonths(offset.toLong())
            return of(first, first.plusMonths(1))
        }
    }

    /** "9–15 Nov 2026", "27 Okt–2 Nov 2026", or "November 2026". */
    fun label(span: Span): String {
        val from = start.atZone(JAKARTA).toLocalDate()
        val to = endExclusive.atZone(JAKARTA).toLocalDate().minusDays(1)
        if (span == Span.MONTH) return MONTH_FMT.format(from).replaceFirstChar { it.uppercase() }
        return when {
            from.year != to.year -> "${DAY_MON_YEAR.format(from)}–${DAY_MON_YEAR.format(to)}"
            from.month != to.month -> "${DAY_MON.format(from)}–${DAY_MON_YEAR.format(to)}"
            else -> "${from.dayOfMonth}–${DAY_MON_YEAR.format(to)}"
        }
    }
}

enum class Span { WEEK, MONTH }

private val MONTH_FMT get() = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", id.dompetku.core.I18n.locale)
private val DAY_MON get() = java.time.format.DateTimeFormatter.ofPattern("d MMM", id.dompetku.core.I18n.locale)
private val DAY_MON_YEAR get() = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", id.dompetku.core.I18n.locale)
