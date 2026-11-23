package id.dompetku.ui.accounts

import id.dompetku.core.t

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dompetku.core.Rupiah
import id.dompetku.core.Units
import id.dompetku.data.db.HoldingEntity
import id.dompetku.domain.JAKARTA
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import id.dompetku.prices.Bonds
import id.dompetku.prices.FundCatalog
import id.dompetku.prices.FundRef
import id.dompetku.prices.PriceUpdater
import id.dompetku.prices.UrlHttp
import id.dompetku.prices.Valuation
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.MoneyField
import id.dompetku.ui.components.AmountText
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.formatDateTime
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

private data class HoldingDraft(
    val id: Long, val kind: String, val code: String, val name: String, val amount: String, val cost: String,
)

private val ID = Locale("id", "ID")
private fun pct(p: Double) = String.format(ID, "%+.2f", p) + "%"

private fun unitsLabel(h: HoldingEntity) = when (h.kind) {
    "STOCK" -> "${h.unitsE4 / 10_000 / 100} lot"
    else -> "${Units.plain(h.unitsE4)} unit"
}

private fun title(h: HoldingEntity) = if (h.kind == "FUND") h.name else h.code

/** Coupon rate and maturity of the owner's bond series, saved by the daily price run. */
private fun readBondInfo(dir: File): Map<String, Pair<Double, LocalDate>> = runCatching {
    File(dir, "bond_info.txt").readLines().mapNotNull { l ->
        l.split(";").takeIf { it.size == 3 }?.let { p -> p[0] to (p[1].toDouble() to LocalDate.parse(p[2])) }
    }.toMap()
}.getOrDefault(emptyMap())

/**
 * "Isi portofolio" on an investment account: stocks (lots), funds (units) and bonds (units of Rp1 juta),
 * priced daily; modal per holding gives profit, and money put in − modal gives the RDN cash balance.
 */
