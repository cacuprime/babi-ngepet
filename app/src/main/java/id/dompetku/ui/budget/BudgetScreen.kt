package id.dompetku.ui.budget

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import id.dompetku.core.Rupiah
import id.dompetku.core.t
import id.dompetku.domain.Bucket
import id.dompetku.domain.Budget
import id.dompetku.domain.BudgetMode
import id.dompetku.domain.Budgets
import id.dompetku.domain.JAKARTA
import id.dompetku.domain.Period
import id.dompetku.domain.Reports
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.MoneyField
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.EmptyState
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.formatShort
import id.dompetku.ui.theme.LocalDompetkuColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

object BudgetRoutes {
    const val BUDGET = "budget"
    fun setup(id: Long = 0) = "budget/setup/$id"
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    val c = LocalDompetkuColors.current
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("Kembali"), tint = c.text) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.text)
    }
}

@Composable
fun BudgetScreen(nav: NavController) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val state by remember { budgetState(container) }.collectAsState(initial = null)
    val hidden by container.settings.hideBalances.collectAsState(initial = false)
    var confirmDelete by remember { mutableStateOf<Budget?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(t("Budget")) { nav.popBackStack() }
        val s = state ?: return@Column
        val cur = s.current
        if (cur == null) {
            SectionCard(Modifier.fillMaxWidth()) {
                Text(t("Belum ada budget untuk hari ini"), style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(t("Bagi gajimu ke Kebutuhan, Keinginan, dan Tabungan. Babi Ngepet akan menghitung jatah belanja harianmu."),
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
                VSpace(12)
                Button({ nav.navigate(BudgetRoutes.setup()) }, colors = ButtonDefaults.buttonColors(containerColor = c.fab, contentColor = c.onFab)) {
                    Text(t("Buat budget"))
                }
                s.latest?.let { last ->
                    VSpace(8)
                    OutlinedButton({
                        scope.launch { container.budgets.copyToCurrent(last, Instant.now()).onFailure { message = it.message } }
                    }) { Text(t("Salin budget %s ke periode ini", periodText(last))) }
                }
            }
        } else {
            val b = cur.budget
            val (day, total) = dayOf(b, Instant.now())
            SectionCard(Modifier.fillMaxWidth()) {
                Text(periodText(b), style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(t("Hari %d dari %d", day, total) + " · " + (if (b.mode == BudgetMode.PERCENT) t("Persentase") else t("Jumlah tetap")),
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
                VSpace(8)
                cur.usage.forEach { BucketBar(it, hidden) }
                cur.daily?.let { d ->
                    VSpace(8)
                    Text(t("Jatah harian %s · sisa hari ini %s · %d hari lagi",
                        if (hidden) Rupiah.MASK else Rupiah.full(d.allowance), if (hidden) Rupiah.MASK else Rupiah.full(d.leftToday), d.daysLeft),
                        style = MaterialTheme.typography.bodySmall, color = if (d.leftToday < 0) c.expense else c.muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ nav.navigate(BudgetRoutes.setup(b.id)) }) { Text(t("Ubah")) }
                OutlinedButton({
                    scope.launch { container.budgets.copyToNext(b).onSuccess { message = t("Budget disalin ke periode berikutnya.") }.onFailure { message = it.message } }
                }) { Text(t("Salin ke periode berikutnya")) }
            }
            TextButton({ confirmDelete = b }) { Text(t("Hapus budget ini"), color = c.expense) }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.text) }

        val others = s.all.filter { it.id != cur?.budget?.id }
        if (others.isNotEmpty()) {
            Text(t("Periode lain"), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, color = c.text)
            SectionCard(Modifier.fillMaxWidth()) {
                others.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { nav.navigate(BudgetRoutes.setup(o.id)) }.padding(vertical = 8.dp)) {
                        Text(periodText(o), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text)
                        Text(Rupiah.compact(o.limits.values.sum()), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    }
                }
            }
        }
        VSpace(24)
    }

    confirmDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(t("Hapus budget?")) },
            text = { Text(t("Transaksi tidak ikut terhapus, hanya batas budget periode %s.", periodText(b))) },
            confirmButton = { TextButton({ scope.launch { container.budgets.delete(b.id); confirmDelete = null } }) { Text(t("Hapus"), color = c.expense) } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text(t("Batal")) } },
        )
    }
}

