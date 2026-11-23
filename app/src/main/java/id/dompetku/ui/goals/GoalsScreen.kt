package id.dompetku.ui.goals

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import id.dompetku.core.Rupiah
import id.dompetku.core.t
import id.dompetku.data.AccountView
import id.dompetku.data.db.GoalEntity
import id.dompetku.domain.JAKARTA
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.MoneyField
import id.dompetku.ui.components.EmptyState
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.formatDay
import id.dompetku.ui.theme.LocalDompetkuColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val GOALS_ROUTE = "goals"

/** Progress of a goal and what's still needed per month to make the deadline. */
data class GoalProgress(val saved: Long, val target: Long, val perMonth: Long, val monthsLeft: Long) {
    val percent: Int get() = if (target <= 0) 0 else (saved * 100 / target).toInt().coerceIn(0, 100)
    val done: Boolean get() = saved >= target
}

object Goals {
    fun progress(g: GoalEntity, views: List<AccountView>, today: LocalDate): GoalProgress {
        val saved = g.accountId?.let { id -> views.firstOrNull { it.account.id == id }?.currentValue } ?: g.saved
        val deadline = java.time.Instant.ofEpochMilli(g.deadline).atZone(JAKARTA).toLocalDate()
        // Count the current month too: a deadline later this month still leaves one month to save in.
        val months = (ChronoUnit.MONTHS.between(today.withDayOfMonth(1), deadline.withDayOfMonth(1)) + 1).coerceAtLeast(1)
        val left = (g.target - saved).coerceAtLeast(0)
        return GoalProgress(saved, g.target, (left + months - 1) / months, months)
    }
}

@Composable
fun GoalsScreen(nav: NavController) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val goals by container.goals.observeAll().collectAsState(initial = emptyList())
    val views by container.dashboard.accountViews().collectAsState(initial = emptyList())
    val hidden by container.settings.hideBalances.collectAsState(initial = false)
    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    val today = LocalDate.now(JAKARTA)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("Kembali"), tint = c.text) }
            Text(t("Tujuan menabung"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        if (goals.isEmpty()) EmptyState(t("Belum ada tujuan. Misalnya: dana nikah, liburan, atau DP rumah."))
        goals.forEach { g ->
            val p = Goals.progress(g, views, today)
            SectionCard(Modifier.fillMaxWidth(), onClick = { editing = g }) {
                Row {
                    Text(g.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text("${p.percent}%", style = MaterialTheme.typography.titleMedium, color = if (p.done) c.income else c.text)
                }
                Text(if (hidden) Rupiah.MASK else Rupiah.full(p.saved) + " / " + Rupiah.full(p.target),
                    style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.barTrack)) {
                    Box(Modifier.fillMaxWidth(p.percent / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(c.income))
                }
                val deadline = java.time.Instant.ofEpochMilli(g.deadline).atZone(JAKARTA).toLocalDate()
                Text(
                    if (p.done) t("Tercapai!") else t("Sisihkan %s per bulan · tenggat %s", if (hidden) Rupiah.MASK else Rupiah.full(p.perMonth), formatDay(deadline)),
                    style = MaterialTheme.typography.bodySmall, color = if (!p.done && deadline.isBefore(today)) c.expense else c.muted,
                )
                g.accountId?.let { id -> views.firstOrNull { it.account.id == id }?.let {
                    Text(t("Mengikuti saldo %s", it.account.name), style = MaterialTheme.typography.labelSmall, color = c.muted)
                } }
            }
        }
        Button({ editing = GoalEntity(0, "", 0, today.plusYears(1).atStartOfDay(JAKARTA).toInstant().toEpochMilli(), null, 0, 0) },
            colors = ButtonDefaults.buttonColors(containerColor = c.fab, contentColor = c.onFab)) { Text(t("Tambah tujuan")) }
        VSpace(24)
    }
    editing?.let { GoalDialog(it, views) { editing = null } }
}

@Composable
private fun GoalDialog(initial: GoalEntity, views: List<AccountView>, onClose: () -> Unit) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var target by remember { mutableStateOf(initial.target.takeIf { it > 0 }?.toString() ?: "") }
    var saved by remember { mutableStateOf(initial.saved.takeIf { it > 0 }?.toString() ?: "") }
    var deadline by remember { mutableStateOf(java.time.Instant.ofEpochMilli(initial.deadline).atZone(JAKARTA).toLocalDate()) }
    var accountId by remember { mutableStateOf(initial.accountId) }
    var menu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val live = views.filter { !it.account.isArchived || it.account.id == initial.accountId }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (initial.id == 0L) t("Tujuan baru") else t("Ubah tujuan")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(t("Nama")) }, singleLine = true)
                MoneyField(t("Target"), target, { target = it })
                OutlinedButton({
                    DatePickerDialog(context, { _, y, m, d -> deadline = LocalDate.of(y, m + 1, d) }, deadline.year, deadline.monthValue - 1, deadline.dayOfMonth).show()
                }) { Text(t("Tenggat: %s", formatDay(deadline))) }
                Box {
                    OutlinedButton({ menu = true }) {
                        Text(accountId?.let { id -> live.firstOrNull { it.account.id == id }?.account?.name } ?: t("Tanpa akun (isi manual)"))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text(t("Tanpa akun (isi manual)")) }, { accountId = null; menu = false })
                        live.forEach { v -> DropdownMenuItem({ Text(v.account.name) }, { accountId = v.account.id; menu = false }) }
                    }
                }
                if (accountId == null) MoneyField(t("Sudah terkumpul"), saved, { saved = it })
                else Text(t("Progres mengikuti saldo akun ini."), style = MaterialTheme.typography.bodySmall, color = c.muted)
                error?.let { Text(it, color = c.expense) }
                if (initial.id != 0L) TextButton({ scope.launch { container.goals.delete(initial.id); onClose() } }) { Text(t("Hapus tujuan"), color = c.expense) }
            }
        },
        confirmButton = {
            TextButton({
                scope.launch {
                    container.goals.save(initial.copy(
                        name = name, target = target.toLongOrNull() ?: 0, saved = saved.toLongOrNull() ?: 0, accountId = accountId,
                        deadline = deadline.atStartOfDay(JAKARTA).toInstant().toEpochMilli(),
                    )).onSuccess { onClose() }.onFailure { error = it.message }
                }
            }) { Text(t("Simpan")) }
        },
        dismissButton = { TextButton(onClose) { Text(t("Batal")) } },
    )
}
