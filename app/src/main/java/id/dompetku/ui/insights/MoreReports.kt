package id.dompetku.ui.insights

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dompetku.core.Export
import id.dompetku.core.I18n
import id.dompetku.core.Rupiah
import id.dompetku.core.t
import id.dompetku.data.db.CategoryEntity
import id.dompetku.domain.Account
import id.dompetku.domain.Analysis
import id.dompetku.domain.Emergency
import id.dompetku.domain.Health
import id.dompetku.domain.JAKARTA
import id.dompetku.domain.Period
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import id.dompetku.ui.tx.Tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun HealthCard(h: Health) {
    val c = LocalDompetkuColors.current
    SectionCard(Modifier.fillMaxWidth()) {
        Text(t("Skor kesehatan finansial"), style = MaterialTheme.typography.titleMedium, color = c.text)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${h.score}", style = AmountStyle.copy(fontSize = 40.sp),
                color = when { h.score >= 70 -> c.income; h.score >= 40 -> c.text; else -> c.expense })
            Text(" / 100  ·  " + when { h.score >= 70 -> t("Sehat"); h.score >= 40 -> t("Cukup"); else -> t("Perlu perhatian") },
                Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        val lines = buildList {
            add(h.savingsRate?.let { t("Rasio menabung %d%% (target 20%%)", it) } ?: t("Belum ada pemasukan tercatat"))
            add(t("Dana darurat %s bulan (target 6)", String.format(I18n.locale, "%.1f", h.emergencyMonths)))
            add(when (h.budgetOver) { null -> t("Belum ada budget aktif"); 0 -> t("Budget terjaga"); else -> t("%d pos budget terlewati", h.budgetOver) })
            if (h.monthsCounted > 0) add(t("%d dari %d bulan terakhir pemasukan ≥ pengeluaran", h.positiveMonths, h.monthsCounted))
        }
        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.muted) }
    }
}

