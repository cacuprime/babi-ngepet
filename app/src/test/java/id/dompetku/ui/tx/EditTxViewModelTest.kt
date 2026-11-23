package id.dompetku.ui.tx

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class EditTxViewModelTest {
    private lateinit var c: AppContainer
    private val now = Instant.parse("2026-11-11T03:00:00Z")

    @Before fun setUp() { c = AppContainer.inMemory(ApplicationProvider.getApplicationContext()) }
    @After fun tearDown() { c.db.close() }

    private suspend fun bank(name: String) =
        c.accounts.create(Account(0, name, AccountType.BANK, null, 1_000_000, true, false, now))

    @Test fun keypadCapsAndRejectsZero() {
        var a = AmountInput()
        a = a.press('0').press('0')
        assertEquals(0L, a.value)
        repeat(14) { a = a.press('9') }
        assertEquals(9_999_999_999_999L, a.value)
        a = AmountInput().press000()
        assertEquals(0L, a.value)
        a = AmountInput().press('5').press000().backspace()
        assertEquals(500L, a.value)
    }

    @Test fun savingZeroShowsError() = runBlocking {
        bank("A")
        val vm = EditTxViewModel(c, 0) { now }
        vm.load()
        assertFalse(vm.save())
        assertEquals("Nominal harus lebih dari 0", vm.state.value.error)
    }

    @Test fun defaultsToLastUsedAccount() = runBlocking {
        bank("A"); val b = bank("B")
        val cat = c.categories.observe("EXPENSE").first().first().id
        c.transactions.save(Tx(0, TxType.EXPENSE, 10_000, cat, b, null, null, now, null, null, null)).getOrThrow()
        val vm = EditTxViewModel(c, 0) { now }
        vm.load()
        assertEquals(b, vm.state.value.sourceAccountId)
        assertEquals(cat, vm.state.value.categories.first().id)
    }

    @Test fun transferHidesCategory() = runBlocking {
        bank("A"); bank("B")
        val vm = EditTxViewModel(c, 0) { now }
        vm.load()
        vm.setType(TxType.TRANSFER)
        assertFalse(vm.state.value.showCategory)
        assertTrue(vm.state.value.showDestination)
    }

    @Test fun quickExpenseSaves() = runBlocking {
        val a = bank("A")
        val vm = EditTxViewModel(c, 0) { now }
        vm.load()
        "35000".forEach { vm.press(it) }
        vm.setCategory(vm.state.value.categories.first().id)
        assertTrue(vm.save())
        val views = c.dashboard.accountViews().first()
        assertEquals(965_000L, views.single { it.account.id == a }.balance)
    }

    @Test fun doubleSaveInsertsOnce() = runBlocking {
        bank("A")
        val vm = EditTxViewModel(c, 0) { now }
        vm.load()
        "5000".forEach { vm.press(it) }
        vm.setCategory(vm.state.value.categories.first().id)
        val results = listOf(async { vm.save() }, async { vm.save() }).awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(1, c.transactions.observeAll().first().size)
    }

    @Test fun keypadShownForNewHiddenForEdit() = runBlocking {
        val a = bank("A")
        val vm = EditTxViewModel(c, 0) { now }
        vm.load()
        assertTrue(vm.state.value.keypadVisible)
        val cat = c.categories.observe("EXPENSE").first().first().id
        val id = c.transactions.save(Tx(0, TxType.EXPENSE, 10_000, cat, a, null, null, now, null, null, null)).getOrThrow()
        val edit = EditTxViewModel(c, id) { now }
        edit.load()
        assertFalse(edit.state.value.keypadVisible)
    }

    @Test fun showAndHideKeypad() = runBlocking {
        bank("A")
        val vm = EditTxViewModel(c, 0) { now }
        vm.load()
        vm.hideKeypad(); assertFalse(vm.state.value.keypadVisible)
        vm.showKeypad(); assertTrue(vm.state.value.keypadVisible)
    }

    @Test fun editLoadsExisting() = runBlocking {
        val a = bank("A"); val b = bank("B")
        val id = c.transactions.save(Tx(0, TxType.TRANSFER, 250_000, null, a, b, null, now, "Top up", null, null)).getOrThrow()
        val vm = EditTxViewModel(c, id) { now }
        vm.load()
        val s = vm.state.value
        assertTrue(s.isEdit)
        assertEquals(TxType.TRANSFER, s.type)
        assertEquals(250_000L, s.amount.value)
        assertEquals(b, s.destinationAccountId)
        assertEquals("Top up", s.description)
    }
}
