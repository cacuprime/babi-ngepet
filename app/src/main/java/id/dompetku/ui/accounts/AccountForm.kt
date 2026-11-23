package id.dompetku.ui.accounts

import id.dompetku.core.t

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.dompetku.core.Grams
import id.dompetku.core.Rupiah
import id.dompetku.data.StartingAccount
import id.dompetku.domain.AccountType
import id.dompetku.ui.MoneyField
import id.dompetku.ui.components.Chip
import id.dompetku.ui.theme.LocalDompetkuColors

data class AccountFormState(
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val provider: String = "",
    val balance: String = "",
    val value: String = "",
    val grams: String = "",
    val pricePerGram: String = "",
    val isLiquid: Boolean = AccountType.BANK.defaultLiquid,
    /** When editing an existing account, valuation fields are hidden (use "Perbarui nilai"). */
    val editing: Boolean = false,
) {
    /** Returns the account or an Indonesian error message. */
    fun build(): Pair<StartingAccount?, String?> {
        if (name.isBlank()) return null to t("Nama akun wajib diisi")
        val bal = balance.toLongOrNull() ?: 0L
        if (!editing && type == AccountType.GOLD) {
            val g = Grams.parse(grams) ?: return null to t("Isi jumlah gram, contoh 10,5000")
            val p = pricePerGram.toLongOrNull() ?: return null to t("Isi harga jual per gram")
            val v = Grams.valueOf(g, p)
            return StartingAccount(name, type, provider.ifBlank { null }, if (bal > 0) bal else v, v, g, p, isLiquid) to null
        }
        val v = if (!editing && type == AccountType.INVESTMENT) value.toLongOrNull() ?: bal else null
        return StartingAccount(name, type, provider.ifBlank { null }, bal, v, null, null, isLiquid) to null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountForm(s: AccountFormState, onChange: (AccountFormState) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalDompetkuColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(s.name, { onChange(s.copy(name = it)) }, label = { Text(t("Nama akun")) },
            placeholder = { Text(t("BCA Utama")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(t("Jenis"), style = MaterialTheme.typography.labelLarge, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountType.entries.forEach { t ->
                Chip(t.label, s.type == t, { onChange(s.copy(type = t, isLiquid = t.defaultLiquid)) })
            }
        }
        OutlinedTextField(s.provider, { onChange(s.copy(provider = it)) }, label = { Text(t("Bank / penyedia (opsional)")) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        when {
            s.editing -> MoneyField(t("Saldo awal"), s.balance, { onChange(s.copy(balance = it)) }, Modifier.fillMaxWidth())
            s.type == AccountType.GOLD -> {
                OutlinedTextField(s.grams, { onChange(s.copy(grams = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' })) },
                    label = { Text(t("Jumlah emas (gram)")) }, placeholder = { Text("10,5000") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                MoneyField(t("Harga jual per gram"), s.pricePerGram, { onChange(s.copy(pricePerGram = it)) }, Modifier.fillMaxWidth())
                val g = Grams.parse(s.grams); val p = s.pricePerGram.toLongOrNull()
                if (g != null && p != null) {
                    Text(t("Nilai: %s", Rupiah.full(Grams.valueOf(g, p))), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
                MoneyField(t("Modal yang sudah disetor (opsional)"), s.balance, { onChange(s.copy(balance = it)) }, Modifier.fillMaxWidth())
            }
            s.type == AccountType.INVESTMENT -> {
                MoneyField(t("Nilai saat ini"), s.value, { onChange(s.copy(value = it)) }, Modifier.fillMaxWidth())
                MoneyField(t("Modal yang sudah disetor (opsional)"), s.balance, { onChange(s.copy(balance = it)) }, Modifier.fillMaxWidth())
            }
            else -> MoneyField(t("Saldo saat ini"), s.balance, { onChange(s.copy(balance = it)) }, Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Hitung sebagai saldo likuid"), style = MaterialTheme.typography.bodyLarge, color = c.text)
                Text(t("Uang yang bisa dipakai kapan saja"), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Switch(s.isLiquid, { onChange(s.copy(isLiquid = it)) })
        }
    }
}
