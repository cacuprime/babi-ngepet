package id.dompetku.ui.recovery

import id.dompetku.core.t

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.dompetku.ui.theme.LocalDompetkuColors

/**
 * Shown when the encrypted database can't be opened on this phone, for example after the
 * app data was copied to a new phone without its hardware-bound key. The old data can't be
 * decrypted here, so the way back is: start fresh, then Settings → Pulihkan cadangan.
 */
/** The database exists and the key is fine, but opening failed (e.g. an update step). Never offers a wipe. */
@Composable
fun OpenFailedScreen(onRetry: () -> Unit) {
    val c = LocalDompetkuColors.current
    Column(
        Modifier.fillMaxSize().background(c.background).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text(t("Data belum bisa dibuka"), style = MaterialTheme.typography.headlineSmall, color = c.text)
        Text(
            t("Ada masalah saat membuka data. Datamu TIDAK dihapus dan masih tersimpan di HP ini. ") +
                t("Coba buka ulang aplikasi. Jika tetap gagal, pasang kembali versi sebelumnya dan hubungi pembuat aplikasi."),
            style = MaterialTheme.typography.bodyLarge, color = c.muted,
        )
        Button(onRetry, Modifier.fillMaxWidth()) { Text(t("Coba lagi")) }
    }
}

@Composable
fun RecoveryScreen(onStartFresh: () -> Unit) {
    val c = LocalDompetkuColors.current
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(c.background).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text(t("Data tidak bisa dibuka"), style = MaterialTheme.typography.headlineSmall, color = c.text)
        Text(
            "Database Babi Ngepet di HP ini terkunci dengan kunci yang sudah tidak ada. Biasanya ini terjadi " +
                "setelah pindah HP atau reset keamanan. Data lama tidak bisa dibaca lagi.",
            style = MaterialTheme.typography.bodyLarge, color = c.muted,
        )
        Text(
            t("Mulai baru, lalu buka Pengaturan → Pulihkan untuk memasukkan file cadanganmu."),
            style = MaterialTheme.typography.bodyLarge, color = c.text,
        )
        Button({ confirm = true }, Modifier.fillMaxWidth()) { Text(t("Mulai baru")) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(t("Mulai baru?")) },
            text = { Text(t("Data lama yang tidak terbaca di HP ini akan dihapus. File cadangan di luar aplikasi tidak terpengaruh.")) },
            confirmButton = { TextButton({ confirm = false; onStartFresh() }) { Text(t("Mulai baru"), color = c.expense) } },
            dismissButton = { TextButton({ confirm = false }) { Text(t("Batal")) } },
        )
    }
}
