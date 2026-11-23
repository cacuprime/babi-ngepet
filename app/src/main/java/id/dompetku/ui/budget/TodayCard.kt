package id.dompetku.ui.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import id.dompetku.core.Rupiah
import id.dompetku.core.t
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.components.AmountText
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors

/** Home: "Sekilas hari ini" — what's left to spend today under the current budget. */
@Composable
fun TodayCard(hidden: Boolean, onOpen: () -> Unit) {
    val c = LocalDompetkuColors.current
    val container = LocalContainer.current
    val state by remember { budgetState(container) }.collectAsState(initial = null)
    val s = state ?: return
    val daily = s.current?.daily
    SectionCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Text(t("SEKILAS HARI INI"), style = MaterialTheme.typography.labelMedium, color = c.muted)
        if (daily == null) {
            Text(t("Atur budget untuk melihat jatah belanja harianmu"), style = MaterialTheme.typography.bodyLarge, color = c.text)
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                AmountText(daily.leftToday, style = AmountStyle.copy(fontSize = 26.sp), masked = hidden,
                    color = if (daily.leftToday < 0) c.expense else c.text)
                Text(if (daily.leftToday < 0) t("lewat dari jatah hari ini") else t("budget harian yang tersisa"),
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(t("Jatah %s", if (hidden) Rupiah.MASK else Rupiah.full(daily.allowance)), style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text(t("Keluar %s", if (hidden) Rupiah.MASK else Rupiah.full(daily.spentToday)), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        s.current?.usage?.filter { it.over && it.bucket != id.dompetku.domain.Bucket.SAVINGS }?.forEach {
            Text(t("Pos %s sudah %d%% dari budget.", t(it.bucket.label), it.percent), style = MaterialTheme.typography.labelMedium, color = c.expense)
        }
    }
}
