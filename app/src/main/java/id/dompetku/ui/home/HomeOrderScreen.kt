package id.dompetku.ui.home

import id.dompetku.core.t

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.appViewModel
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.theme.LocalDompetkuColors
import kotlinx.coroutines.launch

/** "Atur kartu Home": move cards up/down and show/hide them. At least one card always stays visible. */
@Composable
fun HomeOrderScreen(nav: NavController) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val vm = appViewModel { HomeViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val keys = s.allPages.map { HomeOrder.key(it) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("Kembali"), tint = c.text) }
            Text(t("Atur kartu Home"), style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        Text(t("Urutkan kartu yang bisa digeser di halaman Home, dan sembunyikan yang tidak perlu."),
            style = MaterialTheme.typography.bodySmall, color = c.muted)
        SectionCard(Modifier.fillMaxWidth()) {
            s.allPages.forEachIndexed { i, page ->
                val k = HomeOrder.key(page)
                val hiddenNow = k in s.homeHidden
                val name = when (page) {
                    is HomePage.Total -> t("Total aset")
                    is HomePage.Group -> t("Grup · %s", page.view.group.name)
                    is HomePage.Single -> page.view.account.name
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (hiddenNow) c.muted else c.text)
                    IconButton({ scope.launch { container.settings.setHomeOrder(HomeOrder.move(keys, k, -1)) } }, enabled = i > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, t("Naikkan %s", name), tint = if (i > 0) c.text else c.barTrack)
                    }
                    IconButton({ scope.launch { container.settings.setHomeOrder(HomeOrder.move(keys, k, 1)) } }, enabled = i < keys.size - 1) {
                        Icon(Icons.Outlined.KeyboardArrowDown, t("Turunkan %s", name), tint = if (i < keys.size - 1) c.text else c.barTrack)
                    }
                    val visibleCount = keys.count { it !in s.homeHidden }
                    val canHide = hiddenNow || visibleCount > 1
                    IconButton({
                        scope.launch { container.settings.setHomeHidden(if (hiddenNow) s.homeHidden - k else s.homeHidden + k) }
                    }, enabled = canHide) {
                        Icon(if (hiddenNow) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (hiddenNow) t("Tampilkan %s", name) else t("Sembunyikan %s", name), tint = if (canHide) c.text else c.barTrack)
                    }
                }
            }
        }
    }
}
