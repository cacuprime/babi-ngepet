package id.dompetku.ui.home

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import id.dompetku.ui.transactions.groupByDay
import id.dompetku.domain.JAKARTA
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private lateinit var c: AppContainer
    private val now = Instant.parse("2026-11-11T03:00:00Z")

    @Before fun setUp() { c = AppContainer.inMemory(ApplicationProvider.getApplicationContext()) }
    @After fun tearDown() { c.db.close() }

    private suspend fun seed(): Long {
        val bank = c.accounts.create(Account(0, "BCA", AccountType.BANK, null, 5_000_000, true, false, now))
        c.accounts.create(Account(0, "Bibit", AccountType.INVESTMENT, null, 2_000_000, false, false, now))
        val inc = c.categories.observe("INCOME").first().first().id
        val exp = c.categories.observe("EXPENSE").first().first().id
        c.transactions.save(Tx(0, TxType.INCOME, 8_500_000, inc, bank, null, null, now, null, null, null)).getOrThrow()
        c.transactions.save(Tx(0, TxType.EXPENSE, 200_000, exp, bank, null, null,
            Instant.parse("2026-10-15T03:00:00Z"), null, null, null)).getOrThrow()
        return bank
    }

    @Test fun homeSummaryMatchesLedger() = runBlocking {
        seed()
        val s = HomeViewModel(c) { now }.homeFlow(PeriodChoice.THIS_MONTH).first()
        assertEquals(5_000_000L + 8_500_000L - 200_000L + 2_000_000L, s.summary.totalAssets)
        assertEquals(13_300_000L, s.summary.liquid)
        assertEquals(8_500_000L, s.summary.income)
        assertEquals(0L, s.summary.expense)
        assertEquals(2, s.recent.size)
    }

    @Test fun periodChipChangesIncome() = runBlocking {
        seed()
        val vm = HomeViewModel(c) { now }
        val last = vm.homeFlow(PeriodChoice.LAST_MONTH).first()
        assertEquals(0L, last.summary.income)
        assertEquals(200_000L, last.summary.expense)
    }

    @Test fun pagesOrderTotalGroupsAccounts() = runBlocking {
        val a = c.accounts.create(Account(0, "Mandiri", AccountType.BANK, null, 100, true, false, now))
        val b = c.accounts.create(Account(0, "BNI", AccountType.BANK, null, 200, true, false, now))
        c.groups.save("Dana harian", listOf(a, b)).getOrThrow()
        val pages = HomeViewModel(c) { now }.homeFlow(PeriodChoice.THIS_MONTH).first().pages
        assertEquals(4, pages.size)
        assertTrue(pages[0] is HomePage.Total)
        assertEquals(300L, (pages[1] as HomePage.Group).view.total)
        assertEquals(listOf(a, b), pages.drop(2).map { (it as HomePage.Single).view.account.id })
    }

    @Test fun archivedAccountsNotPaged() = runBlocking {
        val a = c.accounts.create(Account(0, "Mandiri", AccountType.BANK, null, 100, true, false, now))
        c.accounts.create(Account(0, "Lama", AccountType.BANK, null, 5, true, true, now))
        val pages = HomeViewModel(c) { now }.homeFlow(PeriodChoice.THIS_MONTH).first().pages
        assertEquals(listOf(a), pages.filterIsInstance<HomePage.Single>().map { it.view.account.id })
        assertEquals(105L, (pages[0] as HomePage.Total).summary.totalAssets)
    }

    @Test fun groupByDayNewestFirstJakarta() {
        fun t(id: Long, iso: String) = Tx(id, TxType.EXPENSE, 1, 1, 1, null, null, Instant.parse(iso), null, null, null)
        val groups = groupByDay(
            listOf(t(1, "2026-11-10T16:30:00Z"), t(2, "2026-11-10T10:00:00Z"), t(3, "2026-11-11T02:00:00Z")),
            JAKARTA,
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 10)), groups.map { it.first })
        // 16:30Z on the 10th is 23:30 WIB, still the 10th.
        assertEquals(listOf(3L), groups[0].second.map { it.id })
        assertEquals(listOf(1L, 2L), groups[1].second.map { it.id })
    }
}
