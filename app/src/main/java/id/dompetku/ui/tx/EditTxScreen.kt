package id.dompetku.ui.tx

import id.dompetku.core.t

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.dompetku.core.Rupiah
import id.dompetku.domain.Account
import id.dompetku.domain.JAKARTA
import id.dompetku.domain.TxType
import id.dompetku.ui.appViewModel
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.formatDateTime
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTxScreen(nav: NavController, txId: Long) {
    val c = LocalDompetkuColors.current
    val vm = appViewModel(key = "tx$txId") { EditTxViewModel(it, txId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val container = id.dompetku.ui.LocalContainer.current
    val focus = LocalFocusManager.current
    val hideOnFocus = Modifier.onFocusChanged { if (it.isFocused) vm.hideKeypad() }
    // popBackStack(route, inclusive) is a no-op once this screen is gone, so a late second
    // tap can never pop Home and leave an empty screen.
    val route = "tx/edit/{id}"

    LaunchedEffect(txId) { if (!vm.state.value.loaded) vm.load() }
    var voiceOpen by remember { mutableStateOf(false) }
    if (voiceOpen) id.dompetku.ui.voice.VoiceEntry(onClose = { voiceOpen = false }, onSaved = { voiceOpen = false; nav.popBackStack() })

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.Outlined.Close, t("Tutup"), tint = c.text) }
            Text(if (s.isEdit) t("Ubah transaksi") else t("Transaksi baru"), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, color = c.text)
            if (s.isEdit) IconButton({ confirmDelete = true }) { Icon(Icons.Outlined.Delete, t("Hapus"), tint = c.expense) }
            else IconButton({ voiceOpen = true }) { Icon(Icons.Outlined.Mic, t("Catat pakai suara"), tint = c.text) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TxType.entries.forEach { t -> Chip(t.label, s.type == t, { vm.setType(t) }) }
            }
            if (!s.isEdit) {
                androidx.compose.material3.OutlinedButton({ nav.navigate(id.dompetku.ui.Routes.IMPORT) }, Modifier.fillMaxWidth()) {
                    Text(t("📷 Foto struk / 📎 Lampirkan bukti"))
                }
            }
            s.attachmentId?.let { attId ->
                androidx.compose.material3.OutlinedButton({
                    scope.launch {
                        val a = container.attachments.get(attId) ?: return@launch
                        // No viewer app installed, or a file that can't be shared, must not crash the app.
                        runCatching {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.files", container.attachments.file(a),
                            )
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, a.fileType)
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        }.onFailure {
                            android.widget.Toast.makeText(context, t("Tidak ada aplikasi untuk membuka file ini"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }, Modifier.fillMaxWidth()) { Text(t("Lihat bukti transaksi")) }
            }

            val sign = when {
                s.type == TxType.EXPENSE -> "-"
                s.type == TxType.INCOME -> "+"
                s.type == TxType.ADJUSTMENT && s.adjustmentNegative -> "-"
                s.type == TxType.ADJUSTMENT -> "+"
                else -> ""
            }
            if (s.amount.hasOperator) {
                Text(s.amount.digits.replace("-", " − ").replace("+", " + ").replace("×", " × ").replace("÷", " ÷ "),
                    Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
            Text(
                sign + Rupiah.full(s.amount.value),
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (s.keypadVisible) c.card else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { focus.clearFocus(); vm.showKeypad() }.padding(vertical = 8.dp),
                style = AmountStyle.copy(fontSize = 40.sp, textAlign = TextAlign.Center),
                color = when (s.type) { TxType.INCOME -> c.income; TxType.EXPENSE -> c.expense; else -> c.text },
            )
            if (s.type == TxType.ADJUSTMENT) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Chip(t("Tambah saldo"), !s.adjustmentNegative, { if (s.adjustmentNegative) vm.toggleAdjustmentSign() })
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    Chip(t("Kurangi saldo"), s.adjustmentNegative, { if (!s.adjustmentNegative) vm.toggleAdjustmentSign() })
                }
            } else if (!s.keypadVisible) {
                Text(t("Ketuk nominal untuk mengubah"), Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }

            AccountPicker(if (s.type == TxType.TRANSFER) t("Dari akun") else t("Akun"), s.accounts, s.sourceAccountId) { vm.setSource(it) }
            if (s.showDestination) {
                AccountPicker(t("Ke akun"), s.accounts.filter { it.id != s.sourceAccountId }, s.destinationAccountId) { vm.setDestination(it) }
            }
            if (s.showGold) {
                OutlinedTextField(s.goldGrams, { vm.setGoldGrams(it) }, label = { Text(t("Gram emas (opsional)")) }, modifier = hideOnFocus.fillMaxWidth(),
                    placeholder = { Text("0,4300") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }

            if (s.showCategory) {
                Text(t("Kategori"), style = MaterialTheme.typography.labelLarge, color = c.muted)
                val selected = s.categories.firstOrNull { it.id == s.categoryId }
                val parentId = selected?.parentId ?: selected?.id
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.categories.filter { it.parentId == null }.forEach { cat -> Chip(cat.name, parentId == cat.id, { vm.setCategory(cat.id) }) }
                }
                val children = s.categories.filter { it.parentId != null && it.parentId == parentId }
                if (children.isNotEmpty()) {
                    Text(t("Subkategori"), style = MaterialTheme.typography.labelMedium, color = c.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        children.forEach { cat -> Chip(cat.name, s.categoryId == cat.id, { vm.setCategory(cat.id) }) }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { details = !details }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t("Detail · %s", formatDateTime(s.date)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text)
                Icon(if (details) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = c.muted)
            }
            if (details) {
                OutlinedButtonRow(t("Ubah tanggal & jam")) {
                    val z = s.date.atZone(JAKARTA)
                    DatePickerDialog(context, { _, y, m, d ->
                        TimePickerDialog(context, { _, h, min ->
                            vm.setDate(z.withYear(y).withMonth(m + 1).withDayOfMonth(d).withHour(h).withMinute(min).toInstant())
                        }, z.hour, z.minute, true).show()
                    }, z.year, z.monthValue - 1, z.dayOfMonth).show()
                }
                if (s.type == TxType.EXPENSE || s.type == TxType.INCOME) {
                    OutlinedTextField(s.merchant, vm::setMerchant, label = { Text(if (s.type == TxType.EXPENSE) t("Toko / penerima") else t("Dari")) }, modifier = hideOnFocus.fillMaxWidth(),
                        singleLine = true)
                }
                OutlinedTextField(s.description, vm::setDescription, label = { Text(t("Keterangan")) }, singleLine = true, modifier = hideOnFocus.fillMaxWidth())
                OutlinedTextField(s.notes, vm::setNotes, label = { Text(t("Catatan")) }, modifier = hideOnFocus.fillMaxWidth())
                OutlinedTextField(s.tags, vm::setTags, label = { Text(t("Tag")) }, placeholder = { Text(t("#liburan #bali")) },
                    singleLine = true, modifier = hideOnFocus.fillMaxWidth())
            }
            s.error?.let { Text(it, color = c.expense, style = MaterialTheme.typography.bodyMedium) }
            VSpace(8)
        }

        AnimatedVisibility(
            s.keypadVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Keypad(onDigit = vm::press, on000 = vm::press000, onBack = vm::backspace, onDone = { vm.evaluate(); vm.hideKeypad() },
                onOperator = vm::operator, onEquals = vm::evaluate)
        }
        Button(
            onClick = {
                scope.launch {
                    if (!vm.save()) return@launch
                    val dest = vm.state.value.destinationAccountId.takeIf { vm.state.value.type == TxType.TRANSFER }
                    val holdingsAcct = dest?.takeIf { d -> container.holdings.all().any { it.accountId == d } }
                    vm.overBudget?.let { o ->
                        android.widget.Toast.makeText(context, t("Pos %s sudah %d%% dari budget", t(o.bucket.label), o.percent), android.widget.Toast.LENGTH_LONG).show()
                    }
                    nav.popBackStack(route, inclusive = true)
                    if (holdingsAcct != null) {
                        // New money in an account with holdings: units must be updated or the next daily price run undercounts it.
                        android.widget.Toast.makeText(context, t("Jangan lupa perbarui jumlah unit/lot yang kamu beli"), android.widget.Toast.LENGTH_LONG).show()
                        nav.navigate(id.dompetku.ui.Routes.account(holdingsAcct))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.fab, contentColor = c.onFab),
        ) { Text(t("Simpan"), style = MaterialTheme.typography.titleMedium) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Hapus transaksi?")) },
            text = { Text(t("Saldo akun akan dihitung ulang tanpa transaksi ini.")) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    scope.launch { vm.delete(); if (vm.done) nav.popBackStack(route, inclusive = true) }
                }) {
                    Text(t("Hapus"), color = c.expense)
                }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(t("Batal")) } },
        )
    }
}

@Composable
private fun OutlinedButtonRow(text: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick, Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
fun AccountPicker(label: String, accounts: List<Account>, selected: Long?, onSelect: (Long) -> Unit) {
    val c = LocalDompetkuColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        SectionCard(Modifier.fillMaxWidth(), onClick = { open = true }) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
            Text(accounts.firstOrNull { it.id == selected }?.name ?: t("Pilih akun"),
                style = MaterialTheme.typography.bodyLarge, color = c.text)
        }
        DropdownMenu(open, { open = false }) {
            accounts.forEach { a ->
                DropdownMenuItem(text = { Text(a.name) }, onClick = { onSelect(a.id); open = false })
            }
        }
    }
}

@Composable
fun Keypad(
    onDigit: (Char) -> Unit, on000: () -> Unit, onBack: () -> Unit, onDone: () -> Unit,
    onOperator: (Char) -> Unit = {}, onEquals: () -> Unit = {},
) {
    val c = LocalDompetkuColors.current
    // Calculator column on the right: ÷ × − +, so "25000+12500" can be typed straight in.
    val rows = listOf("123" to '÷', "456" to '×', "789" to '-')
    Column(Modifier.fillMaxWidth().background(c.card).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onEquals) { Text("=", color = c.text) }
            TextButton(onDone) { Text(t("Selesai"), color = c.text) }
        }
        rows.forEach { (r, op) ->
            Row {
                r.forEach { d -> Key(d.toString(), Modifier.weight(1f)) { onDigit(d) } }
                Key(if (op == '-') "−" else op.toString(), Modifier.weight(1f), muted = true) { onOperator(op) }
            }
        }
        Row {
            Key("000", Modifier.weight(1f), onClick = on000)
            Key("0", Modifier.weight(1f)) { onDigit('0') }
            Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, t("Hapus angka"), tint = c.text)
            }
            Key("+", Modifier.weight(1f), muted = true) { onOperator('+') }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, muted: Boolean = false, onClick: () -> Unit) {
    val c = LocalDompetkuColors.current
    Box(modifier.height(52.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = AmountStyle.copy(fontSize = 22.sp), color = if (muted) c.muted else c.text)
    }
}
