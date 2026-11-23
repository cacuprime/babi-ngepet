package id.dompetku.ui.inbox

import id.dompetku.core.t

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import id.dompetku.capture.BankApp
import id.dompetku.capture.BankNotificationListener
import id.dompetku.core.Rupiah
import id.dompetku.data.InboxEdit
import id.dompetku.data.db.CategoryEntity
import id.dompetku.data.db.InboxItemEntity
import id.dompetku.domain.Account
import id.dompetku.domain.TxType
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.MoneyField
import id.dompetku.ui.Routes
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.EmptyState
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.formatDateTime
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import kotlinx.coroutines.launch
import java.time.Instant

fun InboxItemEntity.toEdit() = InboxEdit(
    id = id, type = txType?.let { TxType.valueOf(it) } ?: TxType.EXPENSE, amount = amount ?: 0, categoryId = categoryId,
    accountId = accountId, destinationAccountId = null, description = description, merchant = merchant,
)

/** "Perlu dicek": captured bank transactions waiting for one tap. Nothing here affects balances until saved. */
@Composable
fun InboxScreen(nav: NavController) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by container.inbox.observeWaiting().collectAsState(initial = null)
    val accounts by container.accounts.observeAll().collectAsState(initial = emptyList())
    val categories by container.categories.observeAll().collectAsState(initial = emptyList())
    val edits = remember { mutableStateMapOf<Long, InboxEdit>() }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<InboxEdit?>(null) }
    var busy by remember { mutableStateOf(false) }
    val live = accounts.filter { !it.isArchived }
    val access = remember { BankNotificationListener.isEnabled(context) }

    fun edit(i: InboxItemEntity) = edits[i.id] ?: i.toEdit()
    fun save(list: List<InboxEdit>) = scope.launch {
        if (busy) return@launch
        busy = true
        container.inbox.save(list)
            .onSuccess { n -> message = t("%d transaksi tersimpan", n); list.forEach { edits.remove(it.id) } }
            .onFailure { message = it.message }
        busy = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("Kembali"), tint = c.text) }
            Text(t("Perlu dicek"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = c.text)
            IconButton({ nav.navigate(Routes.CAPTURE_SETUP) }) { Icon(Icons.Outlined.Tune, t("Pengaturan catat otomatis"), tint = c.text) }
        }
        val list = items
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!access) item {
                SectionCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.CAPTURE_SETUP) }) {
                    Text(t("Catat otomatis belum aktif"), style = MaterialTheme.typography.titleMedium, color = c.expense)
                    Text(t("Ketuk untuk mengizinkan Babi Ngepet membaca notifikasi wondr, SeaBank, dan Livin'."),
                        style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            if (list != null && list.isEmpty()) item {
                EmptyState(t("Semua sudah dicek. Pembayaran QRIS baru akan muncul di sini otomatis."))
            }
            items(list.orEmpty(), key = { it.id }) { item ->
                ItemCard(item, edit(item), categories, live,
                    onChange = { edits[item.id] = it; message = null },
                    onOpen = { editing = edit(item) },
                    onSave = { save(listOf(edit(item))) },
                    onDismiss = { scope.launch { container.inbox.dismiss(item.id); edits.remove(item.id) } },
                )
            }
            item { VSpace(8) }
        }
        message?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = c.text) }
        val n = list?.size ?: 0
        if (n > 0) Button(
            onClick = { save(list!!.map { edit(it) }) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.fab, contentColor = c.onFab),
        ) { Text(t("Simpan semua (%d)", n)) }
    }

    editing?.let { e ->
        EditDialog(e, onDismiss = { editing = null }) { edits[e.id] = it; editing = null }
    }
}

@Composable
private fun ItemCard(
    item: InboxItemEntity, e: InboxEdit, categories: List<CategoryEntity>, accounts: List<Account>,
    onChange: (InboxEdit) -> Unit, onOpen: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit,
) {
    val c = LocalDompetkuColors.current
    SectionCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(e.merchant ?: e.description ?: t("Transaksi"), style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1)
                val app = runCatching { BankApp.valueOf(item.bankApp).label }.getOrDefault(item.bankApp)
                Text("$app · ${formatDateTime(Instant.ofEpochMilli(item.txDate))}", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Text(
                when (e.type) { TxType.INCOME -> "+"; TxType.TRANSFER -> ""; else -> "-" } + Rupiah.full(e.amount),
                style = AmountStyle.copy(fontSize = 15.sp),
                color = when (e.type) { TxType.INCOME -> c.income; TxType.TRANSFER -> c.text; else -> c.expense },
            )
        }
        VSpace(6)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Menu(
                label = when (e.type) { TxType.INCOME -> t("Pemasukan"); TxType.TRANSFER -> t("Transfer"); else -> t("Pengeluaran") }, set = true,
                options = listOf(t("Pengeluaran"), t("Pemasukan"), t("Transfer ke akun saya")),
            ) { i ->
                val t = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)[i]
                if (t != e.type) onChange(e.copy(type = t, categoryId = null, destinationAccountId = null))
            }
            if (e.type == TxType.TRANSFER) {
                val dst = accounts.filter { it.id != e.accountId }
                Menu(accounts.firstOrNull { it.id == e.destinationAccountId }?.name?.let { t("Ke %s", it) } ?: t("Pilih akun tujuan"),
                    e.destinationAccountId != null, dst.map { it.name }) { i -> onChange(e.copy(destinationAccountId = dst[i].id)) }
            } else {
                val cats = categories.filter { it.type == (if (e.type == TxType.INCOME) "INCOME" else "EXPENSE") }
                Menu(cats.firstOrNull { it.id == e.categoryId }?.name ?: t("Pilih kategori"), e.categoryId != null, cats.map { it.name }) { i ->
                    onChange(e.copy(categoryId = cats[i].id))
                }
            }
        }
        VSpace(6)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Menu(accounts.firstOrNull { it.id == e.accountId }?.name?.let { t("Akun: %s", it) } ?: t("Pilih akun"), e.accountId != null,
                accounts.map { it.name }) { i -> onChange(e.copy(accountId = accounts[i].id)) }
            if (item.duplicateOf != null) Text(t("Mungkin duplikat"), style = MaterialTheme.typography.labelSmall, color = c.expense)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onDismiss) { Text(t("Buang"), color = c.muted) }
            TextButton(onSave) { Text(t("Simpan")) }
        }
    }
}

@Composable
private fun Menu(label: String, set: Boolean, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Chip(label, set, { open = true })
        DropdownMenu(open, { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem({ Text(o) }, { onPick(i); open = false }) }
        }
    }
}

@Composable
private fun EditDialog(e: InboxEdit, onDismiss: () -> Unit, onSave: (InboxEdit) -> Unit) {
    var amount by remember { mutableStateOf(e.amount.toString()) }
    var name by remember { mutableStateOf(e.merchant ?: e.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Ubah transaksi")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(t("Nominal"), amount, { amount = it })
                OutlinedTextField(name, { name = it }, label = { Text(t("Toko / keterangan")) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton({
                val v = amount.toLongOrNull() ?: return@TextButton
                onSave(e.copy(amount = v, merchant = name.ifBlank { null }, description = name.ifBlank { e.description }))
            }) { Text(t("Simpan")) }
        },
        dismissButton = { OutlinedButton(onDismiss) { Text(t("Batal")) } },
    )
}
