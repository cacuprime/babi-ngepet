package id.dompetku.importer

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.TxType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ImportViewModelTest {
    private lateinit var c: AppContainer
    private val now = Instant.parse("2026-11-15T03:00:00Z")
    private var bank = 0L

    @Before fun setUp() = runBlocking {
        c = AppContainer.inMemory(ApplicationProvider.getApplicationContext())
        bank = c.accounts.create(Account(0, "SeaBank", AccountType.BANK, null, 1_000_000, true, false, now))
    }
    @After fun tearDown() { c.db.close() }

    private fun d(type: TxType, amount: Long, merchant: String, ref: String? = null, iso: String = "2026-11-10T03:00:00Z") =
        Draft(type, amount, Instant.parse(iso), merchant, merchant, ref, null, 0.9f)

    private val drafts = listOf(
        d(TxType.EXPENSE, 25_000, "GOJEK GORIDE", "R1"),
        d(TxType.EXPENSE, 18_000, "Sumber Rejeki Abadi", "R2"),
    )

    private suspend fun cat(name: String) = c.categories.observeAll().first().first { it.name == name }.id
    private suspend fun balance() = c.dashboard.accountViews().first().single { it.account.id == bank }.balance
    private suspend fun session() = ImportViewModel(c) { now }.also { it.accept(drafts, "STATEMENT", null) }

    @Test fun acceptSuggestsCategories() = runBlocking {
        val s = session().state.value
        assertEquals(ImportStage.REVIEW, s.stage)
        assertEquals(bank, s.accountId)
        assertEquals(cat("Transportasi"), s.rows[0].categoryId)
        assertNull(s.rows[1].categoryId)
        assertTrue(s.rows.all { it.selected })
    }

    @Test fun draftsDontTouchBalances() = runBlocking {
        session()
        assertEquals(1_000_000L, balance())
    }

    @Test fun confirmSavesSelectedOnly() = runBlocking {
        val vm = session()
        vm.toggle(vm.state.value.rows[1].key)
        assertEquals(1, vm.confirm())
        assertEquals(975_000L, balance())
        assertEquals(ImportStage.DONE, vm.state.value.stage)
    }

    @Test fun missingCategoryBlocksConfirm() = runBlocking {
        val vm = session()
        assertEquals(0, vm.confirm())
        assertNotNull(vm.state.value.message)
        assertEquals(1_000_000L, balance())
    }

    @Test fun secondImportAllDuplicates() = runBlocking {
        val vm = session()
        val food = cat("Makanan & Minuman")
        vm.edit(vm.state.value.rows[1].key) { it.copy(categoryId = food) }
        assertEquals(2, vm.confirm())
        val again = session().state.value
        assertTrue(again.rows.all { it.duplicateOf != null })
        assertTrue(again.rows.none { it.selected })
    }

    @Test fun doubleConfirmSavesOnce() = runBlocking {
        val vm = session()
        vm.toggle(vm.state.value.rows[1].key)
        val r = listOf(async { vm.confirm() }, async { vm.confirm() }).awaitAll()
        assertEquals(1, r.sum())
        assertEquals(1, c.transactions.observeAll().first().size)
    }

    @Test fun correctionIsLearned() = runBlocking {
        val vm = session()
        val food = cat("Makanan & Minuman")
        vm.edit(vm.state.value.rows[1].key) { it.copy(categoryId = food) }
        vm.confirm()
        val next = ImportViewModel(c) { now }
        next.accept(listOf(d(TxType.EXPENSE, 20_000, "SUMBER REJEKI ABADI CABANG 2", iso = "2026-11-12T03:00:00Z")), "SCREENSHOT", null)
        assertEquals(food, next.state.value.rows[0].categoryId)
        assertFalse(next.state.value.rows[0].duplicateOf != null)
    }
}