@Composable
fun HoldingsSection(accountId: Long) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holdings by remember(accountId) { container.holdings.observe(accountId) }.collectAsState(initial = emptyList())
    val lastRun by container.settings.lastPriceRun.collectAsState(initial = null)
    val views by remember { container.dashboard.accountViews() }.collectAsState(initial = emptyList())
    val ledgerBalance = views.firstOrNull { it.account.id == accountId }?.balance
    val bondInfo = remember(holdings, lastRun) { readBondInfo(context.filesDir) }
    var draft by remember { mutableStateOf<HoldingDraft?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var correcting by remember { mutableStateOf(false) }

    Text(t("Isi portofolio"), style = MaterialTheme.typography.titleMedium, color = c.text)
    Text(
        t("Nilai dihitung otomatis setiap hari dari harga terbaru (saham: Yahoo Finance, reksa dana & obligasi: Pasardana)."),
        style = MaterialTheme.typography.bodySmall, color = c.muted,
    )

    if (holdings.isNotEmpty() && ledgerBalance != null) {
        val rdn = Valuation.rdnCash(ledgerBalance, holdings)
        SectionCard(Modifier.fillMaxWidth()) {
            if (rdn == null) {
                Text(t("Isi modal semua investasi untuk menghitung saldo RDN dan keuntungan."),
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Saldo RDN (dana siap investasi)"), style = MaterialTheme.typography.bodyMedium, color = c.text)
                        Text(t("Dana masuk − modal semua investasi"), style = MaterialTheme.typography.labelSmall, color = c.muted)
                    }
                    AmountText(rdn, style = AmountStyle.copy(fontSize = 15.sp), color = if (rdn < 0) c.expense else c.text)
                }
                if (rdn < 0) Text(t("Minus: cek modal, atau koreksi saldo RDN sesuai Bibit."),
                    style = MaterialTheme.typography.labelSmall, color = c.expense)
                TextButton({ correcting = true }) { Text(t("Koreksi saldo RDN")) }
                if (holdings.all { it.priceE4 != null }) {
                    val value = holdings.sumOf { Valuation.holdingValue(it.unitsE4, it.priceE4!!) }
                    val (gain, p) = Valuation.profit(value, holdings.sumOf { it.costRp!! })
                    Text(t("Keuntungan investasi: %s", Rupiah.signed(gain) + (p?.let { " (${pct(it)})" } ?: "")),
                        style = MaterialTheme.typography.bodyMedium, color = if (gain >= 0) c.income else c.expense)
                }
            }
        }
        if (correcting && rdn != null) {
            RdnCorrectionDialog(rdn, onDismiss = { correcting = false }) { newRdn ->
                scope.launch {
                    // Records the difference as a balance adjustment on this account (traceable, undoable).
                    container.transactions.save(Tx(0, TxType.ADJUSTMENT, newRdn - rdn, null, accountId, null, null,
                        Instant.now(), "Koreksi saldo RDN", null, null))
                    correcting = false
                }
            }
        }
    }

    SectionCard(Modifier.fillMaxWidth()) {
        if (holdings.isEmpty()) Text(t("Belum ada. Tambahkan saham, reksa dana, atau obligasi yang kamu punya."), color = c.muted)
        holdings.forEach { h ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    val amount = if (h.kind == "STOCK") (h.unitsE4 / 10_000 / 100).toString() else Units.plain(h.unitsE4)
                    draft = HoldingDraft(h.id, h.kind, h.code, h.name, amount, h.costRp?.toString() ?: "")
                }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title(h), style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1)
                    val price = h.priceE4?.let { " · @" + Rupiah.full(it / 10_000) } ?: ""
                    Text(unitsLabel(h) + price, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    h.priceError?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.expense) }
                    val cost = h.costRp
                    if (h.priceE4 != null && cost != null) {
                        val (gain, p) = Valuation.profit(Valuation.holdingValue(h.unitsE4, h.priceE4), cost)
                        Text(Rupiah.signed(gain) + (p?.let { " (${pct(it)})" } ?: ""),
                            style = MaterialTheme.typography.labelSmall, color = if (gain >= 0) c.income else c.expense)
                    } else if (cost == null) {
                        Text(t("Modal belum diisi"), style = MaterialTheme.typography.labelSmall, color = c.muted)
                    }
                    if (h.kind == "BOND") bondInfo[h.code.uppercase()]?.let { (rate, maturity) ->
                        val nominal = h.unitsE4 / 10_000 * Bonds.UNIT_FACE_RP
                        val accrued = Bonds.accruedCoupon(nominal, rate, maturity, LocalDate.now(JAKARTA))
                        Text(t("Kupon berjalan ±%s · %s%%/thn · jatuh tempo %s", Rupiah.full(accrued), String.format(ID, "%.3f", rate * 100), maturity),
                            style = MaterialTheme.typography.labelSmall, color = c.muted)
                    }
                }
                h.priceE4?.let { AmountText(Valuation.holdingValue(h.unitsE4, it), style = AmountStyle.copy(fontSize = 14.sp), color = c.text) }
                    ?: Text(t("menunggu harga"), style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
        }
        VSpace(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ draft = HoldingDraft(0, "STOCK", "", "", "", "") }) { Text(t("Tambah")) }
            Button(enabled = !running && holdings.isNotEmpty(), onClick = {
                running = true
                scope.launch {
                    val r = PriceUpdater(container, UrlHttp()).run()
                    status = when {
                        r.skipped -> t("Harga otomatis dimatikan di Pengaturan.")
                        r.errors.isEmpty() -> t("Harga diperbarui.")
                        else -> r.errors.joinToString("\n")
                    }
                    running = false
                }
            }) { Text(if (running) t("Memperbarui…") else t("Perbarui sekarang")) }
        }
        lastRun?.let { Text(t("Terakhir diperbarui %s", formatDateTime(Instant.ofEpochMilli(it))), style = MaterialTheme.typography.labelSmall, color = c.muted) }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.text) }
    }

    draft?.let { d -> HoldingForm(accountId, d, holdings.firstOrNull { it.id == d.id && d.id != 0L }, onChange = { draft = it }, onClose = { draft = null }) }
}

private class PendingSale(val label: String, val modalRemoved: Long, val apply: suspend () -> Unit)

