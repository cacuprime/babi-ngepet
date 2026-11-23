package id.dompetku.ui.voice

import id.dompetku.core.t

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dompetku.core.Rupiah
import id.dompetku.data.db.CategorizationRuleEntity
import id.dompetku.domain.AccountType
import id.dompetku.domain.JAKARTA
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import id.dompetku.importer.Categorizer
import id.dompetku.importer.Draft
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.MoneyField
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.formatDay
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import id.dompetku.voice.VoiceItem
import id.dompetku.voice.VoiceParser
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private data class VRow(val item: VoiceItem, val categoryId: Long?)

/**
 * Speak → check → save. Uses the phone's speech recogniser (Indonesian); the sentence is read
 * locally by [VoiceParser]. Nothing is saved until "Simpan".
 */
@Composable
fun VoiceEntry(onClose: () -> Unit, onSaved: () -> Unit = onClose, typed: Boolean = false) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val accounts by container.accounts.observeAll().collectAsState(initial = null)
    val categories by container.categories.observeAll().collectAsState(initial = null)
    var rules by remember { mutableStateOf<List<CategorizationRuleEntity>?>(null) }
    var sentence by remember { mutableStateOf("") }
    val rows = remember { mutableStateListOf<VRow>() }
    var message by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }
    var editingAmount by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { rules = container.rules.all() }

    fun read(text: String) {
        val accs = accounts ?: return
        val cats = categories ?: return
        val live = accs.filter { !it.isArchived }
        val default = live.firstOrNull { it.type == AccountType.CASH }?.id ?: live.firstOrNull()?.id
        // Typed list: one transaction per line.
        val r = VoiceParser.parse(text.lines().filter { it.isNotBlank() }.joinToString(", "), LocalDate.now(JAKARTA), live, default)
        val categorizer = Categorizer(rules.orEmpty(), cats)
        rows.clear()
        r.items.forEach { i ->
            val s = categorizer.suggest(Draft(i.type, i.amount, Instant.now(), i.description, i.description, null, null, 1f))
            rows += VRow(i, s?.categoryId)
        }
        message = r.error?.let { t(it) }
    }

    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        listening = false
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) { sentence = text; read(text) }
        else if (rows.isEmpty() && sentence.isBlank()) message = t("Tidak ada suara yang tertangkap. Ketuk \"Bicara\" untuk mencoba lagi, atau ketik kalimatnya.")
    }

    fun listen() {
        listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Contoh: bakso 25 ribu pakai cash")
        try { recognizer.launch(intent) } catch (_: ActivityNotFoundException) {
            listening = false
            message = t("Pengenal suara Google tidak tersedia di HP ini. Ketik kalimatnya di kotak di atas.")
        }
    }

    // Start listening as soon as the data needed to read the sentence is loaded.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(accounts, categories, rules) {
        if (!started && !typed && accounts != null && categories != null && rules != null) { started = true; listen() }
    }

    val live = accounts.orEmpty().filter { !it.isArchived }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (typed) id.dompetku.core.t("Tambah banyak sekaligus") else id.dompetku.core.t("Catat pakai suara")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sentence, { sentence = it },
                    label = { Text(if (listening) t("Mendengarkan…") else if (typed) id.dompetku.core.t("Satu transaksi per baris") else t("Kalimat")) },
                    placeholder = if (typed) ({ Text(t("bakso 25rb\nparkir 5000 pakai cash\ngaji 8 juta ke BSI")) }) else null,
                    minLines = if (typed) 4 else 1, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!typed) OutlinedButton({ listen() }) { Text(t("Bicara")) }
                    OutlinedButton({ read(sentence) }, enabled = sentence.isNotBlank()) { Text(if (typed) id.dompetku.core.t("Baca") else t("Baca ulang")) }
                }
                message?.let { Text(it, color = c.expense, style = MaterialTheme.typography.bodySmall) }
                rows.forEachIndexed { idx, r ->
                    val i = r.item
                    SectionCard(Modifier.fillMaxWidth()) {
                        Row {
                            Text(i.description, Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.bodyLarge)
                            Text((if (i.type == TxType.INCOME) "+" else "-") + Rupiah.full(i.amount),
                                Modifier.clickable { editingAmount = idx },
                                style = AmountStyle.copy(fontSize = 15.sp), color = if (i.type == TxType.INCOME) c.income else c.expense)
                        }
                        Text(formatDay(i.date), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        VSpace(6)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val cats = categories.orEmpty().filter { it.type == i.type.name }
                            Menu(cats.firstOrNull { it.id == r.categoryId }?.name ?: t("Pilih kategori"), r.categoryId != null, cats.map { it.name }) { k ->
                                rows[idx] = r.copy(categoryId = cats[k].id)
                            }
                            Menu(live.firstOrNull { it.id == i.accountId }?.name ?: t("Pilih akun"), i.accountId != null, live.map { it.name }) { k ->
                                rows[idx] = r.copy(item = i.copy(accountId = live[k].id))
                            }
                        }
                        Row {
                            Menu(if (i.type == TxType.INCOME) t("Pemasukan") else t("Pengeluaran"), true, listOf(t("Pengeluaran"), t("Pemasukan"))) { k ->
                                val t = if (k == 0) TxType.EXPENSE else TxType.INCOME
                                if (t != i.type) rows[idx] = VRow(i.copy(type = t), null)
                            }
                            TextButton({ rows.removeAt(idx) }) { Text(t("Hapus"), color = c.muted) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = rows.isNotEmpty() && !busy, onClick = {
                val missing = rows.indexOfFirst { it.categoryId == null || it.item.accountId == null }
                if (missing >= 0) { message = t("Pilih kategori dan akun untuk \"%s\"", rows[missing].item.description); return@TextButton }
                scope.launch {
                    busy = true
                    var saved = 0
                    for (r in rows.toList()) {
                        val i = r.item
                        val at = if (i.date == LocalDate.now(JAKARTA)) Instant.now()
                        else i.date.atTime(LocalTime.NOON).atZone(JAKARTA).toInstant()
                        container.transactions.save(Tx(0, i.type, i.amount, r.categoryId, i.accountId!!, null, null, at, i.description, null, null), "VOICE")
                            .onSuccess { saved++; runCatching { container.rules.confirm(i.description, r.categoryId!!) } }
                            .onFailure { message = it.message }
                    }
                    busy = false
                    if (saved == rows.size) onSaved() else { repeat(saved) { rows.removeAt(0) } }
                }
            }) { Text(if (rows.size > 1) t("Simpan %d", rows.size) else t("Simpan")) }
        },
        dismissButton = { TextButton({ onClose() }, enabled = !busy) { Text(t("Batal")) } },
    )

    editingAmount?.let { idx ->
        val r = rows.getOrNull(idx) ?: run { editingAmount = null; return@let }
        var digits by remember(idx) { mutableStateOf(r.item.amount.toString()) }
        AlertDialog(
            onDismissRequest = { editingAmount = null },
            title = { Text(t("Ubah nominal")) },
            text = { MoneyField(t("Nominal"), digits, { digits = it }) },
            confirmButton = {
                TextButton({
                    digits.toLongOrNull()?.takeIf { it > 0 }?.let { rows[idx] = r.copy(item = r.item.copy(amount = it)) }
                    editingAmount = null
                }) { Text("OK") }
            },
        )
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
