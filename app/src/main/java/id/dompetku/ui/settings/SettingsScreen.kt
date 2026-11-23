package id.dompetku.ui.settings

import id.dompetku.core.t

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import id.dompetku.data.backup.BackupManager
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.Routes
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.lock.canUseAppLock
import id.dompetku.ui.theme.LocalDompetkuColors
import id.dompetku.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class PwMode { EXPORT, RESTORE }

@Composable
fun SettingsScreen(nav: NavController) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by container.settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val lock by container.settings.appLock.collectAsState(initial = false)
    val name by container.settings.userName.collectAsState(initial = "")
    var status by remember { mutableStateOf<String?>(null) }
    var pwMode by remember { mutableStateOf<PwMode?>(null) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var wipeStep by remember { mutableStateOf(0) }
    val lockAvailable = remember { canUseAppLock(context) }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { pendingUri = uri; pwMode = PwMode.EXPORT }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; pwMode = PwMode.RESTORE }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("Kembali"), tint = c.text) }
            Text(t("Pengaturan"), style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        SectionCard(Modifier.fillMaxWidth()) {
            var n by remember(name) { mutableStateOf(name) }
            OutlinedTextField(n, { n = it; scope.launch { container.settings.setUserName(it) } },
                label = { Text(t("Nama panggilan")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        SectionCard(Modifier.fillMaxWidth()) {
            Text(t("Tema"), style = MaterialTheme.typography.titleMedium, color = c.text)
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.LIGHT to t("Terang"), ThemeMode.DARK to t("Gelap"), ThemeMode.SYSTEM to t("Ikuti sistem")).forEach { (m, l) ->
                    Chip(l, theme == m, { scope.launch { container.settings.setThemeMode(m) } })
                }
            }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            val english by container.settings.english.collectAsState(initial = false)
            Text(id.dompetku.core.t("Bahasa"), style = MaterialTheme.typography.titleMedium, color = c.text)
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Indonesia", !english, { scope.launch { container.settings.setEnglish(false); id.dompetku.core.I18n.english = false } })
                Chip("English", english, { scope.launch { container.settings.setEnglish(true); id.dompetku.core.I18n.english = true } })
            }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            val daily by container.settings.dailyReminder.collectAsState(initial = true)
            val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(id.dompetku.core.t("Pengingat harian"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(id.dompetku.core.t("Jam 20.00, hanya jika hari itu belum ada transaksi tercatat"),
                        style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Switch(daily, { on ->
                    if (on && android.os.Build.VERSION.SDK_INT >= 33) askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    scope.launch { container.settings.setDailyReminder(on); id.dompetku.capture.DailyReminderWorker.schedule(context, on) }
                })
            }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            val auto by container.settings.autoPrices.collectAsState(initial = true)
            val lastRun by container.settings.lastPriceRun.collectAsState(initial = null)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Harga otomatis"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(
                        "Saham, reksa dana, dan emas Pegadaian diperbarui tiap hari (perlu internet, ±0,3 MB/hari)." +
                            (lastRun?.let { t("\nTerakhir: ") + id.dompetku.ui.formatDateTime(java.time.Instant.ofEpochMilli(it)) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = c.muted,
                    )
                }
                Switch(auto, { on ->
                    scope.launch { container.settings.setAutoPrices(on); id.dompetku.prices.PriceWorker.schedule(context, on) }
                })
            }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Kunci aplikasi"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(if (lockAvailable) t("Sidik jari / wajah / PIN perangkat") else t("Atur kunci layar HP dulu"),
                        style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Switch(lock, { scope.launch { container.settings.setAppLock(it) } }, enabled = lockAvailable || lock)
            }
        }
        SectionCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.CAPTURE_SETUP) }) {
            Text(t("Catat otomatis dari notifikasi bank"), style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(t("wondr, SeaBank, Livin' · akun tiap bank, pengingat, notifikasi belum terbaca"),
                style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        SectionCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.CATEGORIES) }.padding(vertical = 4.dp)) {
                Text(t("Kelola kategori"), style = MaterialTheme.typography.titleMedium, color = c.text)
            }
            Row(Modifier.fillMaxWidth().clickable {
                scope.launch { container.rules.resetLearned(); status = t("Pembelajaran kategori sudah direset.") }
            }.padding(vertical = 8.dp)) {
                Column {
                    Text(t("Reset pembelajaran kategori"), style = MaterialTheme.typography.bodyLarge, color = c.text)
                    Text(t("Lupakan kategori yang dipelajari dari koreksimu saat impor"), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            Text(t("Cadangan"), style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(t("File cadangan terenkripsi dengan kata sandi pilihanmu. Simpan di Google Drive atau tempat aman lain."),
                style = MaterialTheme.typography.bodySmall, color = c.muted)
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button({
                    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                    createDoc.launch("babingepet-backup-$stamp.dmpk")
                }) { Text(t("Buat cadangan")) }
                androidx.compose.material3.OutlinedButton({ openDoc.launch(arrayOf("*/*")) }) { Text(t("Pulihkan")) }
            }
            status?.let { VSpace(8); Text(it, style = MaterialTheme.typography.bodySmall, color = c.text) }
        }
        SectionCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { wipeStep = 1 }.padding(vertical = 4.dp)) {
                Text(t("Hapus semua data"), style = MaterialTheme.typography.titleMedium, color = c.expense)
            }
        }
        Text(t("Babi Ngepet 1.6 · Data hanya tersimpan di perangkat ini."), style = MaterialTheme.typography.bodySmall, color = c.muted)
        VSpace(24)
    }

    pwMode?.let { mode ->
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) pwMode = null },
            title = { Text(if (mode == PwMode.EXPORT) t("Kata sandi cadangan") else t("Pulihkan cadangan")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mode == PwMode.RESTORE) Text(t("Semua data saat ini akan diganti dengan isi cadangan."), color = c.expense)
                    OutlinedTextField(pw, { pw = it; err = null }, label = { Text(t("Kata sandi")) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (mode == PwMode.EXPORT) {
                        OutlinedTextField(pw2, { pw2 = it; err = null }, label = { Text(t("Ulangi kata sandi")) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        Text(t("Jika lupa kata sandi, cadangan tidak bisa dibuka."), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    err?.let { Text(it, color = c.expense) }
                    if (busy) Text(t("Memproses…"), color = c.muted)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val uri = pendingUri ?: return@TextButton
                    when {
                        pw.length < 6 -> err = t("Minimal 6 karakter")
                        mode == PwMode.EXPORT && pw != pw2 -> err = t("Kata sandi tidak sama")
                        else -> scope.launch {
                            busy = true
                            val mgr = BackupManager(container)
                            val r = if (mode == PwMode.EXPORT) mgr.export(context, uri, pw.toCharArray())
                            else mgr.restore(context, uri, pw.toCharArray())
                            busy = false
                            r.onSuccess {
                                status = if (mode == PwMode.EXPORT) t("Cadangan tersimpan.") else t("Data dipulihkan.")
                                pwMode = null
                            }.onFailure { err = it.message ?: t("Gagal") }
                        }
                    }
                }) { Text(if (mode == PwMode.EXPORT) t("Simpan") else t("Pulihkan")) }
            },
            dismissButton = { TextButton({ pwMode = null }, enabled = !busy) { Text(t("Batal")) } },
        )
    }

    if (wipeStep > 0) {
        AlertDialog(
            onDismissRequest = { wipeStep = 0 },
            title = { Text(if (wipeStep == 1) t("Hapus semua data?") else t("Yakin? Ini tidak bisa dibatalkan")) },
            text = { Text(t("Semua akun, transaksi, dan riwayat nilai akan dihapus permanen dari perangkat ini. Buat cadangan dulu jika perlu.")) },
            confirmButton = {
                TextButton({
                    if (wipeStep == 1) wipeStep = 2 else scope.launch {
                        container.wipeAll(); container.settings.setOnboarded(false); wipeStep = 0
                    }
                }) { Text(if (wipeStep == 1) t("Lanjut") else t("Hapus permanen"), color = c.expense) }
            },
            dismissButton = { TextButton({ wipeStep = 0 }) { Text(t("Batal")) } },
        )
    }
}