@Composable
private fun SaleDialog(label: String, onDismiss: () -> Unit, onDone: (proceeds: Long?) -> Unit) {
    var digits by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Apakah ini penjualan?")) },
        text = {
            Column {
                Text(t("Kalau kamu menjual %s, isi total hasil penjualan (uang yang masuk ke RDN) supaya keuntungannya tetap terhitung.", label))
                VSpace(8)
                MoneyField(t("Hasil penjualan"), digits, { digits = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onDone(digits.toLongOrNull() ?: 0L) }) { Text(t("Simpan penjualan")) } },
        dismissButton = { TextButton({ onDone(null) }) { Text(t("Bukan penjualan")) } },
    )
}

@Composable
private fun HoldingForm(accountId: Long, d: HoldingDraft, existing: HoldingEntity?, onChange: (HoldingDraft) -> Unit, onClose: () -> Unit) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var funds by remember(d.id) { mutableStateOf<List<FundRef>?>(null) }
    var query by remember(d.id) { mutableStateOf(if (d.kind == "FUND") d.name else "") }
    var error by remember(d.id) { mutableStateOf<String?>(null) }
    var sale by remember(d.id) { mutableStateOf<PendingSale?>(null) }

    SectionCard(Modifier.fillMaxWidth()) {
        Text(if (d.id == 0L) t("Tambah isi portofolio") else t("Ubah"), style = MaterialTheme.typography.titleMedium, color = c.text)
        VSpace(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t("Saham"), d.kind == "STOCK", { onChange(d.copy(kind = "STOCK", code = "", name = "")) })
            Chip(t("Reksa dana"), d.kind == "FUND", { onChange(d.copy(kind = "FUND", code = "", name = "")) })
            Chip(t("Obligasi"), d.kind == "BOND", { onChange(d.copy(kind = "BOND", code = "", name = "")) })
        }
        VSpace(8)
        when (d.kind) {
            "STOCK" -> {
                OutlinedTextField(d.code, { onChange(d.copy(code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6))) },
                    label = { Text(t("Kode saham")) }, placeholder = { Text("BBCA") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.amount, { onChange(d.copy(amount = it.filter { ch -> ch.isDigit() }.take(7))) },
                    label = { Text(t("Jumlah lot (1 lot = 100 lembar)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
            "BOND" -> {
                OutlinedTextField(d.code, { onChange(d.copy(code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(10))) },
                    label = { Text(t("Seri obligasi")) }, placeholder = { Text(t("FR0100, ORI025, SBR013…")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.amount, { onChange(d.copy(amount = it.filter { ch -> ch.isDigit() }.take(6))) },
                    label = { Text(t("Jumlah unit (1 unit = Rp1 juta nominal)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text(t("Seri FR mengikuti harga pasar harian; ORI/SR/SBR/ST dihitung sebesar nominal."),
                    style = MaterialTheme.typography.labelSmall, color = c.muted)
            }
            else -> {
                if (d.code.isNotEmpty()) Text(d.name, style = MaterialTheme.typography.bodyLarge, color = c.text)
                OutlinedTextField(query, { q ->
                    query = q
                    if (funds == null) scope.launch {
                        funds = FundCatalog.load(context, UrlHttp()).getOrElse { e -> error = t("Daftar reksa dana belum bisa diunduh: %s", e.message ?: ""); emptyList() }
                    }
                }, label = { Text(t("Cari nama reksa dana")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val matches = funds.orEmpty().filter { f -> query.length >= 3 && query.lowercase().split(' ').all { f.name.lowercase().contains(it) } }.take(20)
                if (matches.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(matches) { f ->
                        Text(f.name, Modifier.fillMaxWidth().clickable { onChange(d.copy(code = f.id, name = f.name)); query = f.name }.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium, color = c.text)
                    }
                }
                OutlinedTextField(d.amount, { onChange(d.copy(amount = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' })) },
                    label = { Text(t("Jumlah unit (lihat di Bibit)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
        }
        MoneyField(t("Modal (total yang dibayar, lihat di Bibit)"), d.cost, { onChange(d.copy(cost = it)) }, Modifier.fillMaxWidth())
        error?.let { Text(it, color = c.expense, style = MaterialTheme.typography.bodySmall) }
        VSpace(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClose) { Text(t("Batal")) }
            Button({
                val unitsE4 = when (d.kind) {
                    "STOCK" -> d.amount.toLongOrNull()?.times(100 * 10_000)
                    "BOND" -> d.amount.toLongOrNull()?.times(10_000)
                    else -> Units.parse(d.amount)
                }
                if (unitsE4 == null) { error = t("Isi jumlahnya"); return@Button }
                val newCost = d.cost.toLongOrNull()
                val doSave: suspend () -> Unit = {
                    container.holdings.save(
                        HoldingEntity(d.id, accountId, d.kind, d.code, if (d.kind == "FUND") d.name else d.code,
                            unitsE4, null, null, null, 0, costRp = newCost)
                    ).onSuccess { onClose() }.onFailure { error = it.message }
                }
                if (existing != null && unitsE4 < existing.unitsE4) {
                    // Fewer units: maybe a sale — ask for the proceeds so the profit isn't lost.
                    sale = PendingSale(existing.code, ((existing.costRp ?: 0) - (newCost ?: 0)).coerceAtLeast(0), doSave)
                } else scope.launch { doSave() }
            }) { Text(t("Simpan")) }
            if (existing != null) TextButton({
                sale = PendingSale(existing.code, existing.costRp ?: 0) { container.holdings.delete(existing.id); onClose() }
            }) { Text(t("Hapus / jual"), color = c.expense) }
        }
    }

    sale?.let { p ->
        SaleDialog(p.label, onDismiss = { sale = null }) { proceeds ->
            scope.launch {
                if (proceeds != null) {
                    // Proceeds go to RDN; RDN already rises by the modal removed, so record only the difference.
                    val adj = Valuation.saleAdjustment(proceeds, p.modalRemoved)
                    if (adj != 0L) container.transactions.save(Tx(0, TxType.ADJUSTMENT, adj, null, accountId, null, null,
                        Instant.now(), "Hasil jual ${p.label}", null, null))
                }
                p.apply()
                sale = null
            }
        }
    }
}

@Composable
private fun RdnCorrectionDialog(current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var digits by remember { mutableStateOf(current.coerceAtLeast(0).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Koreksi saldo RDN")) },
        text = {
            Column {
                Text(t("Masukkan saldo RDN yang tampil di Bibit. Selisihnya dicatat sebagai penyesuaian saldo."))
                VSpace(8)
                MoneyField(t("Saldo RDN sekarang"), digits, { digits = it }, Modifier.fillMaxWidth())
            }
        },
        // An empty field means Rp0 (the amount field drops leading zeros).
        confirmButton = { TextButton({ (digits.toLongOrNull() ?: 0L).let { if (it != current) onSave(it) else onDismiss() } }) { Text(t("Simpan")) } },
        dismissButton = { TextButton(onDismiss) { Text(t("Batal")) } },
    )
}

/** Gold accounts: show the latest automatic Pegadaian buy-back price. */
@Composable
fun GoldPriceLine(accountId: Long) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val history by remember(accountId) { container.snapshots.observeHistory(accountId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val last = history.firstOrNull { it.goldPricePerGram != null }
    SectionCard(Modifier.fillMaxWidth()) {
        Text(t("Harga Pegadaian otomatis"), style = MaterialTheme.typography.titleSmall, color = c.text)
        Text(
            last?.let { t("Harga jual (buyback) %s/gr · %s", Rupiah.full(it.goldPricePerGram!!), formatDateTime(it.date)) }
                ?: t("Belum ada harga. Isi jumlah gram lewat \"Perbarui nilai\" dulu."),
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
        TextButton({
            scope.launch {
                val r = PriceUpdater(container, UrlHttp()).run()
                status = if (r.skipped) t("Harga otomatis dimatikan di Pengaturan.") else r.errors.firstOrNull { it.startsWith("Emas") } ?: t("Harga emas diperbarui.")
            }
        }) { Text(t("Perbarui sekarang")) }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.text) }
    }
}
