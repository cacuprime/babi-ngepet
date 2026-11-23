package id.dompetku.importer

import id.dompetku.core.t

import androidx.lifecycle.ViewModel
import id.dompetku.AppContainer
import id.dompetku.data.ImportedTx
import id.dompetku.data.db.CategoryEntity
import id.dompetku.domain.Account
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.Instant

enum class ImportStage { IDLE, READING, REVIEW, DONE, ERROR }

data class DraftRow(
    val key: Int,
    val draft: Draft,
    val categoryId: Long?,
    /** What the categoriser suggested; a different final choice is learned on confirm. */
    val suggestedCategoryId: Long?,
    val confidence: Float,
    val selected: Boolean,
    val duplicateOf: Long?,
)

data class ImportState(
    val stage: ImportStage = ImportStage.IDLE,
    val rows: List<DraftRow> = emptyList(),
    val accountId: Long? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val source: String = "SCREENSHOT",
    val attachmentId: Long? = null,
    val message: String? = null,
    val saved: Int = 0,
)

/** One import: drafts → review (edit, select, pick account) → confirm. Nothing touches balances before confirm. */
class ImportViewModel(private val c: AppContainer, private val now: () -> Instant = Instant::now) : ViewModel() {
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state

    private var existing: List<Tx> = emptyList()
    private var refs: Map<String, Long> = emptyMap()
    private var busy = false

    fun reading() = _state.update { it.copy(stage = ImportStage.READING, message = null) }
    fun fail(message: String) = _state.update { it.copy(stage = ImportStage.ERROR, message = message) }

    suspend fun accept(drafts: List<Draft>, source: String, attachment: Long?, warning: String? = null) {
        if (drafts.isEmpty()) { fail("Tidak ada transaksi yang terbaca. Coba foto lebih jelas atau masukkan manual."); return }
        val accounts = c.accounts.observeAll().first().filter { !it.isArchived }
        val categories = c.categories.observeAll().first()
        val categorizer = Categorizer(c.rules.all(), categories)
        existing = c.transactions.observeAll().first()
        refs = c.imports.referenceMap()
        val account = _state.value.accountId?.takeIf { id -> accounts.any { it.id == id } }
            ?: c.transactions.lastUsedAccount(TxType.EXPENSE)?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull()?.id
        val rows = drafts.mapIndexed { i, d ->
            val s = categorizer.suggest(d)
            DraftRow(i, d, s?.categoryId, s?.categoryId, d.confidence, selected = true, duplicateOf = null)
        }
        _state.value = ImportState(
            stage = ImportStage.REVIEW, rows = markDuplicates(rows, account), accountId = account,
            accounts = accounts, categories = categories, source = source, attachmentId = attachment, message = warning,
        )
    }

    private fun markDuplicates(rows: List<DraftRow>, account: Long?): List<DraftRow> = rows.map { r ->
        val dup = account?.let { Duplicates.find(r.draft, it, existing, refs) }
        r.copy(duplicateOf = dup, selected = if (dup != null) false else r.selected || r.duplicateOf != null)
    }

    fun setAccount(id: Long) = _state.update { it.copy(accountId = id, rows = markDuplicates(it.rows, id), message = null) }
    fun edit(key: Int, f: (DraftRow) -> DraftRow) = _state.update { s -> s.copy(rows = s.rows.map { if (it.key == key) f(it) else it }, message = null) }
    fun toggle(key: Int) = edit(key) { it.copy(selected = !it.selected) }
    fun remove(key: Int) = _state.update { s -> s.copy(rows = s.rows.filterNot { it.key == key }) }

    /** Saves the selected rows. Returns how many were saved (0 if blocked or already saving/saved). */
    suspend fun confirm(): Int {
        val s = _state.value
        if (busy || s.stage != ImportStage.REVIEW) return 0
        val account = s.accountId ?: run { _state.update { it.copy(message = t("Pilih akun dulu")) }; return 0 }
        val chosen = s.rows.filter { it.selected }
        if (chosen.isEmpty()) { _state.update { it.copy(message = t("Pilih minimal satu transaksi")) }; return 0 }
        busy = true
        try {
            val items = chosen.map { r ->
                val d = r.draft
                val catNeeded = d.type == TxType.INCOME || d.type == TxType.EXPENSE
                ImportedTx(
                    Tx(0, d.type, d.amount, if (catNeeded) r.categoryId else null, account, null, null, d.date,
                        d.description.take(200).ifBlank { null }, d.merchant?.take(100), null),
                    d.reference, s.source, s.attachmentId,
                )
            }
            val result = c.imports.saveAll(items)
            result.exceptionOrNull()?.let { e -> _state.update { it.copy(message = e.message) }; return 0 }
            val n = result.getOrThrow()
            // Saved already: mark DONE first so a failure while learning can never allow a second save.
            _state.update { it.copy(stage = ImportStage.DONE, saved = n, message = null) }
            chosen.filter { it.categoryId != null && it.categoryId != it.suggestedCategoryId }.forEach { r ->
                runCatching { c.rules.learn(r.draft.merchant ?: r.draft.description, r.categoryId!!) }
            }
            return n
        } finally {
            busy = false
        }
    }
}
