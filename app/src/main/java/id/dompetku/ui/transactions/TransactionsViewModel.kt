package id.dompetku.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.dompetku.AppContainer
import id.dompetku.data.TxFilter
import id.dompetku.data.db.CategoryEntity
import id.dompetku.domain.Account
import id.dompetku.domain.Tx
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/** Transactions grouped by local day, newest day first; within a day newest first. */
fun groupByDay(txs: List<Tx>, zone: ZoneId): List<Pair<LocalDate, List<Tx>>> =
    txs.groupBy { it.date.atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .map { (d, l) -> d to l.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id }) }

data class TransactionsState(
    val filter: TxFilter = TxFilter(),
    val groups: List<Pair<LocalDate, List<Tx>>> = emptyList(),
    val accounts: Map<Long, Account> = emptyMap(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(private val c: AppContainer, initial: TxFilter = TxFilter()) : ViewModel() {
    private val filter = MutableStateFlow(initial)

    val state: StateFlow<TransactionsState> = filter.flatMapLatest { f ->
        combine(c.transactions.observe(f), c.accounts.observeAll(), c.categories.observeAll()) { txs, accs, cats ->
            TransactionsState(
                f, groupByDay(txs, id.dompetku.domain.JAKARTA),
                accs.associateBy { it.id }, cats.associateBy { it.id }, true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsState())

    fun update(f: (TxFilter) -> TxFilter) { filter.value = f(filter.value) }
}
