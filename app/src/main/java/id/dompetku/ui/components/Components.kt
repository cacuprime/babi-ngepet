package id.dompetku.ui.components

import id.dompetku.core.t

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.dompetku.core.Rupiah
import id.dompetku.ui.theme.AmountStyle
import id.dompetku.ui.theme.HeroAmountStyle
import id.dompetku.ui.theme.LocalDompetkuColors

val CardShape = RoundedCornerShape(20.dp)

/** Share of liquid vs investment for the composition bar; both 0 when there is nothing. */
fun compositionShares(liquid: Long, investment: Long): Pair<Float, Float> {
    val total = liquid + investment
    if (total <= 0L) return 0f to 0f
    val l = liquid.toFloat() / total
    return l to (1f - l)
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    color: Color = LocalDompetkuColors.current.card,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun AmountText(
    amount: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = AmountStyle.copy(fontSize = 16.sp),
    color: Color = LocalDompetkuColors.current.text,
    signed: Boolean = false,
    compact: Boolean = false,
    masked: Boolean = false,
) {
    val text = when {
        masked -> Rupiah.MASK
        compact -> Rupiah.compact(amount)
        signed -> Rupiah.signed(amount)
        else -> Rupiah.full(amount)
    }
    Text(text, modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun CompositionBar(liquid: Long, investment: Long, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val c = LocalDompetkuColors.current
    val (l, i) = compositionShares(liquid, investment)
    val liquidColor = if (onDark) c.accent else c.text
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (l + i == 0f) {
                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(c.barTrack))
            } else {
                if (l > 0f) Box(Modifier.weight(l).height(6.dp).clip(CircleShape).background(liquidColor))
                if (i > 0f) Box(Modifier.weight(i).height(6.dp).clip(CircleShape).background(c.barTrack))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val label = if (onDark) c.muted else c.onHeroMuted
            Text(t("Likuid %d%%", (l * 100).toInt()), style = MaterialTheme.typography.labelMedium, color = label)
            Text(t("Investasi %d%%", (i * 100).toInt()), style = MaterialTheme.typography.labelMedium, color = label)
        }
    }
}

/** The big lime (light) / dark (dark) total-assets card. */
@Composable
fun HeroCard(
    title: String,
    amount: Long,
    liquid: Long,
    investment: Long,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    val c = LocalDompetkuColors.current
    val dark = c.hero != c.accent
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(c.hero).padding(20.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = c.onHeroMuted)
        Spacer(Modifier.height(4.dp))
        AmountText(amount, style = HeroAmountStyle, color = c.onHero)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.onHeroMuted)
        }
        Spacer(Modifier.height(14.dp))
        CompositionBar(liquid, investment, onDark = dark)
    }
}

/** Cash-flow card: Masuk uses lime, Keluar uses expense red (both themes). */
@Composable
fun StatCard(
    label: String,
    amount: Long,
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier, color = container) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(content.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = content, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.height(12.dp))
        AmountText(amount, style = AmountStyle.copy(fontSize = 20.sp), color = content)
        Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.75f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalDompetkuColors.current
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, style = MaterialTheme.typography.labelLarge, color = c.muted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalDompetkuColors.current
    val bg = if (selected) c.fab else c.card
    val fg = if (selected) c.onFab else c.text
    Text(
        text,
        modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
        color = fg,
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().padding(24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalDompetkuColors.current.muted,
    )
}

@Composable
fun CircleIcon(icon: ImageVector, bg: Color, fg: Color, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size((size * 0.5).dp))
    }
}

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
