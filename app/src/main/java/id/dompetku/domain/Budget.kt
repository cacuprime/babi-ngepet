package id.dompetku.domain

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Bucket(val label: String) { NEEDS("Kebutuhan"), WANTS("Keinginan"), SAVINGS("Tabungan") }

enum class BudgetMode { PERCENT, FIXED }

/** [startDay] 1–28 = monthly from that day (payday); null = custom range. Limits are Rp. */
data class Budget(
    val id: Long,
    val period: Period,
    val startDay: Int?,
    val mode: BudgetMode,
    val income: Long,
    val percents: Map<Bucket, Int>,
    val limits: Map<Bucket, Long>,
)

data class BucketUse(val bucket: Bucket, val limit: Long, val spent: Long) {
    val percent: Int get() = if (limit <= 0) (if (spent > 0) 100 else 0) else (spent * 100 / limit).toInt()
    val over: Boolean get() = spent > limit
}

/** "Sekilas hari ini": what can still be spent per day on needs + wants until the period ends. */
data class Daily(val allowance: Long, val spentToday: Long, val daysLeft: Int) {
    val leftToday: Long get() = allowance - spentToday
}

object Budgets {
    val DEFAULT_PERCENTS = mapOf(Bucket.NEEDS to 50, Bucket.WANTS to 30, Bucket.SAVINGS to 20)
    private val SAVINGS_TARGETS = setOf(AccountType.SAVINGS, AccountType.DEPOSIT, AccountType.INVESTMENT, AccountType.GOLD)
    private val SPENDING_ACCOUNTS = setOf(AccountType.CASH, AccountType.BANK, AccountType.EWALLET)

    private fun day(now: Instant): LocalDate = now.atZone(JAKARTA).toLocalDate()

    /** The monthly period starting on [startDay] that contains [now] (e.g. 25 Okt – 24 Nov). */
    fun monthlyPeriod(startDay: Int, now: Instant): Period {
        val d = day(now)
        val sd = startDay.coerceIn(1, 28)
        val start = if (d.dayOfMonth >= sd) d.withDayOfMonth(sd) else d.minusMonths(1).withDayOfMonth(sd)
        return Period.custom(start, start.plusMonths(1).minusDays(1))
    }

    /** The period right after this budget's: next month for monthly, same length for custom. */
    fun nextPeriod(b: Budget): Period {
        val from = b.period.start.atZone(JAKARTA).toLocalDate()
        val to = b.period.endExclusive.atZone(JAKARTA).toLocalDate()
        if (b.startDay != null) return Period.custom(from.plusMonths(1), from.plusMonths(2).minusDays(1))
        val len = ChronoUnit.DAYS.between(from, to)
        return Period.custom(to, to.plusDays(len - 1))
    }

    /** Rp limits from percents of income (percent mode); remainders are dropped, never rounded up. */
    fun limitsFrom(income: Long, percents: Map<Bucket, Int>): Map<Bucket, Long> =
        Bucket.entries.associateWith { income * (percents[it] ?: 0) / 100 }

    /** Category id → bucket. A subcategory takes its parent's bucket; unknown or null = WANTS. */
    fun bucketMap(categories: List<CategoryInfo>): Map<Long, Bucket> {
        val byId = categories.associateBy { it.id }
        return categories.associate { c ->
            val owner = c.parentId?.let { byId[it] } ?: c
            c.id to (owner.bucket?.let { runCatching { Bucket.valueOf(it) }.getOrNull() } ?: Bucket.WANTS)
        }
    }

    private fun spent(txs: List<Tx>, period: Period, buckets: Map<Long, Bucket>, accounts: Map<Long, AccountType>): Map<Bucket, Long> {
        val out = Bucket.entries.associateWith { 0L }.toMutableMap()
        for (t in txs) {
            if (!period.contains(t.date)) continue
            when {
                t.type == TxType.EXPENSE -> {
                    val b = t.categoryId?.let { buckets[it] } ?: Bucket.WANTS
                    out[b] = out.getValue(b) + t.amount
                }
                // Moving money from everyday accounts into savings/investments counts as saving.
                t.type == TxType.TRANSFER && accounts[t.sourceAccountId] in SPENDING_ACCOUNTS &&
                    accounts[t.destinationAccountId] in SAVINGS_TARGETS ->
                    out[Bucket.SAVINGS] = out.getValue(Bucket.SAVINGS) + t.amount
            }
        }
        return out
    }

    fun usage(b: Budget, txs: List<Tx>, buckets: Map<Long, Bucket>, accounts: Map<Long, AccountType>): List<BucketUse> {
        val s = spent(txs, b.period, buckets, accounts)
        return Bucket.entries.map { BucketUse(it, b.limits[it] ?: 0, s.getValue(it)) }
    }

    fun daily(b: Budget, txs: List<Tx>, buckets: Map<Long, Bucket>, accounts: Map<Long, AccountType>, now: Instant): Daily? {
        if (!b.period.contains(now)) return null
        val today = Period.today(now)
        val before = Period(b.period.start, today.start)
        fun spend(p: Period) = spent(txs, p, buckets, accounts).let { it.getValue(Bucket.NEEDS) + it.getValue(Bucket.WANTS) }
        val limit = (b.limits[Bucket.NEEDS] ?: 0) + (b.limits[Bucket.WANTS] ?: 0)
        val daysLeft = ChronoUnit.DAYS.between(day(now), b.period.endExclusive.atZone(JAKARTA).toLocalDate()).toInt().coerceAtLeast(1)
        val allowance = (limit - spend(before)).coerceAtLeast(0) / daysLeft
        return Daily(allowance, spend(today), daysLeft)
    }

    /** Bucket that just went over its limit because of [tx] (for the "over budget" warning), if any. */
    fun newlyOver(b: Budget, txsBefore: List<Tx>, tx: Tx, buckets: Map<Long, Bucket>, accounts: Map<Long, AccountType>): BucketUse? {
        val before = usage(b, txsBefore, buckets, accounts)
        val after = usage(b, txsBefore + tx, buckets, accounts)
        return after.firstOrNull { a -> a.bucket != Bucket.SAVINGS && a.over && before.first { it.bucket == a.bucket }.spent <= a.limit }
    }
}

/** The category fields budget logic needs (keeps domain free of Room entities). */
data class CategoryInfo(val id: Long, val parentId: Long?, val bucket: String?)
