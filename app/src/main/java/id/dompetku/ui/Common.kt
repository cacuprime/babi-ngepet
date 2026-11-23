package id.dompetku.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import id.dompetku.AppContainer
import id.dompetku.core.Rupiah
import id.dompetku.domain.JAKARTA
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }

val ID_LOCALE: Locale = Locale("id", "ID")
// Follow the app language; built per call, which is cheap next to a recomposition.
private val dayFmt get() = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", id.dompetku.core.I18n.locale)
private val shortFmt get() = DateTimeFormatter.ofPattern("d MMM yyyy", id.dompetku.core.I18n.locale)
private val timeFmt get() = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", id.dompetku.core.I18n.locale)
private val monthFmt get() = DateTimeFormatter.ofPattern("MMM", id.dompetku.core.I18n.locale)

fun formatDay(d: LocalDate): String = dayFmt.format(d).replaceFirstChar { it.uppercase() }
fun formatShort(i: Instant): String = shortFmt.format(i.atZone(JAKARTA))
fun formatDateTime(i: Instant): String = timeFmt.format(i.atZone(JAKARTA))
fun formatMonth(m: java.time.YearMonth): String = monthFmt.format(m.atDay(1))

@Suppress("UNCHECKED_CAST")
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalContainer.current
    return viewModel(key = key, factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create(c) as T
    })
}

/** Shows digits as 1.250.000 while the field keeps raw digits. */
object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val out = Rupiah.groupDots(raw.toLongOrNull() ?: return TransformedText(text, OffsetMapping.Identity))
        val map = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val dotsBefore = (raw.length - 1) / 3 - (raw.length - offset) / 3
                return (offset + dotsBefore.coerceAtLeast(0)).coerceIn(0, out.length)
            }
            override fun transformedToOriginal(offset: Int): Int =
                out.take(offset).count { it.isDigit() }.coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(out), map)
    }
}

@Composable
fun MoneyField(label: String, digits: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = digits,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.trimStart('0').take(13)) },
        label = { Text(label) },
        prefix = { Text("Rp") },
        singleLine = true,
        visualTransformation = ThousandsTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
