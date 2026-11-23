package id.dompetku.ui.tx

import id.dompetku.core.t

import androidx.lifecycle.ViewModel
import id.dompetku.AppContainer
import id.dompetku.core.Grams
import id.dompetku.data.db.CategoryEntity
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * Keypad amount: numbers (max 13 digits each, no leading zeros) with optional + − × ÷ between them.
 * [value] is the result, never below 0.
 */
data class AmountInput(val digits: String = "") {
    val value: Long get() = id.dompetku.core.Calc.eval(digits).coerceAtLeast(0)
    val hasOperator: Boolean get() = digits.any { it in id.dompetku.core.Calc.OPS }
    private val last: String get() = digits.takeLastWhile { it.isDigit() }

    fun press(d: Char): AmountInput {
        if (!d.isDigit()) return this
        if (last.isEmpty() && d == '0') return this
        if (last.length >= MAX) return this
        return copy(digits = digits + d)
    }

    fun press000(): AmountInput = if (last.isEmpty()) this else copy(digits = digits + "000".take(MAX - last.length))

    /** Adds an operator after a number, or replaces the one just typed. */
    fun operator(op: Char): AmountInput = when {
        op !in id.dompetku.core.Calc.OPS || digits.isEmpty() -> this
        digits.last() in id.dompetku.core.Calc.OPS -> copy(digits = digits.dropLast(1) + op)
        else -> copy(digits = digits + op)
    }

    /** Replaces the expression with its result ("="). */
    fun evaluate(): AmountInput = of(value)
    fun backspace(): AmountInput = copy(digits = digits.dropLast(1))

    companion object {
        const val MAX = 13
        fun of(v: Long) = AmountInput(if (v <= 0) "" else v.toString())
    }
}

data class EditTxState(
    val isEdit: Boolean = false,
    val type: TxType = TxType.EXPENSE,
    val amount: AmountInput = AmountInput(),
    val adjustmentNegative: Boolean = false,
    val categoryId: Long? = null,
    val sourceAccountId: Long? = null,
    val destinationAccountId: Long? = null,
    val goldGrams: String = "",
    val date: Instant = Instant.EPOCH,
    val description: String = "",
    val merchant: String = "",
    val notes: String = "",
    /** As typed, e.g. "#liburan #bali". */
    val tags: String = "",
    val accounts: List<Account> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val error: String? = null,
    val loaded: Boolean = false,
    /** Number keypad shows only while the amount is being edited (open by default for a new transaction). */
    val keypadVisible: Boolean = false,
    /** Proof file (photo/screenshot/statement) linked to this transaction, if any. */
    val attachmentId: Long? = null,
) {
    val showCategory get() = type == TxType.INCOME || type == TxType.EXPENSE
    val showDestination get() = type == TxType.TRANSFER
    val showGold get() = type == TxType.TRANSFER &&
        accounts.any { it.type == AccountType.GOLD && (it.id == destinationAccountId || it.id == sourceAccountId) }
}

