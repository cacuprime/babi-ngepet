package id.dompetku.data

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.data.db.HoldingEntity
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class HoldingRepositoryTest {
    private lateinit var c: AppContainer
    private var bibit = 0L

    @Before fun setUp() = runBlocking {
        c = AppContainer.inMemory(ApplicationProvider.getApplicationContext())
        bibit = c.accounts.create(Account(0, "Bibit", AccountType.INVESTMENT, null, 0, false, false, Instant.EPOCH))
    }
    @After fun tearDown() { c.db.close() }

    private fun stock(code: String, lots: Long) =
        HoldingEntity(0, bibit, "STOCK", code, code, lots * 100 * 10_000, null, null, null, 0)

    @Test fun saveAndObserve() = runBlocking {
        val id = c.holdings.save(stock("BBCA", 3)).getOrThrow()
        val h = c.holdings.observe(bibit).first().single()
        assertEquals(id, h.id); assertEquals(3_000_000L, h.unitsE4)
        assertTrue(h.updatedAt > 0)
    }

    @Test fun rejectsBlankCodeOrZeroUnits() = runBlocking {
        assertTrue(c.holdings.save(stock(" ", 1)).isFailure)
        assertTrue(c.holdings.save(stock("BBCA", 0)).isFailure)
    }

    @Test fun priceAndErrorAreStoredWithoutTouchingUnitsDate() = runBlocking {
        val id = c.holdings.save(stock("BBCA", 3)).getOrThrow()
        val before = c.holdings.observe(bibit).first().single().updatedAt
        c.holdings.setPrice(id, 70_000_000, 1_000)
        var h = c.holdings.observe(bibit).first().single()
        assertEquals(70_000_000L, h.priceE4); assertEquals(1_000L, h.priceAt); assertNull(h.priceError)
        assertEquals(before, h.updatedAt)
        c.holdings.setError(id, "Kode saham tidak ditemukan")
        h = c.holdings.observe(bibit).first().single()
        assertEquals("Kode saham tidak ditemukan", h.priceError); assertEquals(70_000_000L, h.priceE4)
    }

    @Test fun costStored() = runBlocking {
        c.holdings.save(stock("BBCA", 3).copy(costRp = 1_620_000)).getOrThrow()
        assertEquals(1_620_000L, c.holdings.observe(bibit).first().single().costRp)
    }

    @Test fun deletingAccountRemovesHoldings() = runBlocking {
        c.holdings.save(stock("BBCA", 3)).getOrThrow()
        c.accounts.delete(bibit).getOrThrow()
        assertEquals(0, c.holdings.observeAll().first().size)
    }
}
