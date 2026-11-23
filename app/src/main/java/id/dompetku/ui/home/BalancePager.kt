package id.dompetku.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dompetku.core.Grams
import id.dompetku.ui.components.AmountText
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.HeroAmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors

/** Swipeable balance cards: Total aset, then account groups, then each account. Balances only. */
@Composable
fun BalancePager(
    pages: List<HomePage>,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    onOpenAccount: (Long) -> Unit,
) {
    val c = LocalDompetkuColors.current
    val pager = rememberPagerState { pages.size }
    Column {
        HorizontalPager(pager, pageSpacing = 12.dp, modifier = Modifier.fillMaxWidth()) { i ->
            val page = pages.getOrNull(i) ?: return@HorizontalPager
            Column(
                Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(24.dp)).background(c.hero)
                    .then(if (page is HomePage.Single) Modifier.clickable { onOpenAccount(page.view.account.id) } else Modifier)
                    .padding(20.dp),
            ) {
                val (title, sub) = when (page) {
                    is HomePage.Total -> t("Total aset") to null
                    is HomePage.Group -> page.view.group.name to t("Grup · %d akun", page.view.members.size)
                    is HomePage.Single -> page.view.account.name to
                        listOfNotNull(page.view.account.provider, page.view.account.type.label).distinct().joinToString(" · ")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.labelLarge, color = c.onHeroMuted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
                    }
                    IconButton(onToggleHidden, Modifier.size(32.dp)) {
                        Icon(
                            if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (hidden) t("Tampilkan saldo") else t("Sembunyikan saldo"), tint = c.onHero,
                        )
                    }
                }
                val big = when (page) {
                    is HomePage.Total -> page.summary.totalAssets
                    is HomePage.Group -> page.view.total
                    is HomePage.Single -> page.view.currentValue
                }
                AmountText(big, style = HeroAmountStyle, color = c.onHero, masked = hidden)
                Box(Modifier.weight(1f))
                when (page) {
                    is HomePage.Total -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Line("Likuid", page.summary.liquid, hidden)
                        Line("Investasi", page.summary.investmentValue, hidden)
                    }
                    is HomePage.Group -> Text(
                        page.view.members.joinToString(" · ") { it.account.name },
                        style = MaterialTheme.typography.bodySmall, color = c.onHeroMuted, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    is HomePage.Single -> page.view.goldTenThousandths?.let {
                        Text(if (hidden) t("••• gr") else Grams.format(it), style = MaterialTheme.typography.titleSmall, color = c.onHero)
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                pages.indices.forEach { i ->
                    val active = i == pager.currentPage
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (active) 8.dp else 6.dp).clip(CircleShape)
                            .background(if (active) c.text else c.barTrack),
                    )
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, amount: Long, hidden: Boolean) {
    val c = LocalDompetkuColors.current
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
        AmountText(amount, style = AmountStyle.copy(fontSize = 15.sp), color = c.onHero, masked = hidden)
    }
}