@Composable
fun EmergencyCard(e: Emergency, hidden: Boolean, onChooseAccounts: () -> Unit) {
    val c = LocalDompetkuColors.current
    SectionCard(Modifier.fillMaxWidth(), onClick = onChooseAccounts) {
        Text(t("Dana darurat"), style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(if (hidden) Rupiah.MASK else Rupiah.full(e.fund), style = AmountStyle.copy(fontSize = 22.sp), color = c.text)
        if (e.avgMonthlyExpense > 0) {
            Text(t("Cukup untuk %s bulan pengeluaran. Target 6 bulan = %s.",
                String.format(I18n.locale, "%.1f", e.months), if (hidden) Rupiah.MASK else Rupiah.full(e.target)),
                style = MaterialTheme.typography.bodySmall, color = if (e.months >= 6) c.income else c.muted)
        } else {
            Text(t("Catat pengeluaran sebulan dulu untuk menghitung kebutuhan dana darurat."), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Text(t("Ketuk untuk memilih akun dana darurat"), style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
}

/** Month grid; the darker the cell, the more was spent that day. */
@Composable
fun ActivityCalendar(txs: List<Tx>, hidden: Boolean) {
    val c = LocalDompetkuColors.current
    var month by remember { mutableStateOf(YearMonth.now(JAKARTA)) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val spend = Analysis.dailySpend(txs, month)
    val max = spend.values.maxOrNull() ?: 0L
    SectionCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Kalender aktivitas"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text)
            Text("‹", Modifier.clickable { month = month.minusMonths(1); picked = null }.padding(horizontal = 12.dp), color = c.text)
            Text(DateTimeFormatter.ofPattern("MMM yyyy", I18n.locale).format(month.atDay(1)), color = c.muted,
                style = MaterialTheme.typography.bodyMedium)
            Text("›", Modifier.clickable { if (month < YearMonth.now(JAKARTA)) { month = month.plusMonths(1); picked = null } }.padding(horizontal = 12.dp), color = c.text)
        }
        VSpace(8)
        Row {
            DayOfWeek.entries.forEach { d ->
                Text(d.getDisplayName(TextStyle.NARROW, I18n.locale), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
        }
        val lead = month.atDay(1).dayOfWeek.value - 1
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).toList()
        cells.chunked(7).forEach { week ->
            Row {
                (0 until 7).forEach { i ->
                    val day = week.getOrNull(i)
                    val v = day?.let { spend[it] } ?: 0L
                    val alpha = if (max == 0L || v == 0L) 0f else 0.25f + 0.75f * v / max
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (day == null) c.card else c.barTrack)
                        .then(if (day != null) Modifier.clickable { picked = day } else Modifier), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            Box(Modifier.matchParentSize().background(c.expense.copy(alpha = alpha)))
                            Text("$day", style = MaterialTheme.typography.labelSmall, color = if (alpha > 0.6f) androidx.compose.ui.graphics.Color.White else c.text)
                        }
                    }
                }
            }
        }
        val top = spend.maxByOrNull { it.value }
        VSpace(6)
        Text(
            picked?.let { d -> t("%d %s: %s", d, DateTimeFormatter.ofPattern("MMM", I18n.locale).format(month.atDay(d)), if (hidden) Rupiah.MASK else Rupiah.full(spend[d] ?: 0)) }
                ?: top?.let { t("Hari paling boros: tanggal %d (%s)", it.key, if (hidden) Rupiah.MASK else Rupiah.full(it.value)) }
                ?: t("Belum ada pengeluaran bulan ini."),
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
    }
}

@Composable
fun NotesCard(notes: List<String>, roast: Boolean, onRoast: (Boolean) -> Unit) {
    val c = LocalDompetkuColors.current
    SectionCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Catatan untukmu"), style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(t("Dihitung di HP, tanpa AI dan tanpa internet"), style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
            Text(t("Roasting"), style = MaterialTheme.typography.labelMedium, color = c.muted)
            Switch(roast, onRoast)
        }
        VSpace(8)
        notes.forEach { Text("• $it", Modifier.padding(vertical = 3.dp), style = MaterialTheme.typography.bodyMedium, color = c.text) }
    }
}

/** Export transactions of a period to Excel or CSV through the system "save as" picker. */
@Composable
fun ExportCard(txs: List<Tx>, accounts: Map<Long, Account>, categories: Map<Long, CategoryEntity>) {
    val c = LocalDompetkuColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var which by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var xlsx by remember { mutableStateOf(true) }
    val now = Instant.now()
    val periods = listOf(t("Bulan ini") to Period.thisMonth(now), t("Bulan lalu") to Period.lastMonth(now), t("Semua") to null)

    fun rows(): List<List<Any?>> {
        val p = periods[which].second
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val list = txs.filter { p == null || p.contains(it.date) }.sortedBy { it.date }
        return listOf(listOf(t("Tanggal"), t("Jenis"), t("Nominal"), t("Kategori"), t("Akun"), t("Ke akun"), t("Toko / penerima"), t("Keterangan"), t("Tag"), t("Catatan"))) +
            list.map { tx ->
                val cat = tx.categoryId?.let { categories[it] }
                val parent = cat?.parentId?.let { categories[it] }
                listOf(
                    fmt.format(tx.date.atZone(JAKARTA)), t(tx.type.label),
                    if (tx.type == TxType.EXPENSE) -tx.amount else tx.amount,
                    listOfNotNull(parent?.name, cat?.name).joinToString(" › ").ifEmpty { null },
                    accounts[tx.sourceAccountId]?.name, tx.destinationAccountId?.let { accounts[it]?.name },
                    tx.merchant, tx.description, Tags.display(tx.tags).ifEmpty { null }, tx.notes,
                )
            }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) scope.launch {
            status = runCatching { write(context, uri, if (xlsx) Export.xlsx(rows(), t("Transaksi")) else Export.csv(rows())) }
                .fold({ t("File tersimpan.") }, { t("Gagal menyimpan: %s", it.message ?: "") })
        }
    }

    SectionCard(Modifier.fillMaxWidth()) {
        Text(t("Ekspor transaksi"), style = MaterialTheme.typography.titleMedium, color = c.text)
        VSpace(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { periods.forEachIndexed { i, (l, _) -> Chip(l, which == i, { which = i }) } }
        VSpace(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val stamp = java.time.LocalDate.now(JAKARTA).toString()
            OutlinedButton({ xlsx = true; save.launch("babingepet-$stamp.xlsx") }) { Text("Excel") }
            OutlinedButton({ xlsx = false; save.launch("babingepet-$stamp.csv") }) { Text("CSV") }
        }
        status?.let { VSpace(6); Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
    }
}

private suspend fun write(context: Context, uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
    val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
        ?: context.contentResolver.openOutputStream(uri, "w") ?: error(t("File tidak bisa ditulis"))
    out.use { it.write(bytes) }
}

/** Pick which accounts count as emergency fund (none picked = savings + deposit accounts). */
@Composable
fun FundDialog(views: List<id.dompetku.data.AccountView>, selected: Set<Long>, onSave: (Set<Long>) -> Unit, onClose: () -> Unit) {
    val c = LocalDompetkuColors.current
    val live = views.filter { !it.account.isArchived }
    val defaults = live.filter { it.account.type == id.dompetku.domain.AccountType.SAVINGS || it.account.type == id.dompetku.domain.AccountType.DEPOSIT }.map { it.account.id }.toSet()
    var picked by remember { mutableStateOf(selected.ifEmpty { defaults }) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t("Akun dana darurat")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                live.forEach { v ->
                    Row(Modifier.fillMaxWidth().clickable { picked = if (v.account.id in picked) picked - v.account.id else picked + v.account.id },
                        verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(v.account.id in picked, { on -> picked = if (on) picked + v.account.id else picked - v.account.id })
                        Text(v.account.name, color = c.text)
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton({ onSave(if (picked == defaults) emptySet() else picked) }) { Text(t("Simpan")) } },
        dismissButton = { androidx.compose.material3.TextButton(onClose) { Text(t("Batal")) } },
    )
}
