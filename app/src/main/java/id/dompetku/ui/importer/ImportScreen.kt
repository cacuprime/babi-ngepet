package id.dompetku.ui.importer

import id.dompetku.core.t

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.dompetku.AppContainer
import id.dompetku.data.db.AttachmentEntity
import id.dompetku.core.Rupiah
import id.dompetku.domain.TxType
import id.dompetku.importer.DraftRow
import id.dompetku.importer.Extractors
import id.dompetku.importer.ImportStage
import id.dompetku.importer.ImportViewModel
import id.dompetku.importer.PdfPasswordRequired
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.MoneyField
import id.dompetku.ui.Routes
import id.dompetku.ui.appViewModel
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.formatDateTime
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import id.dompetku.ui.tx.AccountPicker
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

private data class Picked(val uri: Uri, val mime: String, val name: String)

private fun mimeOf(context: Context, uri: Uri): String = context.contentResolver.getType(uri) ?: when {
    uri.toString().endsWith(".pdf", true) -> "application/pdf"
    uri.toString().endsWith(".xlsx", true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    uri.toString().endsWith(".csv", true) -> "text/csv"
    else -> "image/jpeg"
}

/**
 * Store the file as proof (once), read it, and hand the drafts to the review session.
 * Returns the stored attachment when the PDF needs a password, so the retry reuses it.
 */
private suspend fun process(
    c: AppContainer, context: Context, vm: ImportViewModel, p: Picked, password: String?, stored: AttachmentEntity?,
): AttachmentEntity? {
    vm.reading()
    val att = stored ?: (context.contentResolver.openInputStream(p.uri)?.let { c.attachments.save(it, p.name, p.mime) }
        ?: Result.failure(IllegalStateException(t("File tidak bisa dibuka"))))
        .getOrElse { vm.fail(it.message ?: t("File tidak bisa dibuka")); return null }
    val fileUri = Uri.fromFile(c.attachments.file(att))
    val r = Extractors.drafts(context, fileUri, p.mime, password, Instant.now())
    r.exceptionOrNull()?.let { e ->
        if (e is PdfPasswordRequired) return att // caller asks for the password
        c.attachments.delete(att)
        vm.fail(e.message ?: t("Gagal membaca file")); return null
    }
    val read = r.getOrThrow()
    if (read.drafts.isEmpty()) c.attachments.delete(att)
    val source = if (read.source == "SCREENSHOT" && p.name.startsWith("struk")) "RECEIPT" else read.source
    vm.accept(read.drafts, source, att.id, read.warning)
    return null
}

@Composable
fun ImportScreen(nav: NavController) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = appViewModel(key = "import") { ImportViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    var needPassword by remember { mutableStateOf<Pair<Picked, AttachmentEntity>?>(null) }
    var editing by remember { mutableStateOf<DraftRow?>(null) }

    fun start(p: Picked, password: String? = null, stored: AttachmentEntity? = null) = scope.launch {
        process(container, context, vm, p, password, stored)?.let { needPassword = p to it }
    }

    val cameraFile = remember { File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "struk.jpg") } }
    val cameraUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", cameraFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) start(Picked(cameraUri, "image/jpeg", "struk-${System.currentTimeMillis()}.jpg"))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) start(Picked(uri, mimeOf(context, uri), "screenshot-${System.currentTimeMillis()}.jpg"))
    }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = mimeOf(context, uri)
            val ext = when { mime == "application/pdf" -> "pdf"; mime.contains("sheet") -> "xlsx"; else -> "csv" }
            start(Picked(uri, mime, "dokumen-${System.currentTimeMillis()}.$ext"))
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("Kembali"), tint = c.text) }
            Text(t("Impor transaksi"), style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        when (s.stage) {
            ImportStage.IDLE, ImportStage.ERROR -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("Pilih sumber. Hasilnya bisa kamu periksa dulu sebelum disimpan."), color = c.muted)
                SourceButton(Icons.Outlined.CameraAlt, t("Foto struk / nota"), t("Pakai kamera")) { camera.launch(cameraUri) }
                SourceButton(Icons.Outlined.Image, "Screenshot", t("Bukti transfer, GoPay, OVO, DANA, QRIS")) {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SourceButton(Icons.Outlined.Description, t("Rekening koran / file"), t("PDF, CSV, atau Excel")) {
                    document.launch(arrayOf("application/pdf", "text/csv", "text/comma-separated-values", "text/plain",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                }
                s.message?.let { Text(it, color = c.expense) }
                if (s.stage == ImportStage.ERROR) TextButton({ nav.navigate(Routes.editTx()) }) { Text("Isi manual saja") }
            }
            ImportStage.READING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(); VSpace(12); Text(t("Membaca…"), color = c.muted)
                }
            }
            ImportStage.REVIEW -> {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AccountPicker(t("Akun"), s.accounts, s.accountId) { vm.setAccount(it) }
                        VSpace(4)
                        Text(t("%d transaksi terbaca · %d kemungkinan duplikat", s.rows.size, s.rows.count { it.duplicateOf != null }),
                            style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    items(s.rows, key = { it.key }) { row -> RowCard(row, s.categories, vm) { editing = row } }
                    item { VSpace(8) }
                }
                s.message?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = c.expense) }
                val n = s.rows.count { it.selected }
                Button(
                    onClick = { scope.launch { vm.confirm() } }, enabled = n > 0,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.fab, contentColor = c.onFab),
                ) { Text(t("Simpan %d transaksi", n)) }
            }
            ImportStage.DONE -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("%d transaksi tersimpan", s.saved), style = MaterialTheme.typography.headlineSmall, color = c.text)
                Text(t("Kategori yang kamu ubah sudah diingat untuk impor berikutnya."), color = c.muted)
                Button({ nav.popBackStack() }) { Text(t("Selesai")) }
            }
        }
    }

    needPassword?.let { (p, att) ->
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { needPassword = null; vm.fail("PDF dikunci kata sandi") },
            title = { Text(t("PDF dikunci")) },
            text = {
                Column {
                    Text(t("Masukkan kata sandi PDF ini. Kata sandi hanya dipakai sekali dan tidak disimpan."))
                    VSpace(8)
                    OutlinedTextField(pw, { pw = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            },
            confirmButton = { TextButton({ needPassword = null; start(p, pw, att) }) { Text(t("Buka")) } },
            dismissButton = { TextButton({ needPassword = null; vm.fail(t("PDF dikunci kata sandi")) }) { Text(t("Batal")) } },
        )
    }

    editing?.let { row -> EditRowDialog(row, onDismiss = { editing = null }) { updated -> vm.edit(row.key) { updated }; editing = null } }
}

@Composable
private fun SourceButton(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = LocalDompetkuColors.current
    SectionCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.text)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

@Composable
private fun RowCard(row: DraftRow, categories: List<id.dompetku.data.db.CategoryEntity>, vm: ImportViewModel, onEdit: () -> Unit) {
    val c = LocalDompetkuColors.current
    var menu by remember { mutableStateOf(false) }
    val d = row.draft
    SectionCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(row.selected, { vm.toggle(row.key) })
            Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(d.merchant ?: d.description, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1)
                Text(formatDateTime(d.date), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Text(
                (if (d.type == TxType.EXPENSE) "-" else "+") + Rupiah.full(d.amount),
                style = AmountStyle.copy(fontSize = 15.sp), color = if (d.type == TxType.EXPENSE) c.expense else c.income,
            )
        }
        Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (d.type == TxType.EXPENSE || d.type == TxType.INCOME) {
                Box {
                    val name = row.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
                    Chip(name ?: t("Pilih kategori"), name != null, { menu = true })
                    DropdownMenu(menu, { menu = false }) {
                        categories.filter { it.type == (if (d.type == TxType.INCOME) "INCOME" else "EXPENSE") }.forEach { cat ->
                            DropdownMenuItem({ Text(cat.name) }, { vm.edit(row.key) { it.copy(categoryId = cat.id) }; menu = false })
                        }
                    }
                }
            }
            if (row.duplicateOf != null) Text(t("Mungkin duplikat"), style = MaterialTheme.typography.labelSmall, color = c.expense)
            else if (row.confidence < 0.8f) Text(t("Periksa lagi"), style = MaterialTheme.typography.labelSmall, color = c.muted)
        }
    }
}

@Composable
private fun EditRowDialog(row: DraftRow, onDismiss: () -> Unit, onSave: (DraftRow) -> Unit) {
    var amount by remember { mutableStateOf(row.draft.amount.toString()) }
    var merchant by remember { mutableStateOf(row.draft.merchant ?: row.draft.description) }
    var type by remember { mutableStateOf(row.draft.type) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Ubah transaksi")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t("Pengeluaran"), type == TxType.EXPENSE, { type = TxType.EXPENSE })
                    Chip(t("Pemasukan"), type == TxType.INCOME, { type = TxType.INCOME })
                }
                MoneyField(t("Nominal"), amount, { amount = it })
                OutlinedTextField(merchant, { merchant = it }, label = { Text(t("Toko / keterangan")) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton({
                val v = amount.toLongOrNull() ?: return@TextButton
                val typeChanged = type != row.draft.type
                onSave(row.copy(
                    draft = row.draft.copy(amount = v, merchant = merchant.ifBlank { null }, description = merchant.ifBlank { row.draft.description }, type = type),
                    categoryId = if (typeChanged) null else row.categoryId,
                ))
            }) { Text(t("Simpan")) }
        },
        dismissButton = { OutlinedButton(onDismiss) { Text(t("Batal")) } },
    )
}
