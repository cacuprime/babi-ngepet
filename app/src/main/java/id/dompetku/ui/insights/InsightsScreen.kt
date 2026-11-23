package id.dompetku.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import id.dompetku.AppContainer
import id.dompetku.core.Rupiah
import id.dompetku.core.t
import kotlinx.coroutines.launch
import id.dompetku.domain.Reports
import id.dompetku.ui.appViewModel
import id.dompetku.ui.components.AmountText
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.EmptyState
import id.dompetku.ui.components.HSpace
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.formatMonth
import id.dompetku.ui.home.PeriodChoice
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

class InsightsViewModel(c: AppContainer) : ViewModel() {
    // Reports roll subcategories into their parent category.
    val data = combine(c.transactions.observeAll(), c.categories.observeAll()) { t, cats ->
        val parent = cats.associate { it.id to (it.parentId ?: it.id) }
        t.map { tx -> tx.categoryId?.let { tx.copy(categoryId = parent[it] ?: it) } ?: tx } to cats.associateBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Raw (not rolled-up) data for the newer report cards. */
    val extra = combine(
        c.transactions.observeAll(), c.categories.observeAll(), c.dashboard.accountViews(),
        id.dompetku.ui.budget.budgetState(c), combine(c.settings.emergencyAccounts, c.settings.roast, c.settings.hideBalances) { e, r, h -> Triple(e, r, h) },
    ) { txs, cats, views, budget, (emergencyIds, roast, hidden) ->
        val now = Instant.now()
        val fundViews = views.filter { v ->
            if (emergencyIds.isEmpty()) v.account.type in setOf(id.dompetku.domain.AccountType.SAVINGS, id.dompetku.domain.AccountType.DEPOSIT) && !v.account.isArchived
            else v.account.id in emergencyIds
        }
        val emergency = id.dompetku.domain.Analysis.emergency(txs, fundViews.sumOf { it.currentValue }, now)
        val usage = budget.current?.usage
        InsightsExtra(
            txs, cats.associateBy { it.id }, views.associate { it.account.id to it.account }, views, budget, emergency,
            id.dompetku.domain.Analysis.health(txs, emergency, usage, now),
            id.dompetku.domain.Analysis.notes(txs, cats.associate { it.id to it.name }, usage.orEmpty(), now, roast),
            roast, hidden, emergencyIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class InsightsExtra(
    val txs: List<id.dompetku.domain.Tx>,
    val categories: Map<Long, id.dompetku.data.db.CategoryEntity>,
    val accounts: Map<Long, id.dompetku.domain.Account>,
    val views: List<id.dompetku.data.AccountView>,
    val budget: id.dompetku.ui.budget.BudgetState,
    val emergency: id.dompetku.domain.Emergency,
    val health: id.dompetku.domain.Health,
    val notes: List<String>,
    val roast: Boolean,
    val hidden: Boolean,
    val emergencyIds: Set<Long>,
)

@Composable
fun InsightsScreen(nav: androidx.navigation.NavController, onOpenCategory: (Long, id.dompetku.domain.Period) -> Unit) {
    val c = LocalDompetkuColors.current
    val vm = appViewModel { InsightsViewModel(it) }
    val data by vm.data.collectAsStateWithLifecycle()
    val extra by vm.extra.collectAsStateWithLifecycle()
    val container = id.dompetku.ui.LocalContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var choice by remember { mutableStateOf(PeriodChoice.THIS_MONTH) }
    var custom by remember { mutableStateOf<id.dompetku.domain.Period?>(null) }
    var chooseFund by remember { mutableStateOf(false) }
    val now = Instant.now()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("Insights"), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmall, color = c.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton({ nav.navigate(id.dompetku.ui.budget.BudgetRoutes.BUDGET) }) { Text(t("Budget")) }
            androidx.compose.material3.OutlinedButton({ nav.navigate(id.dompetku.ui.goals.GOALS_ROUTE) }) { Text(t("Tujuan menabung")) }
        }
        val (txs, cats) = data ?: return@Column
        extra?.let { x ->
            HealthCard(x.health)
            NotesCard(x.notes, x.roast) { on -> scope.launch { container.settings.setRoast(on) } }
            x.budget.current?.let { cur ->
                SectionCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(id.dompetku.ui.budget.BudgetRoutes.BUDGET) }) {
                    Text(t("Realisasi anggaran"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(id.dompetku.ui.budget.periodText(cur.budget), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    cur.usage.forEach { id.dompetku.ui.budget.BucketBar(it, x.hidden) }
                }
            }
        }
        SpendingSection(txs, cats, onOpenCategory)
        Text(t("Komposisi"), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, color = c.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(PeriodChoice.THIS_MONTH, PeriodChoice.LAST_MONTH, PeriodChoice.THIS_WEEK).forEach { p ->
                Chip(t(p.label), custom == null && choice == p, { choice = p; custom = null })
            }
            Chip(custom?.let { id.dompetku.ui.formatShort(it.start) + "–" + id.dompetku.ui.formatShort(it.endExclusive.minusSeconds(1)) } ?: t("Pilih tanggal"), custom != null, {
                val today = java.time.LocalDate.now(id.dompetku.domain.JAKARTA)
                android.app.DatePickerDialog(context, { _, y, m, d ->
                    val from = java.time.LocalDate.of(y, m + 1, d)
                    android.app.DatePickerDialog(context, { _, y2, m2, d2 ->
                        val to = java.time.LocalDate.of(y2, m2 + 1, d2)
                        custom = if (to.isBefore(from)) id.dompetku.domain.Period.custom(to, from) else id.dompetku.domain.Period.custom(from, to)
                    }, today.year, today.monthValue - 1, today.dayOfMonth).show()
                }, today.year, today.monthValue - 1, 1).show()
            })
        }
        val byCat = Reports.expenseByCategory(txs, custom ?: choice.period(now))
        val total = byCat.sumOf { it.second }

        SectionCard(Modifier.fillMaxWidth()) {
            Text(t("Pengeluaran per kategori"), style = MaterialTheme.typography.titleMedium, color = c.text)
            VSpace(12)
            if (total == 0L) {
                EmptyState(t("Belum ada data untuk periode ini."))
            } else {
                val top = byCat.take(5).toMutableList()
                val rest = byCat.drop(5).sumOf { it.second }
                val palette = listOf(c.accent, c.text, Color(0xFF7FB3FF), Color(0xFFFFB86B), Color(0xFFB79CFF), c.muted)
                val slices = top.mapIndexed { i, (id, v) -> Triple(cats[id]?.name ?: "?", v, palette[i]) } +
                    (if (rest > 0) listOf(Triple(t("Lainnya"), rest, palette[5])) else emptyList())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                        Donut(slices.map { it.second to it.third }, c.barTrack)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t("Total"), style = MaterialTheme.typography.labelSmall, color = c.muted)
                            Text(Rupiah.compact(total), style = AmountStyle.copy(fontSize = 18.sp), color = c.text)
                        }
                    }
                    HSpace(16)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        slices.forEach { (name, v, color) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                                HSpace(8)
                                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.text, maxLines = 1)
                                Text("${v * 100 / total}%", style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                        }
                    }
                }
                VSpace(12)
                Text(t("Terbesar: %s %s", slices.first().first, Rupiah.full(slices.first().second)),
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }

        val months = Reports.incomeVsExpenseByMonth(txs, 6, now)
        SectionCard(Modifier.fillMaxWidth()) {
            Text(t("Pemasukan vs pengeluaran"), style = MaterialTheme.typography.titleMedium, color = c.text)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.income)); HSpace(4)
                Text(t("Masuk"), style = MaterialTheme.typography.labelSmall, color = c.muted); HSpace(12)
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.expense)); HSpace(4)
                Text(t("Keluar"), style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
            VSpace(12)
            val max = months.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                months.forEach { m ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.height(130.dp).fillMaxWidth().padding(horizontal = 6.dp)) {
                            val w = size.width / 2 - 2
                            fun bar(x: Float, v: Long, color: Color) {
                                val h = size.height * v / max
                                drawRoundRect(color, Offset(x, size.height - h), Size(w, h), CornerRadius(6f, 6f))
                            }
                            bar(0f, m.income, c.income)
                            bar(w + 4, m.expense, c.expense)
                        }
                        VSpace(6)
                        Text(formatMonth(m.month), style = MaterialTheme.typography.labelSmall, color = c.muted, textAlign = TextAlign.Center)
                    }
                }
            }
            VSpace(8)
            val cur = months.last()
            Row {
                Text(t("Bulan ini tersisa"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text)
                AmountText(cur.income - cur.expense, signed = true, style = AmountStyle.copy(fontSize = 15.sp),
                    color = if (cur.income >= cur.expense) c.income else c.expense)
            }
            if (cur.income > 0) {
                Text(t("Rasio menabung %d%%", (cur.income - cur.expense) * 100 / cur.income),
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        extra?.let { x ->
            ActivityCalendar(x.txs, x.hidden)
            EmergencyCard(x.emergency, x.hidden) { chooseFund = true }
            ExportCard(x.txs, x.accounts, x.categories)
            if (chooseFund) FundDialog(x.views, x.emergencyIds, onSave = { ids -> scope.launch { container.settings.setEmergencyAccounts(ids) }; chooseFund = false }, onClose = { chooseFund = false })
        }
        VSpace(24)
    }
}

@Composable
private fun Donut(slices: List<Pair<Long, Color>>, track: Color) {
    Canvas(Modifier.size(140.dp)) {
        val stroke = 22.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val total = slices.sumOf { it.first }.toFloat()
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        var start = -90f
        slices.forEach { (v, color) ->
            val sweep = 360f * v / total
            drawArc(color, start, (sweep - 1.5f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            start += sweep
        }
    }
}
