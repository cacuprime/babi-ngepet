package id.dompetku.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.dompetku.AppContainer
import id.dompetku.data.AccountView
import id.dompetku.data.GroupView
import id.dompetku.data.TxFilter
import id.dompetku.data.db.CategoryEntity
import id.dompetku.domain.Account
import id.dompetku.domain.Ledger
import id.dompetku.domain.Period
import id.dompetku.domain.Summary
import id.dompetku.domain.Tx
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

enum class PeriodChoice(val labelId: String) {
    TODAY("Hari ini"), THIS_WEEK("Minggu ini"), THIS_MONTH("Bulan ini"), LAST_MONTH("Bulan lalu");

    val label: String get() = id.dompetku.core.t(labelId)

    fun period(now: Instant): Period = when (this) {
        TODAY -> Period.today(now)
        THIS_WEEK -> Period.thisWeek(now)
        THIS_MONTH -> Period.thisMonth(now)
        LAST_MONTH -> Period.lastMonth(now)
    }
}

/** One swipeable card on Home: the total, an account group, or a single account. */
sealed interface HomePage {
    data class Total(val summary: Summary) : HomePage
    data class Group(val view: GroupView) : HomePage
    data class Single(val view: AccountView) : HomePage
}

data class HomeState(
    val choice: PeriodChoice = PeriodChoice.THIS_MONTH,
    val summary: Summary = Summary(0, 0, 0, 0, 0, 0),
    val pages: List<HomePage> = listOf(HomePage.Total(Summary(0, 0, 0, 0, 0, 0))),
    /** Every card in the owner's order, including hidden ones (for the "Atur kartu Home" screen). */
    val allPages: List<HomePage> = emptyList(),
    val homeHidden: Set<String> = emptySet(),
    val hidden: Boolean = false,
    val recent: List<Tx> = emptyList(),
    val accounts: Map<Long, Account> = emptyMap(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val loaded: Boolean = false,
)

private data class Base(val summary: Summary, val accounts: List<Account>, val recent: List<Tx>, val cats: List<CategoryEntity>)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer, private val now: () -> Instant = Instant::now) : ViewModel() {
    private val choice = MutableStateFlow(PeriodChoice.THIS_MONTH)

    val state: StateFlow<HomeState> = choice.flatMapLatest { homeFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun select(p: PeriodChoice) { choice.value = p }

    fun toggleHidden() {
        viewModelScope.launch { c.settings.setHideBalances(!c.settings.hideBalances.first()) }
    }

    fun homeFlow(p: PeriodChoice): Flow<HomeState> {
        val base = combine(
            c.accounts.observeAll(), c.transactions.observeAll(), c.snapshots.observeLatest(),
            c.transactions.observe(TxFilter()), c.categories.observeAll(),
        ) { accounts, txs, snaps, ordered, cats ->
            Base(Ledger.summary(accounts, txs, snaps, p.period(now())), accounts, ordered.take(5), cats)
        }
        val layout = combine(c.settings.homeOrder, c.settings.homeHidden) { o, h -> o to h }
        return combine(base, c.dashboard.groupViews(), c.dashboard.accountViews(), c.settings.hideBalances, layout) { b, groups, views, hidden, (order, homeHidden) ->
            val pages = buildList<HomePage> {
                add(HomePage.Total(b.summary))
                groups.sortedBy { it.group.id }.forEach { add(HomePage.Group(it)) }
                views.filter { !it.account.isArchived }.sortedBy { it.account.id }.forEach { add(HomePage.Single(it)) }
            }
            HomeState(
                choice = p, summary = b.summary, pages = HomeOrder.apply(pages, order, homeHidden),
                allPages = HomeOrder.apply(pages, order, emptySet()), homeHidden = homeHidden, hidden = hidden, recent = b.recent,
                accounts = b.accounts.associateBy { it.id }, categories = b.cats.associateBy { it.id }, loaded = true,
            )
        }
    }
}
