package id.dompetku.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * App language. Texts are written in Indonesian in the code and looked up in [English] when the
 * owner picks English; a missing entry falls back to Indonesian instead of breaking the screen.
 * shortcut: one table keyed by the Indonesian wording instead of Android string resources; move to
 * strings.xml if a third language or a translator is ever involved.
 */
object I18n {
    /** Snapshot state, so every screen recomposes when the language changes. */
    var english by mutableStateOf(false)
    val locale: Locale get() = if (english) Locale.ENGLISH else Locale("id", "ID")
}

fun t(id: String): String = if (I18n.english) English[id] ?: id else id

fun t(id: String, vararg args: Any?): String = String.format(I18n.locale, t(id), *args)
