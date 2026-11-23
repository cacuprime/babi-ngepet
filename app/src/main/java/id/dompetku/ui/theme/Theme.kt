package id.dompetku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import id.dompetku.R

enum class ThemeMode { LIGHT, DARK, SYSTEM }

val Lime = Color(0xFFC6F062)

/** App-specific colors on top of Material: layout B (light) and C (dark). */
@Immutable
data class DompetkuColors(
    val background: Color,
    val card: Color,
    val cardInverse: Color,
    val onCardInverse: Color,
    val text: Color,
    val muted: Color,
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val accent: Color,
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val barTrack: Color,
    val fab: Color,
    val onFab: Color,
    val divider: Color,
)

val LightColors = DompetkuColors(
    background = Color(0xFFF2F2EC), card = Color(0xFFFFFFFF), cardInverse = Color(0xFF111111),
    onCardInverse = Color(0xFFFFFFFF), text = Color(0xFF111111), muted = Color(0xFF6E6E68),
    hero = Lime, onHero = Color(0xFF111111), onHeroMuted = Color(0xFF2F4A00), accent = Lime,
    income = Color(0xFF3B8C2A), expense = Color(0xFFD93A2F), transfer = Color(0xFF6E6E68),
    barTrack = Color(0x33111111), fab = Color(0xFF111111), onFab = Lime, divider = Color(0x14111111),
)

val DarkColors = DompetkuColors(
    background = Color(0xFF0E0E0E), card = Color(0xFF1D1D1D), cardInverse = Color(0xFF2A2A2A),
    onCardInverse = Color(0xFFF2F2F2), text = Color(0xFFF2F2F2), muted = Color(0xFF9A9A9A),
    hero = Color(0xFF1D1D1D), onHero = Color(0xFFF2F2F2), onHeroMuted = Color(0xFF9A9A9A), accent = Lime,
    income = Lime, expense = Color(0xFFFF5A4E), transfer = Color(0xFF9A9A9A),
    barTrack = Color(0xFF555555), fab = Lime, onFab = Color(0xFF111111), divider = Color(0x1FFFFFFF),
)

val LocalDompetkuColors = staticCompositionLocalOf { LightColors }

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.inter() = copy(fontFamily = Inter)
    return Typography(
        displayLarge = base.displayLarge.inter(), displayMedium = base.displayMedium.inter(),
        displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter().copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.inter().copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.inter().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.inter().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.inter().copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.inter(), bodyMedium = base.bodyMedium.inter(), bodySmall = base.bodySmall.inter(),
        labelLarge = base.labelLarge.inter().copy(fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.inter(), labelSmall = base.labelSmall.inter(),
    )
}

/** Large money numbers: tabular figures so columns line up. */
val AmountStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", textAlign = TextAlign.Start,
)
val HeroAmountStyle = AmountStyle.copy(fontSize = 32.sp, letterSpacing = (-0.5).sp)

@Composable
fun DompetkuTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = Lime, onPrimary = Color(0xFF111111), background = c.background, onBackground = c.text,
            surface = c.card, onSurface = c.text, surfaceVariant = c.cardInverse, onSurfaceVariant = c.muted,
            secondaryContainer = Color(0xFF2E3A12), onSecondaryContainer = Lime, error = c.expense,
            surfaceContainer = c.card, surfaceContainerHigh = c.card, surfaceContainerLow = c.background,
            outline = Color(0xFF444444), outlineVariant = Color(0xFF333333),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF111111), onPrimary = Color.White, background = c.background, onBackground = c.text,
            surface = c.card, onSurface = c.text, surfaceVariant = Color(0xFFE8E8E1), onSurfaceVariant = c.muted,
            secondaryContainer = Lime, onSecondaryContainer = Color(0xFF111111), error = c.expense,
            surfaceContainer = c.card, surfaceContainerHigh = c.card, surfaceContainerLow = c.background,
            outline = Color(0xFFCFCFC8), outlineVariant = Color(0xFFE2E2DB),
        )
    }
    CompositionLocalProvider(LocalDompetkuColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}
