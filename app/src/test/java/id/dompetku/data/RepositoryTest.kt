package id.dompetku.data

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
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

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var c: AppContainer
    private val now = Instant.parse("2026-11-11T03:00:00Z")

    @Before fun setUp() { c = AppContainer.inMemory(ApplicationProvider.getApplicationContext()) }
    @After fun tearDown() { c.db.close() }

    private suspend fun bank(name: String, opening: Long) =
        c.accounts.create(Account(0, name, AccountType.BANK, null, opening, true, false, now))

    private suspend fun expenseCat() = c.categories.observe("EXPENSE").first().first().id

    private fun tx(type: TxType, amount: Long, src: Long, dst: Long? = null, cat: Long? = null, id: Long = 0, merchant: String? = null) =
        Tx(id, type, amount, cat, src, dst, null, now, null, merchant, null)

    private suspend fun balances() = c.dashboard.accountViews().first().associate { it.account.id to it.balance }

    @Test fun defaultCategoriesSeeded() = runBlocking {
        assertEquals(13, c.categories.observe("EXPENSE").first().size)
        assertEquals(7, c.categories.observe("INCOME").first().size)
    }

    @Test fun editTransferMovesBalances() = runBlocking {
        val a = bank("A", 1_000_000); val b = bank("B", 0); val cc = bank("C", 0)
        val id = c.transactions.save(tx(TxType.TRANSFER, 100_000, a, b)).getOrThrow()
        c.transactions.save(tx(TxType.TRANSFER, 150_000, a, cc, id = id)).getOrThrow()
        val bal = balances()
        assertEquals(850_000L, bal[a]); assertEquals(0L, bal[b]); assertEquals(150_000L, bal[cc])
    }

    @Test fun changingTypeFromTransferToExpense() = runBlocking {
        val a = bank("A", 1_000_000); val b = bank("B", 0)
        val id = c.transactions.save(tx(TxType.TRANSFER, 100_000, a, b)).getOrThrow()
        c.transactions.save(tx(TxType.EXPENSE, 100_000, a, null, expenseCat(), id = id)).getOrThrow()
        val bal = balances()
        assertEquals(900_000L, bal[a]); assertEquals(0L, bal[b])
    }

    @Test fun deleteTransactionRestoresBalance() = runBlocking {
        val a = bank("A", 1_000_000)
        val id = c.transactions.save(tx(TxType.EXPENSE, 35_000, a, cat = expenseCat())).getOrThrow()
        assertEquals(965_000L, balances()[a])
        c.transactions.delete(id)
        assertEquals(1_000_000L, balances()[a])
    }

    @Test fun deleteAccountWithTransactionsFails() = runBlocking {
        val a = bank("A", 1_000_000)
        c.transactions.save(tx(TxType.EXPENSE, 35_000, a, cat = expenseCat())).getOrThrow()
        assertTrue(c.accounts.delete(a).isFailure)
        val empty = bank("B", 0)
        assertTrue(c.accounts.delete(empty).isSuccess)
    }

    @Test fun transferToSameAccountRejected() = runBlocking {
        val a = bank("A", 1_000_000)
        assertTrue(c.transactions.save(tx(TxType.TRANSFER, 1_000, a, a)).isFailure)
        assertTrue(c.transactions.save(tx(TxType.TRANSFER, 1_000, a, null)).isFailure)
        assertTrue(c.transactions.save(tx(TxType.EXPENSE, 0, a, cat = expenseCat())).isFailure)
        assertTrue(c.transactions.save(tx(TxType.EXPENSE, 1_000, a, cat = null)).isFailure)
    }

    @Test fun searchMatchesMerchantCaseInsensitive() = runBlocking {
        val a = bank("A", 1_000_000)
        c.transactions.save(tx(TxType.EXPENSE, 50_000, a, cat = expenseCat(), merchant = "Indomaret")).getOrThrow()
        c.transactions.save(tx(TxType.EXPENSE, 20_000, a, cat = expenseCat(), merchant = "Gojek")).getOrThrow()
        val found = c.transactions.observe(TxFilter(query = "indo")).first()
        assertEquals(listOf("Indomaret"), found.map { it.merchant })
    }

    @Test fun deleteUsedCategoryFails() = runBlocking {
        val a = bank("A", 1_000_000)
        val cat = expenseCat()
        c.transactions.save(tx(TxType.EXPENSE, 50_000, a, cat = cat)).getOrThrow()
        assertTrue(c.categories.delete(cat).isFailure)
    }
}
