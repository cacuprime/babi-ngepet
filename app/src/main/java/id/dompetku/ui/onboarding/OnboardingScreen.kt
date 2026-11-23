package id.dompetku.ui.onboarding

import id.dompetku.core.t

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.dompetku.core.Grams
import id.dompetku.core.Rupiah
import id.dompetku.data.StartingAccount
import id.dompetku.data.StartingData
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.accounts.AccountForm
import id.dompetku.ui.accounts.AccountFormState
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.CircleIcon
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.components.accountIcon
import id.dompetku.ui.lock.canUseAppLock
import id.dompetku.ui.theme.LocalDompetkuColors
import id.dompetku.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun OnboardingScreen() {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Saveable so rotating the phone doesn't send the owner back and re-add accounts.
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var accountsCreated by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val starting = remember { StartingData.load(context) }
    val added = remember { mutableStateListOf<StartingAccount>() }
    var form by remember { mutableStateOf<AccountFormState?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    val theme by container.settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    var lock by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step++ }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(t("Langkah %d dari 5", step + 1), style = MaterialTheme.typography.labelMedium, color = c.muted)
        when (step) {
            0 -> {
                CircleIcon(Icons.Outlined.AccountBalanceWallet, c.accent, c.onHero, size = 64)
                Text(t("Selamat datang di Babi Ngepet"), style = MaterialTheme.typography.headlineMedium, color = c.text)
                Text(
                    t("Semua data tersimpan di HP ini dan terenkripsi. Tidak perlu akun, tidak perlu internet."),
                    style = MaterialTheme.typography.bodyLarge, color = c.muted,
                )
                OutlinedTextField(name, { name = it }, label = { Text(t("Nama panggilan (opsional)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                PrimaryButton(t("Mulai")) { scope.launch { container.settings.setUserName(name) }; step = 1 }
            }
            1 -> {
                Text(t("Akun dan saldo awal"), style = MaterialTheme.typography.headlineSmall, color = c.text)
                Text(t("Masukkan saldo setiap rekening, e-wallet, dan investasi saat ini."),
                    style = MaterialTheme.typography.bodyMedium, color = c.muted)
                if (starting != null && added.isEmpty() && form == null && !accountsCreated) {
                    SectionCard {
                        Text(t("Data awal tersedia (%d akun)", starting.size), style = MaterialTheme.typography.titleMedium, color = c.text)
                        VSpace(4)
                        Text(t("Total %s", Rupiah.full(starting.sumOf { it.value ?: it.balance })),
                            style = MaterialTheme.typography.bodyMedium, color = c.muted)
                        VSpace(12)
                        PrimaryButton(t("Pakai data awal")) { added.addAll(starting) }
                    }
                }
                added.forEachIndexed { i, a ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircleIcon(accountIcon(a.type), c.background, c.text)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(a.name, style = MaterialTheme.typography.bodyLarge, color = c.text)
                                val detail = a.goldTenThousandths?.let { Grams.format(it) + " · " } ?: ""
                                Text(detail + Rupiah.full(a.value ?: a.balance), style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                            TextButton({ added.removeAt(i) }) { Text(t("Hapus")) }
                        }
                    }
                }
                val f = form
                if (f != null) {
                    SectionCard {
                        AccountForm(f, { form = it; formError = null })
                        formError?.let { VSpace(8); Text(it, color = c.expense, style = MaterialTheme.typography.bodySmall) }
                        VSpace(12)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ form = null }) { Text(t("Batal")) }
                            Button({
                                val (acc, err) = f.build()
                                if (acc != null) { added.add(acc); form = null } else formError = err
                            }) { Text(t("Tambah")) }
                        }
                    }
                } else {
                    OutlinedButton({ form = AccountFormState() }, Modifier.fillMaxWidth()) { Text(t("Tambah akun")) }
                }
                PrimaryButton(if (added.isEmpty()) t("Lewati, tambah nanti") else t("Lanjut"), enabled = !saving) {
                    if (saving) return@PrimaryButton
                    saving = true
                    val toCreate = added.toList()
                    added.clear()
                    scope.launch {
                        val now = Instant.now()
                        toCreate.forEach { StartingData.createAccount(container, it, now) }
                        accountsCreated = true
                        saving = false
                        step = 2
                    }
                }
            }
            2 -> {
                Text(t("Tampilan"), style = MaterialTheme.typography.headlineSmall, color = c.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.LIGHT to t("Terang"), ThemeMode.DARK to t("Gelap"), ThemeMode.SYSTEM to t("Ikuti sistem")).forEach { (m, l) ->
                        Chip(l, theme == m, { scope.launch { container.settings.setThemeMode(m) } })
                    }
                }
                PrimaryButton(t("Lanjut")) { step = 3 }
            }
            3 -> {
                Text(t("Notifikasi"), style = MaterialTheme.typography.headlineSmall, color = c.text)
                Text(t("Izinkan notifikasi untuk pengingat tagihan dan laporan mingguan (fitur berikutnya)."),
                    style = MaterialTheme.typography.bodyMedium, color = c.muted)
                PrimaryButton(t("Izinkan notifikasi")) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else step = 4
                }
                TextButton({ step = 4 }) { Text(t("Nanti saja")) }
            }
            else -> {
                Text(t("Kunci aplikasi"), style = MaterialTheme.typography.headlineSmall, color = c.text)
                val available = remember { canUseAppLock(context) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (available) t("Minta sidik jari / PIN saat membuka Babi Ngepet")
                        else t("Atur kunci layar HP dulu untuk memakai fitur ini"),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text,
                    )
                    Switch(lock, { lock = it }, enabled = available)
                }
                PrimaryButton(t("Selesai")) {
                    scope.launch {
                        container.settings.setAppLock(lock)
                        container.settings.setOnboarded(true)
                    }
                }
            }
        }
        VSpace(24)
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalDompetkuColors.current
    Button(
        onClick, Modifier.fillMaxWidth(), enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = c.fab, contentColor = c.onFab),
    ) { Text(text, Modifier.padding(vertical = 6.dp)) }
}
