package id.dompetku.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import id.dompetku.AppContainer
import id.dompetku.core.Rupiah
import id.dompetku.core.t
import id.dompetku.domain.Bucket
import id.dompetku.domain.BucketUse
import id.dompetku.domain.Budget
import id.dompetku.domain.Budgets
import id.dompetku.domain.CategoryInfo
import id.dompetku.domain.Daily
import id.dompetku.domain.JAKARTA
import id.dompetku.ui.formatShort
import id.dompetku.ui.theme.LocalDompetkuColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.temporal.ChronoUnit

data class BudgetView(val budget: Budget, val usage: List<BucketUse>, val daily: Daily?)

/** [current] = budget covering today; [latest] = newest budget overall (offered for copying). */
data class BudgetState(val current: BudgetView?, val latest: Budget?, val all: List<Budget>, val loaded: Boolean = true)

fun budgetState(c: AppContainer, now: () -> Instant = Instant::now): Flow<BudgetState> = combine(
    c.budgets.observeAll(), c.transactions.observeAll(), c.categories.observeAll(), c.accounts.observeAll(),
) { all, txs, cats, accs ->
    val buckets = Budgets.bucketMap(cats.map { CategoryInfo(it.id, it.parentId, it.bucket) })
    val types = accs.associate { it.id to it.type }
    val cur = c.budgets.current(all, now())?.let { b ->
        BudgetView(b, Budgets.usage(b, txs, buckets, types), Budgets.daily(b, txs, buckets, types, now()))
    }
    BudgetState(cur, all.firstOrNull(), all)
}

/** Usage of the current budget for other screens (health score, notes); null without a budget. */
fun usageFor(state: BudgetState?): List<BucketUse>? = state?.current?.usage

fun periodText(b: Budget): String =
    formatShort(b.period.start) + " – " + formatShort(b.period.endExclusive.minus(1, ChronoUnit.DAYS))

fun dayOf(b: Budget, now: Instant): Pair<Int, Int> {
    val from = b.period.start.atZone(JAKARTA).toLocalDate()
    val to = b.period.endExclusive.atZone(JAKARTA).toLocalDate()
    val total = ChronoUnit.DAYS.between(from, to).toInt()
    val day = (ChronoUnit.DAYS.between(from, now.atZone(JAKARTA).toLocalDate()) + 1).toInt().coerceIn(1, total)
    return day to total
}

@Composable
fun BucketBar(u: BucketUse, hidden: Boolean = false) {
    val c = LocalDompetkuColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row {
            Text(t(u.bucket.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text)
            Text(if (hidden) Rupiah.MASK else Rupiah.full(u.spent) + " / " + Rupiah.full(u.limit),
                style = MaterialTheme.typography.bodyMedium, color = if (u.over) c.expense else c.muted)
        }
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.barTrack)) {
            val color = when {
                u.over && u.bucket != Bucket.SAVINGS -> c.expense
                u.bucket == Bucket.SAVINGS -> c.income
                else -> c.accent
            }
            Box(Modifier.fillMaxWidth((u.percent.coerceIn(0, 100)) / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
        }
        if (u.over && u.bucket != Bucket.SAVINGS) {
            Text(t("Over budget %d%%", u.percent), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = c.expense)
        } else if (u.bucket == Bucket.SAVINGS && u.limit > 0) {
            Text(t("%d%% dari target tabungan", u.percent), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
    }
}
