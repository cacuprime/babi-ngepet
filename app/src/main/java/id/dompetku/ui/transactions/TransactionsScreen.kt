package id.dompetku.ui.transactions

import id.dompetku.core.t

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.dompetku.core.Rupiah
import id.dompetku.data.TxFilter
import id.dompetku.domain.TxType
import id.dompetku.ui.Routes
import id.dompetku.ui.appViewModel
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.EmptyState
import id.dompetku.ui.components.TxRow
import id.dompetku.ui.formatDay
import id.dompetku.ui.home.PeriodChoice
import id.dompetku.ui.theme.LocalDompetkuColors
import java.time.Instant

@Composable
fun TransactionsScreen(nav: NavController) {
    val vm = appViewModel(key = "txlist") { TransactionsViewModel(it) }
    TransactionList(nav, vm, title = t("Transaksi"), showAccountFilter = true, header = {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            androidx.compose.material3.OutlinedButton({ nav.navigate(id.dompetku.ui.Routes.IMPORT) }) {
                Text(t("Impor foto / rekening koran"))
            }
        }
    })
}

/** One category's transactions in one week or month (opened from the spending dashboard). */
@Composable
fun CategoryTransactionsScreen(nav: NavController, categoryId: Long, fromMs: Long, toMs: Long) {
    val c = LocalDompetkuColors.current
    val period = id.dompetku.domain.Period(Instant.ofEpochMilli(fromMs), Instant.ofEpochMilli(toMs))
    val vm = appViewModel(key = "cat$categoryId-$fromMs") {
        TransactionsViewModel(it, TxFilter(period = period, categoryId = categoryId, type = TxType.EXPENSE))
    }
    val s by vm.state.collectAsStateWithLifecycle()
    TransactionList(nav, vm, title = null, showAccountFilter = false, header = {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.IconButton({ nav.popBackStack() }) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack, t("Kembali"), tint = c.text)
            }
            Column {
                Text(s.categories[categoryId]?.name ?: t("Kategori"), style = MaterialTheme.typography.titleLarge, color = c.text)
                val days = (toMs - fromMs) / 86_400_000L
                Text(period.label(if (days <= 7) id.dompetku.domain.Span.WEEK else id.dompetku.domain.Span.MONTH),
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    })
}

@Composable
fun TransactionList(
    nav: NavController,
    vm: TransactionsViewModel,
    title: String?,
    showAccountFilter: Boolean,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val c = LocalDompetkuColors.current
    val s by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf(s.filter.query ?: "") }
    var periodChoice by remember { mutableStateOf<PeriodChoice?>(null) }
    var accountMenu by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        if (header != null) item { header() }
        if (title != null) item {
            Text(title, Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineSmall, color = c.text)
        }
        item {
            OutlinedTextField(
                query, { query = it; vm.update { f -> f.copy(query = it.ifBlank { null }) } },
                placeholder = { Text(t("Cari toko, keterangan, catatan")) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(t("Semua"), s.filter.type == null, { vm.update { it.copy(type = null) } })
                listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER).forEach { t ->
                    Chip(t.label, s.filter.type == t, { vm.update { f -> f.copy(type = t) } })
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(t("Semua waktu"), periodChoice == null, { periodChoice = null; vm.update { it.copy(period = null) } })
                PeriodChoice.entries.forEach { p ->
                    Chip(p.label, periodChoice == p, { periodChoice = p; vm.update { f -> f.copy(period = p.period(Instant.now())) } })
                }
                if (showAccountFilter) {
                    Box {
                        Chip(s.filter.accountId?.let { s.accounts[it]?.name } ?: t("Semua akun"), s.filter.accountId != null, { accountMenu = true })
                        DropdownMenu(accountMenu, { accountMenu = false }) {
                            DropdownMenuItem({ Text(t("Semua akun")) }, { vm.update { it.copy(accountId = null) }; accountMenu = false })
                            s.accounts.values.forEach { a ->
                                DropdownMenuItem({ Text(a.name) }, { vm.update { it.copy(accountId = a.id) }; accountMenu = false })
                            }
                        }
                    }
                }
            }
        }
        if (s.loaded && s.groups.isEmpty()) item { EmptyState(t("Tidak ada transaksi yang cocok.")) }
        s.groups.forEach { (day, txs) ->
            item(key = "h$day") {
                val net = txs.sumOf { when (it.type) { TxType.INCOME -> it.amount; TxType.EXPENSE -> -it.amount; else -> 0 } }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                    Text(formatDay(day), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = c.muted)
                    if (net != 0L) Text(Rupiah.signed(net), style = MaterialTheme.typography.labelLarge, color = c.muted)
                }
            }
            item(key = "g$day") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).padding(horizontal = 16.dp)) {
                    txs.forEach { tx -> TxRow(tx, s.accounts, s.categories, onClick = { nav.navigate(Routes.editTx(tx.id)) }) }
                }
            }
        }
        item { Box(Modifier.padding(24.dp)) }
    }
}
