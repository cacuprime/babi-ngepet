package id.dompetku.ui.accounts

import id.dompetku.core.t

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import id.dompetku.AppContainer
import id.dompetku.core.Grams
import id.dompetku.data.AccountView
import id.dompetku.data.StartingData
import id.dompetku.domain.AccountType
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.Routes
import id.dompetku.ui.appViewModel
import id.dompetku.ui.components.AmountText
import id.dompetku.ui.components.CircleIcon
import id.dompetku.ui.components.EmptyState
import id.dompetku.ui.components.HSpace
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.components.accountIcon
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.HeroAmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class AccountsViewModel(c: AppContainer) : ViewModel() {
    val views = c.dashboard.accountViews().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private enum class Group(val label: String, val types: Set<AccountType>) {
    LIQUID("Likuid", setOf(AccountType.CASH, AccountType.BANK, AccountType.EWALLET, AccountType.SAVINGS)),
    DEPOSIT("Deposito", setOf(AccountType.DEPOSIT)),
    INVEST("Investasi", setOf(AccountType.INVESTMENT)),
    GOLD("Emas", setOf(AccountType.GOLD)),
    OTHER("Lainnya", setOf(AccountType.OTHER_ASSET)),
}

@Composable
fun AccountsScreen(nav: NavController) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val vm = appViewModel { AccountsViewModel(it) }
    val views by vm.views.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<AccountFormState?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Akun"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = c.text)
            Button({ adding = AccountFormState() }, colors = ButtonDefaults.buttonColors(containerColor = c.fab, contentColor = c.onFab)) {
                Icon(Icons.Outlined.Add, null); HSpace(4); Text(t("Tambah"))
            }
        }
        val active = views.orEmpty().filter { !it.account.isArchived }
        Text(t("Total"), style = MaterialTheme.typography.labelLarge, color = c.muted)
        AmountText(active.sumOf { it.currentValue } + views.orEmpty().filter { it.account.isArchived }.sumOf { it.currentValue },
            style = HeroAmountStyle, color = c.text)

        if (!views.isNullOrEmpty()) AccountGroupsSection(views.orEmpty())

        val f = adding
        if (f != null) {
            SectionCard {
                Text(t("Akun baru"), style = MaterialTheme.typography.titleMedium, color = c.text)
                VSpace(8)
                AccountForm(f, { adding = it; error = null })
                error?.let { Text(it, color = c.expense) }
                VSpace(12)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton({ adding = null }) { Text(t("Batal")) }
                    Button(enabled = !saving, onClick = {
                        val (acc, err) = f.build()
                        if (acc == null) error = err else if (!saving) {
                            saving = true
                            adding = null
                            scope.launch { StartingData.createAccount(container, acc, Instant.now()); saving = false }
                        }
                    }) { Text(t("Simpan")) }
                }
            }
        }

        if (views != null && views!!.isEmpty() && f == null) EmptyState(t("Belum ada akun. Tambahkan rekening, e-wallet, atau investasi."))

        Group.entries.forEach { g ->
            val list = active.filter { it.account.type in g.types }
            if (list.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp)) {
                    Text(g.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text)
                    AmountText(list.sumOf { it.currentValue }, style = AmountStyle.copy(fontSize = 15.sp), color = c.muted)
                }
                SectionCard(Modifier.fillMaxWidth()) {
                    list.forEach { v -> AccountRow(v) { nav.navigate(Routes.account(v.account.id)) } }
                }
            }
        }
        val archived = views.orEmpty().filter { it.account.isArchived }
        if (archived.isNotEmpty()) {
            Text(t("Diarsipkan"), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, color = c.muted)
            SectionCard(Modifier.fillMaxWidth()) {
                archived.forEach { v -> AccountRow(v) { nav.navigate(Routes.account(v.account.id)) } }
            }
        }
        VSpace(24)
    }
}

@Composable
fun AccountRow(v: AccountView, onClick: () -> Unit) {
    val c = LocalDompetkuColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(accountIcon(v.account.type), c.background, c.text)
        HSpace(12)
        Column(Modifier.weight(1f)) {
            Text(v.account.name, style = MaterialTheme.typography.bodyLarge, color = c.text)
            val sub = listOfNotNull(v.account.provider, v.goldTenThousandths?.let { Grams.format(it) }, v.account.type.label)
                .distinct().joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        AmountText(v.currentValue, style = AmountStyle.copy(fontSize = 15.sp), color = c.text)
    }
}