/** 6 steps: period → income → method → split → categories → review. */
@Composable
fun BudgetSetupScreen(nav: NavController, budgetId: Long) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now(JAKARTA)
    var step by remember { mutableIntStateOf(1) }
    var loaded by remember { mutableStateOf(false) }
    var monthly by remember { mutableStateOf(true) }
    var startDay by remember { mutableStateOf("1") }
    var from by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var to by remember { mutableStateOf(today.withDayOfMonth(today.lengthOfMonth())) }
    var income by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(BudgetMode.PERCENT) }
    val pct = remember { mutableStateMapOf<Bucket, String>().apply { Budgets.DEFAULT_PERCENTS.forEach { (k, v) -> put(k, v.toString()) } } }
    val fixed = remember { mutableStateMapOf<Bucket, String>() }
    var error by remember { mutableStateOf<String?>(null) }
    // Editing an old monthly budget keeps it in its own month instead of moving it to today.
    var anchor by remember { mutableStateOf(Instant.now()) }
    val categories by container.categories.observe("EXPENSE").collectAsState(initial = emptyList())

    LaunchedEffect(budgetId) {
        val existing = container.budgets.observeAll().first().firstOrNull { it.id == budgetId }
        if (existing != null) {
            anchor = existing.period.start
            monthly = existing.startDay != null
            startDay = (existing.startDay ?: 1).toString()
            from = existing.period.start.atZone(JAKARTA).toLocalDate()
            to = existing.period.endExclusive.atZone(JAKARTA).toLocalDate().minusDays(1)
            income = existing.income.takeIf { it > 0 }?.toString() ?: ""
            mode = existing.mode
            existing.percents.forEach { (k, v) -> pct[k] = v.toString() }
            existing.limits.forEach { (k, v) -> fixed[k] = v.takeIf { it > 0 }?.toString() ?: "" }
        } else {
            // Start from last month's income: the usual salary.
            val txs = container.transactions.observeAll().first()
            val last = Reports.incomeVsExpenseByMonth(txs, 2, Instant.now()).first().income
            if (last > 0) income = last.toString()
        }
        loaded = true
    }
    if (!loaded) return

    val incomeRp = income.toLongOrNull() ?: 0L
    fun period(): Period = if (monthly) Budgets.monthlyPeriod(startDay.toIntOrNull() ?: 1, anchor) else Period.custom(from, to)
    fun percents() = Bucket.entries.associateWith { pct[it]?.toIntOrNull() ?: 0 }
    fun limits(): Map<Bucket, Long> = if (mode == BudgetMode.PERCENT) Budgets.limitsFrom(incomeRp, percents())
    else Bucket.entries.associateWith { fixed[it]?.toLongOrNull() ?: 0L }

    fun next() {
        error = when (step) {
            1 -> when {
                monthly && (startDay.toIntOrNull() ?: 0) !in 1..28 -> t("Tanggal mulai 1–28")
                !monthly && to.isBefore(from) -> t("Tanggal akhir harus setelah tanggal mulai")
                else -> null
            }
            2 -> if (mode == BudgetMode.PERCENT && incomeRp <= 0) t("Isi pemasukan untuk periode ini") else null
            4 -> when {
                mode == BudgetMode.PERCENT && percents().values.sum() != 100 -> t("Total persentase %d%%, harus 100%%", percents().values.sum())
                mode == BudgetMode.FIXED && limits().values.sum() <= 0 -> t("Isi minimal satu pos")
                else -> null
            }
            else -> null
        }
        if (error == null) {
            if (step == 3 && mode == BudgetMode.FIXED && fixed.isEmpty()) {
                Budgets.limitsFrom(incomeRp, Budgets.DEFAULT_PERCENTS).forEach { (k, v) -> fixed[k] = v.takeIf { it > 0 }?.toString() ?: "" }
            }
            step++
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(t("Atur budget · langkah %d dari 6", step)) { if (step > 1) step-- else nav.popBackStack() }
            when (step) {
                1 -> {
                    Text(t("Periode budget"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(t("Bulanan"), monthly, { monthly = true })
                        Chip(t("Tanggal sendiri"), !monthly, { monthly = false })
                    }
                    if (monthly) {
                        OutlinedTextField(startDay, { v -> startDay = v.filter { it.isDigit() }.take(2) }, label = { Text(t("Mulai tiap tanggal (hari gajian)")) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Text(t("Periode sekarang: %s", periodLabel(period())), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    } else {
                        OutlinedButton({ DatePickerDialog(context, { _, y, m, d -> from = LocalDate.of(y, m + 1, d) }, from.year, from.monthValue - 1, from.dayOfMonth).show() },
                            Modifier.fillMaxWidth()) { Text(t("Dari: %s", id.dompetku.ui.formatDay(from))) }
                        OutlinedButton({ DatePickerDialog(context, { _, y, m, d -> to = LocalDate.of(y, m + 1, d) }, to.year, to.monthValue - 1, to.dayOfMonth).show() },
                            Modifier.fillMaxWidth()) { Text(t("Sampai: %s", id.dompetku.ui.formatDay(to))) }
                    }
                }
                2 -> {
                    Text(t("Pemasukan periode ini"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(t("Biasanya gaji. Diisi otomatis dari pemasukan bulan lalu, ubah jika perlu."), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    MoneyField(t("Pemasukan"), income, { income = it }, Modifier.fillMaxWidth())
                }
                3 -> {
                    Text(t("Cara membagi"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    SectionCard(Modifier.fillMaxWidth(), onClick = { mode = BudgetMode.PERCENT }) {
                        Text((if (mode == BudgetMode.PERCENT) "● " else "○ ") + t("Persentase"), style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(t("Gaji dibagi persen, misalnya 50% kebutuhan, 30% keinginan, 20% tabungan."), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    SectionCard(Modifier.fillMaxWidth(), onClick = { mode = BudgetMode.FIXED }) {
                        Text((if (mode == BudgetMode.FIXED) "● " else "○ ") + t("Jumlah tetap"), style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(t("Tentukan sendiri nominal tiap pos dalam rupiah."), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
                4 -> {
                    Text(t("Pembagian"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Bucket.entries.forEach { b ->
                        if (mode == BudgetMode.PERCENT) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(pct[b] ?: "", { v -> pct[b] = v.filter { it.isDigit() }.take(3) }, label = { Text(t(b.label) + " (%)") },
                                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                Text("  " + Rupiah.full(limits()[b] ?: 0), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                            }
                        } else {
                            MoneyField(t(b.label), fixed[b] ?: "", { fixed[b] = it }, Modifier.fillMaxWidth())
                        }
                    }
                    if (mode == BudgetMode.PERCENT) Text(t("Total %d%%", percents().values.sum()), style = MaterialTheme.typography.bodySmall,
                        color = if (percents().values.sum() == 100) c.muted else c.expense)
                }
                5 -> {
                    Text(t("Kategori ke pos mana?"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(t("Ketuk untuk mengganti. Subkategori ikut induknya."), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    SectionCard(Modifier.fillMaxWidth()) {
                        categories.filter { it.parentId == null }.forEach { cat ->
                            val b = runCatching { Bucket.valueOf(cat.bucket ?: "WANTS") }.getOrDefault(Bucket.WANTS)
                            Row(Modifier.fillMaxWidth().clickable {
                                scope.launch { container.categories.setBucket(cat.id, Bucket.entries[(b.ordinal + 1) % 3]) }
                            }.padding(vertical = 8.dp)) {
                                Text(cat.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text)
                                Text(t(b.label), style = MaterialTheme.typography.labelLarge, color = c.accent)
                            }
                        }
                    }
                }
                else -> {
                    Text(t("Ringkasan"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    SectionCard(Modifier.fillMaxWidth()) {
                        Text(periodLabel(period()), style = MaterialTheme.typography.bodyLarge, color = c.text)
                        if (incomeRp > 0) Text(t("Pemasukan %s", Rupiah.full(incomeRp)), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        VSpace(8)
                        limits().forEach { (b, v) ->
                            Row {
                                Text(t(b.label), Modifier.weight(1f), color = c.text)
                                Text(Rupiah.full(v), color = c.text)
                            }
                        }
                        if (incomeRp > 0 && limits().values.sum() > incomeRp) {
                            VSpace(8)
                            Text(t("Total budget lebih besar dari pemasukan."), style = MaterialTheme.typography.bodySmall, color = c.expense)
                        }
                    }
                }
            }
            error?.let { Text(it, color = c.expense, style = MaterialTheme.typography.bodyMedium) }
            if (categories.isEmpty() && step == 5) EmptyState(t("Belum ada kategori pengeluaran."))
        }
        Button(
            onClick = {
                if (step < 6) { next(); return@Button }
                scope.launch {
                    val b = Budget(budgetId, period(), if (monthly) startDay.toIntOrNull() ?: 1 else null, mode, incomeRp,
                        if (mode == BudgetMode.PERCENT) percents() else Bucket.entries.associateWith { 0 }, limits())
                    container.budgets.save(b).onSuccess { nav.popBackStack() }.onFailure { error = it.message }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.fab, contentColor = c.onFab),
        ) { Text(if (step < 6) t("Lanjut") else t("Simpan budget")) }
    }
}

private fun periodLabel(p: Period) = formatShort(p.start) + " – " + formatShort(p.endExclusive.minusSeconds(1))
