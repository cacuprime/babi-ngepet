package id.dompetku.ui.home

import id.dompetku.core.t

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.dompetku.core.Rupiah
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.Routes
import id.dompetku.ui.appViewModel
import id.dompetku.ui.components.AmountText
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.EmptyState
import id.dompetku.ui.components.HeroCard
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.SectionTitle
import id.dompetku.ui.components.StatCard
import id.dompetku.ui.components.TxRow
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import id.dompetku.ui.theme.Lime
import androidx.compose.ui.graphics.Color
import java.time.LocalTime
import java.time.ZoneId

private fun greeting(): String = when (LocalTime.now(ZoneId.of("Asia/Jakarta")).hour) {
    in 4..10 -> t("Selamat pagi")
    in 11..14 -> t("Selamat siang")
    in 15..17 -> t("Selamat sore")
    else -> t("Selamat malam")
}

@Composable
fun HomeScreen(nav: NavController) {
    val c = LocalDompetkuColors.current
    val vm = appViewModel { HomeViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val name by LocalContainer.current.settings.userName.collectAsState(initial = "")
    val sm = s.summary

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Text(name.ifBlank { "Babi Ngepet" }, style = MaterialTheme.typography.titleLarge, color = c.text)
            }
            IconButton({ nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, "Pengaturan", tint = c.text) }
        }

        BalancePager(s.pages, s.hidden, onToggleHidden = vm::toggleHidden, onOpenAccount = { nav.navigate(Routes.account(it)) })
        Text("Atur kartu", Modifier.fillMaxWidth().clickable { nav.navigate(Routes.HOME_ORDER) }.padding(vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.End)

        id.dompetku.ui.budget.TodayCard(s.hidden) { nav.navigate(id.dompetku.ui.budget.BudgetRoutes.BUDGET) }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodChoice.entries.forEach { p -> Chip(p.label, s.choice == p, { vm.select(p) }) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(t("Masuk"), sm.income, Icons.Outlined.SouthWest, Lime, Color(0xFF111111), Modifier.weight(1f))
            StatCard(t("Keluar"), sm.expense, Icons.Outlined.NorthEast, c.expense, Color.White, Modifier.weight(1f))
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Arus kas bersih"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text)
                AmountText(
                    sm.netCashFlow, signed = true, style = AmountStyle.copy(fontSize = 18.sp),
                    color = if (sm.netCashFlow >= 0) c.income else c.expense,
                )
            }
            Text(t("Transfer antar-akun tidak dihitung"), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }

        val container = LocalContainer.current
        val waiting by container.inbox.observeWaiting().collectAsState(initial = emptyList())
        if (waiting.isNotEmpty()) {
            SectionCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.INBOX) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("%d transaksi perlu dicek", waiting.size), style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(t("Dari notifikasi bank · belum masuk saldo"), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    AmountText(waiting.filter { it.txType == "EXPENSE" }.sumOf { it.amount ?: 0 },
                        style = AmountStyle.copy(fontSize = 16.sp), color = c.expense)
                }
            }
        }

        var voiceOpen by remember { mutableStateOf(false) }
        SectionCard(Modifier.fillMaxWidth(), onClick = { voiceOpen = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, null, tint = c.text)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(t("Catat pakai suara"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(t("Misal: \"bakso 25 ribu pakai cash\""), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
        if (voiceOpen) id.dompetku.ui.voice.VoiceEntry(onClose = { voiceOpen = false })

        var bulkOpen by remember { mutableStateOf(false) }
        SectionCard(Modifier.fillMaxWidth(), onClick = { bulkOpen = true }) {
            Text(id.dompetku.core.t("Tambah banyak sekaligus"), style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(id.dompetku.core.t("Ketik beberapa transaksi, satu per baris"), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (bulkOpen) id.dompetku.ui.voice.VoiceEntry(onClose = { bulkOpen = false }, typed = true)

        SectionCard(Modifier.fillMaxWidth()) {
            SectionTitle(t("Transaksi terakhir"), action = t("Lihat semua"), onAction = { nav.navigate(Routes.TRANSACTIONS) })
            if (s.loaded && s.recent.isEmpty()) {
                EmptyState(
                    if (s.accounts.isEmpty()) t("Tambahkan akun dulu di menu Akun, lalu catat transaksi pertamamu dengan tombol +.")
                    else t("Belum ada transaksi. Tekan tombol + untuk mencatat.")
                )
            }
            s.recent.forEach { tx ->
                TxRow(tx, s.accounts, s.categories, onClick = { nav.navigate(Routes.editTx(tx.id)) })
            }
        }
        VSpace(16)
    }
}
