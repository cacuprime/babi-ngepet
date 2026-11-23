package id.dompetku.ui.accounts

import id.dompetku.core.t

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dompetku.data.AccountView
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.components.AmountText
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import kotlinx.coroutines.launch

private data class GroupDraft(val id: Long, val name: String, val selected: Set<Long>)

/** "Grup akun": display-only sums of chosen accounts, e.g. Dana harian = Mandiri + BNI + SeaBank. */
@Composable
fun AccountGroupsSection(views: List<AccountView>) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val groups by remember { container.dashboard.groupViews() }.collectAsState(initial = emptyList())
    var draft by remember { mutableStateOf<GroupDraft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t("Grup akun"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text)
        TextButton({ draft = GroupDraft(0, "", emptySet()); error = null }) { Text(t("Buat grup")) }
    }
    if (groups.isEmpty() && draft == null) {
        Text(t("Gabungkan beberapa akun untuk melihat sisa dana totalnya, misalnya Dana harian."),
            style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
    groups.forEach { g ->
        SectionCard(Modifier.fillMaxWidth(), onClick = {
            draft = GroupDraft(g.group.id, g.group.name, g.group.accountIds.toSet()); error = null
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.group.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.text)
                AmountText(g.total, style = AmountStyle.copy(fontSize = 15.sp), color = c.text)
            }
            Text(g.members.joinToString(" · ") { it.account.name }, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
    draft?.let { d ->
        SectionCard(Modifier.fillMaxWidth()) {
            Text(if (d.id == 0L) t("Grup baru") else t("Ubah grup"), style = MaterialTheme.typography.titleMedium, color = c.text)
            VSpace(8)
            OutlinedTextField(d.name, { draft = d.copy(name = it); error = null }, label = { Text(t("Nama grup")) },
                placeholder = { Text(t("Dana harian")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            VSpace(8)
            views.filter { !it.account.isArchived }.forEach { v ->
                val checked = v.account.id in d.selected
                Row(
                    Modifier.fillMaxWidth().clickable {
                        draft = d.copy(selected = if (checked) d.selected - v.account.id else d.selected + v.account.id)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked, null)
                    Text(v.account.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text)
                    AmountText(v.currentValue, style = AmountStyle.copy(fontSize = 13.sp), color = c.muted)
                }
            }
            error?.let { Text(it, color = c.expense, style = MaterialTheme.typography.bodySmall) }
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ draft = null }) { Text(t("Batal")) }
                Button(enabled = !saving, onClick = {
                    saving = true
                    scope.launch {
                        container.groups.save(d.name, d.selected.toList(), d.id)
                            .onSuccess { draft = null }.onFailure { error = it.message }
                        saving = false
                    }
                }) { Text(t("Simpan")) }
                if (d.id != 0L) TextButton({ scope.launch { container.groups.delete(d.id); draft = null } }) {
                    Text(t("Hapus"), color = c.expense)
                }
            }
        }
    }
    Column {}
}
