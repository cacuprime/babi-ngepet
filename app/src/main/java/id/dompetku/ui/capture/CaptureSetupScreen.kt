package id.dompetku.ui.capture

import id.dompetku.core.t

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import id.dompetku.capture.BankApp
import id.dompetku.capture.BankNotificationListener
import id.dompetku.capture.InboxReminderWorker
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.formatDateTime
import id.dompetku.ui.theme.LocalDompetkuColors
import id.dompetku.ui.tx.AccountPicker
import kotlinx.coroutines.launch
import java.time.Instant

/** Guess which account a bank app books to, from account names/providers ("BNI", "SeaBank", "Mandiri"). */
fun guessAccount(app: BankApp, accounts: List<Account>): Long? = accounts.firstOrNull { a ->
    !a.isArchived && a.type != AccountType.INVESTMENT && a.type != AccountType.GOLD &&
        app.accountHints.any { h -> a.name.lowercase().contains(h) || a.provider?.lowercase()?.contains(h) == true }
}?.id

@Composable
fun CaptureSetupScreen(nav: NavController) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(BankNotificationListener.isEnabled(context)) }
    var batteryFree by remember { mutableStateOf(ignoresBattery(context)) }
    LifecycleResumeEffect(Unit) {
        access = BankNotificationListener.isEnabled(context)
        batteryFree = ignoresBattery(context)
        if (access) BankNotificationListener.rebind(context)
        onPauseOrDispose { }
    }
    val accounts by container.accounts.observeAll().collectAsState(initial = emptyList())
    val live = accounts.filter { !it.isArchived }
    val map by container.settings.captureAccounts.collectAsState(initial = null)
    val autoSave by container.settings.captureAutoSave.collectAsState(initial = true)
    val reminder by container.settings.captureReminder.collectAsState(initial = true)
    val last by container.inbox.observeLastCapture().collectAsState(initial = null)
    val unread by container.inbox.observeUnread().collectAsState(initial = emptyList())
    var copied by remember { mutableStateOf<String?>(null) }

    // First visit: fill in the bank → account guesses so the owner only has to check them.
    LaunchedEffect(map, accounts) {
        val m = map ?: return@LaunchedEffect
        if (accounts.isEmpty()) return@LaunchedEffect
        BankApp.entries.filter { it.name !in m }.forEach { app ->
            guessAccount(app, accounts)?.let { container.settings.setCaptureAccount(app.name, it) }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("Kembali"), tint = c.text) }
            Text(t("Catat otomatis dari notifikasi"), style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        Text(
            t("Dompetku membaca notifikasi wondr, SeaBank, dan Livin' saja, lalu mencatat transaksinya. ") +
                t("Semua tetap di HP ini; notifikasi aplikasi lain tidak dibaca."),
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )

        SectionCard(Modifier.fillMaxWidth()) {
            Text(t("Langkah pengaturan (sekali saja)"), style = MaterialTheme.typography.titleMedium, color = c.text)
            VSpace(8)
            Step(1, t("Izinkan akses notifikasi"), t("Cari Babi Ngepet di daftar, lalu aktifkan."), done = access) {
                open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            Step(2, t("Aktifkan Autostart (Xiaomi/POCO)"), t("Supaya tetap jalan setelah HP dinyalakan ulang."), done = null) {
                val miui = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
                if (!open(context, miui)) open(context, appDetails(context))
            }
            Step(3, t("Baterai: Tanpa batasan"), t("Pilih \"Tanpa batasan\" / \"No restrictions\" untuk Babi Ngepet."), done = batteryFree) {
                if (!requestIgnoreBattery(context)) open(context, appDetails(context))
            }
            Step(4, t("Notifikasi bank tetap menyala"), t("Di wondr, SeaBank, dan Livin', pastikan notifikasi transaksi aktif."), done = null, action = null)
            VSpace(4)
            Text(
                if (access) t("Status: aktif") + (last?.let { t(" · terakhir menangkap ") + formatDateTime(Instant.ofEpochMilli(it)) } ?: t(" · belum ada notifikasi bank"))
                else t("Status: belum aktif"),
                style = MaterialTheme.typography.bodySmall, color = if (access) c.income else c.expense,
            )
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Text(t("Akun untuk tiap aplikasi bank"), style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(t("Transaksi dari aplikasi ini dicatat ke akun yang dipilih, jadi saldo tiap akun tetap benar."),
                style = MaterialTheme.typography.bodySmall, color = c.muted)
            VSpace(8)
            BankApp.entries.forEach { app ->
                AccountPicker(app.label, live, map?.get(app.name)) { id -> scope.launch { container.settings.setCaptureAccount(app.name, id) } }
                VSpace(6)
            }
            if (live.isEmpty()) Text(t("Buat akun bank dulu di tab Akun."), color = c.expense, style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Simpan otomatis toko langganan"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(t("Toko yang sudah kamu konfirmasi 2 kali langsung tersimpan dengan kategori yang sama."),
                        style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Switch(autoSave, { scope.launch { container.settings.setCaptureAutoSave(it) } })
            }
            VSpace(8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Pengingat jam 21.00"), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(t("Hanya muncul kalau ada transaksi yang perlu dicek."), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Switch(reminder, { on -> scope.launch { container.settings.setCaptureReminder(on); InboxReminderWorker.schedule(context, on) } })
            }
        }

        SectionCard(Modifier.fillMaxWidth()) {
            Text(t("Notifikasi belum terbaca (%d)", unread.size), style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(
                t("Notifikasi bank yang ada nominalnya tapi belum bisa dibaca. Salin dan kirim contohnya supaya aturannya bisa diperbaiki."),
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
            if (unread.isNotEmpty()) {
                VSpace(8)
                OutlinedButton({
                    val text = unread.joinToString("\n\n") { "[${it.bankApp}] ${it.rawText}" }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("notifikasi", text))
                    copied = "Disalin. Samarkan nomor rekening sebelum dibagikan."
                }) { Text(t("Salin semua")) }
                copied?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.text) }
                unread.take(10).forEach { u ->
                    VSpace(6)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(u.rawText, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.text, maxLines = 3)
                        TextButton({ scope.launch { container.inbox.dismiss(u.id) } }) { Text(t("Hapus")) }
                    }
                }
            }
        }
        VSpace(24)
    }
}

@Composable
private fun Step(n: Int, title: String, sub: String, done: Boolean?, action: (() -> Unit)?) {
    val c = LocalDompetkuColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        when (done) {
            true -> Icon(Icons.Outlined.CheckCircle, t("Selesai"), tint = c.income)
            false -> Icon(Icons.Outlined.ErrorOutline, t("Belum"), tint = c.expense)
            null -> Text("$n.", color = c.muted)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (action != null) TextButton(action) { Text(t("Buka")) }
    }
}

private fun open(context: Context, intent: Intent): Boolean =
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))

private fun ignoresBattery(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

@SuppressLint("BatteryLife")
private fun requestIgnoreBattery(context: Context): Boolean =
    open(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)))

