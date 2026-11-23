package id.dompetku.ui.insights

import id.dompetku.core.t

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dompetku.data.db.CategoryEntity
import id.dompetku.domain.Period
import id.dompetku.domain.Reports
import id.dompetku.domain.Span
import id.dompetku.domain.Tx
import id.dompetku.ui.components.AmountText
import id.dompetku.ui.components.Chip
import id.dompetku.ui.components.CircleIcon
import id.dompetku.ui.components.EmptyState
import id.dompetku.ui.components.HSpace
import id.dompetku.ui.components.SectionCard
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.components.categoryIcon
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.HeroAmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors
import java.time.Instant

/** Weekly / monthly spending per category, with ‹ › navigation and tap-through to transactions. */
@Composable
fun SpendingSection(
    txs: List<Tx>,
    categories: Map<Long, CategoryEntity>,
    onOpenCategory: (categoryId: Long, period: Period) -> Unit,
) {
    val c = LocalDompetkuColors.current
    var span by rememberSaveable { mutableStateOf(Span.WEEK) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val r = Reports.spending(txs, span, offset, Instant.now())
    val unit = if (span == Span.WEEK) "minggu" else "bulan"

    SectionCard(Modifier.fillMaxWidth()) {
        Text(t("Pengeluaran"), style = MaterialTheme.typography.titleMedium, color = c.text)
        VSpace(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t("Mingguan"), span == Span.WEEK, { span = Span.WEEK; offset = 0 })
            Chip(t("Bulanan"), span == Span.MONTH, { span = Span.MONTH; offset = 0 })
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ offset-- }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, t("Sebelumnya"), tint = c.text) }
            Text(r.period.label(span), Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall, color = c.text)
            IconButton({ if (offset < 0) offset++ }, enabled = offset < 0) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, t("Berikutnya"), tint = if (offset < 0) c.text else c.barTrack)
            }
        }
        AmountText(r.total, style = HeroAmountStyle.copy(fontSize = 28.sp), color = c.text)
        val change = r.changePercent
        Text(
            when {
                change == null -> t("Belum ada pengeluaran %s sebelumnya untuk dibandingkan", t(unit))
                change > 0 -> t("Naik %d%% dari %s sebelumnya", change, t(unit))
                change < 0 -> t("Turun %d%% dari %s sebelumnya", -change, t(unit))
                else -> t("Sama dengan %s sebelumnya", t(unit))
            },
            style = MaterialTheme.typography.bodySmall,
            color = when { change == null || change == 0 -> c.muted; change > 0 -> c.expense; else -> c.income },
        )
        VSpace(12)
        if (r.categories.isEmpty()) EmptyState(t("Belum ada pengeluaran di periode ini."))
        r.categories.forEach { cs ->
            val cat = categories[cs.categoryId]
            Column(
                Modifier.fillMaxWidth().clickable { onOpenCategory(cs.categoryId, r.period) }.padding(vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(categoryIcon(cat?.icon), c.background, c.text, size = 32)
                    HSpace(10)
                    Text(cat?.name ?: "?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text)
                    Text("${(cs.share * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    HSpace(10)
                    AmountText(cs.amount, style = AmountStyle.copy(fontSize = 14.sp), color = c.text)
                }
                VSpace(6)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.barTrack)) {
                    Box(Modifier.fillMaxWidth(cs.share.coerceIn(0.01f, 1f)).height(4.dp).clip(CircleShape).background(c.expense))
                }
            }
        }
    }
}