class EditTxViewModel(
    private val c: AppContainer,
    private val txId: Long,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow(EditTxState())
    val state: StateFlow<EditTxState> = _state

    private var allCategories: Map<String, List<CategoryEntity>> = emptyMap()
    private var recent: Map<TxType, List<Long>> = emptyMap()

    suspend fun load() {
        val accounts = c.accounts.observeAll().first().filter { !it.isArchived }
        allCategories = mapOf(
            "EXPENSE" to c.categories.observe("EXPENSE").first(),
            "INCOME" to c.categories.observe("INCOME").first(),
        )
        recent = mapOf(
            TxType.EXPENSE to c.transactions.recentCategories(TxType.EXPENSE),
            TxType.INCOME to c.transactions.recentCategories(TxType.INCOME),
        )
        val existing = if (txId != 0L) c.transactions.get(txId) else null
        if (existing != null) {
            val all = c.accounts.observeAll().first()
            _state.value = EditTxState(
                isEdit = true, type = existing.type,
                amount = AmountInput.of(kotlin.math.abs(existing.amount)),
                adjustmentNegative = existing.amount < 0,
                categoryId = existing.categoryId, sourceAccountId = existing.sourceAccountId,
                destinationAccountId = existing.destinationAccountId,
                goldGrams = existing.goldTenThousandths?.let { Grams.format(it).removeSuffix(" gr") } ?: "",
                date = existing.date, description = existing.description ?: "", merchant = existing.merchant ?: "",
                notes = existing.notes ?: "", tags = Tags.display(existing.tags),
                accounts = all.filter { !it.isArchived || it.id == existing.sourceAccountId || it.id == existing.destinationAccountId },
                categories = categoriesFor(existing.type), loaded = true,
                attachmentId = c.db.transactions().get(txId)?.attachmentId,
            )
        } else {
            val last = c.transactions.lastUsedAccount(TxType.EXPENSE)
            _state.value = EditTxState(
                type = TxType.EXPENSE, date = now(), accounts = accounts,
                sourceAccountId = last?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id,
                categories = categoriesFor(TxType.EXPENSE), loaded = true, keypadVisible = true,
            )
        }
    }

    /** Categories for the type, most recently used first. */
    private fun categoriesFor(type: TxType): List<CategoryEntity> {
        val key = if (type == TxType.INCOME) "INCOME" else "EXPENSE"
        val list = allCategories[key].orEmpty()
        val order = recent[type].orEmpty()
        return list.sortedBy { cat -> order.indexOf(cat.id).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    fun setType(t: TxType) = _state.update { s ->
        val dest = if (t == TxType.TRANSFER) s.destinationAccountId ?: s.accounts.firstOrNull { it.id != s.sourceAccountId }?.id else null
        s.copy(
            type = t, categories = categoriesFor(t),
            categoryId = if (s.type.categoryKey() == t.categoryKey()) s.categoryId else null,
            destinationAccountId = dest, error = null,
        )
    }

    private fun TxType.categoryKey() = when (this) { TxType.INCOME -> "I"; TxType.EXPENSE -> "E"; else -> "-" }

    fun showKeypad() = _state.update { it.copy(keypadVisible = true) }
    fun hideKeypad() = _state.update { it.copy(keypadVisible = false) }

    fun press(d: Char) = _state.update { it.copy(amount = it.amount.press(d), error = null) }
    fun press000() = _state.update { it.copy(amount = it.amount.press000(), error = null) }
    fun operator(op: Char) = _state.update { it.copy(amount = it.amount.operator(op), error = null) }
    fun evaluate() = _state.update { it.copy(amount = it.amount.evaluate()) }
    fun backspace() = _state.update { it.copy(amount = it.amount.backspace()) }
    fun toggleAdjustmentSign() = _state.update { it.copy(adjustmentNegative = !it.adjustmentNegative) }
    fun setCategory(id: Long) = _state.update { it.copy(categoryId = id, error = null) }
    fun setSource(id: Long) = _state.update { it.copy(sourceAccountId = id, error = null) }
    fun setDestination(id: Long) = _state.update { it.copy(destinationAccountId = id, error = null) }
    fun setDate(d: Instant) = _state.update { it.copy(date = d) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setMerchant(v: String) = _state.update { it.copy(merchant = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setTags(v: String) = _state.update { it.copy(tags = v) }
    fun setGoldGrams(v: String) = _state.update { it.copy(goldGrams = v) }

    private var busy = false

    /** Returns true only for the call that actually saved; repeat taps while saving are ignored. */
    suspend fun save(): Boolean {
        if (busy || done) return false
        busy = true
        try {
            return doSave()
        } finally {
            busy = false
        }
    }

    private suspend fun doSave(): Boolean {
        val s = _state.value
        val src = s.sourceAccountId ?: run { _state.update { it.copy(error = t("Tambahkan akun dulu")) }; return false }
        val gold = if (s.showGold && s.goldGrams.isNotBlank()) {
            Grams.parse(s.goldGrams) ?: run { _state.update { it.copy(error = t("Format gram tidak valid")) }; return false }
        } else null
        val amount = if (s.type == TxType.ADJUSTMENT && s.adjustmentNegative) -s.amount.value else s.amount.value
        val tx = Tx(
            id = txId, type = s.type, amount = amount,
            categoryId = if (s.showCategory) s.categoryId else null,
            sourceAccountId = src, destinationAccountId = if (s.showDestination) s.destinationAccountId else null,
            goldTenThousandths = gold, date = s.date,
            description = s.description.trim().ifEmpty { null }, merchant = s.merchant.trim().ifEmpty { null },
            notes = s.notes.trim().ifEmpty { null }, tags = Tags.normalize(s.tags),
        )
        val before = if (s.type == TxType.EXPENSE) c.transactions.observeAll().first().filter { it.id != txId } else emptyList()
        val r = c.transactions.save(tx)
        r.exceptionOrNull()?.let { e -> _state.update { it.copy(error = e.message) } }
        if (r.isSuccess) { done = true; overBudget = overBudgetAfter(before, tx) }
        return r.isSuccess
    }

    /** Set once saved or deleted, so a late second tap can't write or navigate again. */
    var done = false
        private set

    /** Set after saving an expense that pushed a budget bucket past 100% (shown as a warning). */
    var overBudget: id.dompetku.domain.BucketUse? = null
        private set

    private suspend fun overBudgetAfter(before: List<Tx>, tx: Tx): id.dompetku.domain.BucketUse? {
        if (tx.type != TxType.EXPENSE) return null
        val budget = c.budgets.current(c.budgets.observeAll().first(), tx.date) ?: return null
        val cats = c.categories.observeAll().first().map { id.dompetku.domain.CategoryInfo(it.id, it.parentId, it.bucket) }
        val accounts = c.accounts.observeAll().first().associate { it.id to it.type }
        return id.dompetku.domain.Budgets.newlyOver(budget, before, tx, id.dompetku.domain.Budgets.bucketMap(cats), accounts)
    }

    suspend fun delete() {
        if (done || busy || txId == 0L) return
        busy = true
        try { c.transactions.delete(txId); done = true } finally { busy = false }
    }
}

/** Tags are stored lowercase, space-separated, without "#": "#Liburan, #bali" → "liburan bali". */
object Tags {
    fun normalize(input: String): String? = input.split(Regex("""[\s,]+""")).map { it.trim().removePrefix("#").lowercase() }
        .filter { it.isNotEmpty() }.distinct().joinToString(" ").ifEmpty { null }

    fun display(stored: String?): String = stored?.split(' ')?.filter { it.isNotEmpty() }?.joinToString(" ") { "#$it" }.orEmpty()
}
