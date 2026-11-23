package id.dompetku.ui.accounts

import id.dompetku.core.t

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.dompetku.core.Grams
import id.dompetku.core.Rupiah
import id.dompetku.data.TxFilter
import id.dompetku.domain.AccountType
import id.dompetku.domain.Snapshot
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.MoneyField
import id.dompetku.ui.appViewModel
import id.dompetku.ui.components.AmountText
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.formatShort
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.HeroAmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import id.dompetku.ui.transactions.TransactionList
import id.dompetku.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountDetailScreen(nav: NavController, accountId: Long) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val accountsVm = appViewModel { AccountsViewModel(it) }
    val views by accountsVm.views.collectAsStateWithLifecycle()
    val v = views?.firstOrNull { it.account.id == accountId }
    val history by remember(accountId) { container.snapshots.observeHistory(accountId) }.collectAsState(initial = emptyList())
    val txVm = appViewModel(key = "acc$accountId") { TransactionsViewModel(it, TxFilter(accountId = accountId)) }
    val scope = rememberCoroutineScope()
    var valuing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountFormState?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (views != null && v == null) { nav.popBackStack(); return }
    if (v == null) return
    val a = v.account

    TransactionList(nav, txVm, title = null, showAccountFilter = false, header = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("Kembali"), tint = c.text) }
                Text(a.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = c.text)
            }
            Text(listOfNotNull(a.type.label, a.provider).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            AmountText(v.currentValue, style = HeroAmountStyle, color = c.text)
            v.goldTenThousandths?.let { Text(Grams.format(it), style = MaterialTheme.typography.titleMedium, color = c.text) }
            if (a.type.isValued) {
                Text(t("Modal tercatat: %s · Selisih %s", Rupiah.full(v.balance), Rupiah.signed(v.currentValue - v.balance)) +
                    (id.dompetku.prices.Valuation.profit(v.currentValue, v.balance).second?.let { " (" + String.format(java.util.Locale("id", "ID"), "%+.2f", it) + "%)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.type.isValued) Button({ valuing = true }) { Text(t("Perbarui nilai")) }
                OutlinedButton({
                    editing = AccountFormState(a.name, a.type, a.provider ?: "", a.openingBalance.toString().trimStart('-'),
                        isLiquid = a.isLiquid, editing = true)
                }) { Text(t("Ubah")) }
                OutlinedButton({ scope.launch { container.accounts.archive(a.id, !a.isArchived) } }) {
                    Text(if (a.isArchived) t("Aktifkan") else t("Arsipkan"))
                }
                TextButton({ confirmDelete = true }) { Text(t("Hapus"), color = c.expense) }
            }
            message?.let { Text(it, color = c.expense, style = MaterialTheme.typography.bodySmall) }

            editing?.let { f ->
                SectionCard {
                    AccountForm(f, { editing = it })
                    VSpace(12)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ editing = null }) { Text(t("Batal")) }
                        Button({
                            if (f.name.isBlank()) message = t("Nama akun wajib diisi") else scope.launch {
                                container.accounts.update(
                                    a.copy(name = f.name.trim(), type = f.type, provider = f.provider.ifBlank { null },
                                        openingBalance = f.balance.toLongOrNull() ?: 0L, isLiquid = f.isLiquid)
                                )
                                editing = null; message = null
                            }
                        }) { Text(t("Simpan")) }
                    }
                }
            }

            if (valuing) ValuationCard(a.type, v.goldTenThousandths, history.firstOrNull(), onCancel = { valuing = false }) { snap ->
                scope.launch { container.snapshots.add(snap.copy(accountId = a.id)); valuing = false }
            }

            if (a.type == AccountType.INVESTMENT) HoldingsSection(a.id)
            if (a.type == AccountType.GOLD) GoldPriceLine(a.id)

            if (history.isNotEmpty()) {
                Text(t("Riwayat nilai"), style = MaterialTheme.typography.titleMedium, color = c.text)
                SectionCard(Modifier.fillMaxWidth()) {
                    history.take(12).forEach { h ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Text(formatShort(h.date) + (h.goldTenThousandths?.let { " · ${Grams.format(it)}" } ?: ""),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                            AmountText(h.currentValue, style = AmountStyle.copy(fontSize = 14.sp), color = c.text)
                        }
                    }
                }
            }
            Text(t("Transaksi"), style = MaterialTheme.typography.titleMedium, color = c.text)
        }
    })

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Hapus akun?")) },
            text = { Text(t("Akun hanya bisa dihapus jika belum punya transaksi. Jika sudah, arsipkan saja.")) },
            confirmButton = {
                TextButton({
                    scope.launch {
                        confirmDelete = false
                        container.accounts.delete(a.id).onFailure { message = it.message }.onSuccess { nav.popBackStack() }
                    }
                }) { Text(t("Hapus"), color = c.expense) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(t("Batal")) } },
        )
    }
}

@Composable
private fun ValuationCard(
    type: AccountType,
    currentGrams: Long?,
    last: Snapshot?,
    onCancel: () -> Unit,
    onSave: (Snapshot) -> Unit,
) {
    val c = LocalDompetkuColors.current
    var value by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf(currentGrams?.let { Grams.format(it).removeSuffix(" gr") } ?: "") }
    var price by remember { mutableStateOf(last?.goldPricePerGram?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    SectionCard {
        Text(t("Perbarui nilai"), style = MaterialTheme.typography.titleMedium, color = c.text)
        VSpace(8)
        if (type == AccountType.GOLD) {
            OutlinedTextField(grams, { grams = it }, label = { Text(t("Jumlah emas (gram)")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            VSpace(8)
            MoneyField(t("Harga jual per gram hari ini"), price, { price = it }, Modifier.fillMaxWidth())
            val g = Grams.parse(grams); val p = price.toLongOrNull()
            if (g != null && p != null) Text(t("Nilai: %s", Rupiah.full(Grams.valueOf(g, p))), color = c.muted)
        } else {
            MoneyField(t("Nilai saat ini"), value, { value = it }, Modifier.fillMaxWidth())
        }
        error?.let { Text(it, color = c.expense) }
        VSpace(12)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onCancel) { Text(t("Batal")) }
            Button({
                val now = Instant.now()
                if (type == AccountType.GOLD) {
                    val g = Grams.parse(grams); val p = price.toLongOrNull()
                    if (g == null || p == null || p <= 0) error = t("Isi gram dan harga per gram") else
                        onSave(Snapshot(0, now, Grams.valueOf(g, p), g, p))
                } else {
                    val v = value.toLongOrNull()
                    if (v == null) error = t("Isi nilai saat ini") else onSave(Snapshot(0, now, v, null, null))
                }
            }) { Text(t("Simpan")) }
        }
    }
}
