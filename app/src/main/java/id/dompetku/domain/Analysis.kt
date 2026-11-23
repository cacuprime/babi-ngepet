package id.dompetku.domain

import id.dompetku.core.Rupiah
import id.dompetku.core.t
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth

data class Emergency(val fund: Long, val avgMonthlyExpense: Long) {
    /** Months the fund would cover; 0 when there's no spending history yet. */
    val months: Float get() = if (avgMonthlyExpense <= 0) 0f else fund.toFloat() / avgMonthlyExpense
    val target: Long get() = avgMonthlyExpense * TARGET_MONTHS
    companion object { const val TARGET_MONTHS = 6 }
}

/** Score 0–100: savings rate (30), emergency fund (30), budget kept (20), months with income ≥ expense (20). */
data class Health(
    val score: Int,
    val savingsRate: Int?,
    val emergencyMonths: Float,
    /** null = no budget for the current period. */
    val budgetOver: Int?,
    val positiveMonths: Int,
    val monthsCounted: Int,
)

object Analysis {
    /** The last 3 full months that have any income or expense (falls back to this month for new users). */
    private fun recentMonths(txs: List<Tx>, now: Instant): List<MonthTotals> {
        val full = Reports.incomeVsExpenseByMonth(txs, 4, now).dropLast(1).filter { it.income > 0 || it.expense > 0 }
        return full.ifEmpty { Reports.incomeVsExpenseByMonth(txs, 1, now).filter { it.income > 0 || it.expense > 0 } }
    }

    fun emergency(txs: List<Tx>, fund: Long, now: Instant): Emergency {
        val m = recentMonths(txs, now)
        return Emergency(fund, if (m.isEmpty()) 0 else m.sumOf { it.expense } / m.size)
    }

    fun health(txs: List<Tx>, emergency: Emergency, budget: List<BucketUse>?, now: Instant): Health {
        val m = recentMonths(txs, now)
        val income = m.sumOf { it.income }
        val rate = if (income > 0) ((income - m.sumOf { it.expense }) * 100 / income).toInt() else null
        val savePts = ((rate ?: 0).coerceIn(0, 20) * 30) / 20
        val emPts = (minOf(emergency.months / Emergency.TARGET_MONTHS, 1f) * 30).toInt()
        val over = budget?.count { it.bucket != Bucket.SAVINGS && it.over }
        val budgetPts = when (over) { null -> 10; 0 -> 20; 1 -> 10; else -> 0 }
        val positive = m.count { it.income >= it.expense && it.income > 0 }
        val flowPts = if (m.isEmpty()) 0 else positive * 20 / m.size
        return Health(savePts + emPts + budgetPts + flowPts, rate, emergency.months, over, positive, m.size)
    }

    /** Expense per day of [month] (day of month → Rp). */
    fun dailySpend(txs: List<Tx>, month: YearMonth): Map<Int, Long> =
        txs.filter { it.type == TxType.EXPENSE && YearMonth.from(it.date.atZone(JAKARTA)) == month }
            .groupBy { it.date.atZone(JAKARTA).dayOfMonth }
            .mapValues { (_, l) -> l.sumOf { it.amount } }

    /**
     * Rule-based observations about this month (the offline stand-in for an "AI advisor").
     * [roast] picks a teasing tone. [names] maps category id → name; [over] = buckets over budget.
     */
    fun notes(txs: List<Tx>, names: Map<Long, String>, over: List<BucketUse>, now: Instant, roast: Boolean): List<String> {
        val out = ArrayList<String>()
        val today = now.atZone(JAKARTA).toLocalDate()
        val thisMonth = Period.thisMonth(now)
        val exp = txs.filter { it.type == TxType.EXPENSE }
        val cur = exp.filter { thisMonth.contains(it.date) }
        // Same number of days last month, so the comparison is fair mid-month.
        val lastStart = today.withDayOfMonth(1).minusMonths(1)
        val lastSameDays = Period.custom(lastStart, lastStart.plusDays((today.dayOfMonth - 1).toLong()).coerceAtMost(lastStart.withDayOfMonth(lastStart.lengthOfMonth())))
        val prev = exp.filter { lastSameDays.contains(it.date) }

        fun byCat(l: List<Tx>) = l.filter { it.categoryId != null }.groupBy { it.categoryId!! }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val curCat = byCat(cur); val prevCat = byCat(prev)
        curCat.mapNotNull { (id, v) ->
            val p = prevCat[id] ?: return@mapNotNull null
            if (p <= 0 || v - p < 100_000 || (v - p) * 100 / p < 20) null else Triple(id, ((v - p) * 100 / p).toInt(), v - p)
        }.maxByOrNull { it.third }?.let { (id, pct, _) ->
            val n = names[id] ?: "?"
            out += if (roast) t("%s naik %d%% dari bulan lalu. Dompetmu lagi diet, kamu malah pesta.", n, pct)
            else t("Pengeluaran %s naik %d%% dibanding periode yang sama bulan lalu.", n, pct)
        }

        val total = cur.sumOf { it.amount }
        if (total > 0 && cur.size >= 5) {
            val weekend = cur.filter { it.date.atZone(JAKARTA).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.sumOf { it.amount }
            val share = (weekend * 100 / total).toInt()
            if (share >= 40) out += if (roast) t("Senin–Jumat hemat, Sabtu–Minggu khilaf: %d%% habis di akhir pekan.", share)
            else t("%d%% pengeluaranmu bulan ini terjadi di akhir pekan.", share)
        }

        cur.filter { !it.merchant.isNullOrBlank() }.groupBy { it.merchant!!.trim().lowercase() }
            .filter { it.value.size >= 3 }.maxByOrNull { it.value.size }?.let { (_, l) ->
                val m = l.first().merchant!!.trim()
                out += if (roast) t("%s sudah %d kali bulan ini (%s). Mungkin mereka perlu kasih kamu saham.", m, l.size, Rupiah.full(l.sumOf { it.amount }))
                else t("Paling sering belanja di %s: %d kali, total %s.", m, l.size, Rupiah.full(l.sumOf { it.amount }))
            }

        val lastMonthTotal = exp.filter { Period.lastMonth(now).contains(it.date) }.sumOf { it.amount }
        if (today.dayOfMonth >= 7 && lastMonthTotal > 0) {
            val projected = total * today.lengthOfMonth() / today.dayOfMonth
            if (projected > lastMonthTotal * 11 / 10) out += if (roast)
                t("Kalau begini terus, akhir bulan habis %s. Bulan lalu cuma %s. Gaya, ya.", Rupiah.full(projected), Rupiah.full(lastMonthTotal))
            else t("Dengan kecepatan ini, pengeluaran bulan ini bisa sampai %s (bulan lalu %s).", Rupiah.full(projected), Rupiah.full(lastMonthTotal))
        }

        over.filter { it.over }.forEach {
            out += if (roast) t("Pos %s sudah %d%%. Budget-nya cuma hiasan?", t(it.bucket.label), it.percent)
            else t("Pos %s sudah %d%% dari budget.", t(it.bucket.label), it.percent)
        }

        if (out.isEmpty()) out += if (roast) t("Tidak ada yang bisa di-roasting. Yakin sudah mencatat semuanya?")
        else t("Pengeluaranmu bulan ini terlihat wajar. Pertahankan!")
        return out
    }
}
