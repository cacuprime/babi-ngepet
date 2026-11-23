package id.dompetku.prices

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.data.db.HoldingEntity
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** Regression tests for the 1.4 final-review findings. */
@RunWith(RobolectricTestRunner::class)
class Release14FixesTest {
    private lateinit var c: AppContainer
    private fun fixture(n: String) = javaClass.classLoader!!.getResource("prices/$n")!!.readText()

    @Before fun setUp() = runBlocking<Unit> {
        c = AppContainer.inMemory(ApplicationProvider.getApplicationContext())
        c.settings.setAutoPrices(true)
    }
    @After fun tearDown() { c.db.close() }

    private fun h(units: Long, price: Long, cost: Long) = HoldingEntity(0, 1, "STOCK", "X", "X", units, price, null, null, 0, costRp = cost)

    // I1: selling at a profit keeps the profit in the account.
    @Test fun saleProceedsKeepProfit() {
        val ledger = 5_000_000L
        val before = Valuation.accountValue(listOf(h(10_000_000, 13_000_000, 1_000_000)), ledger) { 0 }!! // worth 1,3 jt, cost 1 jt
        val adj = Valuation.saleAdjustment(proceeds = 1_300_000, modalRemoved = 1_000_000)
        assertEquals(300_000L, adj)
        // After deleting the holding the account holds only RDN = ledger + adjustment − 0 modal.
        assertEquals(before, Valuation.rdnCash(ledger + adj, emptyList()))
        // Partial sale: half the lots sold for 650 rb, modal drops by 500 rb.
        assertEquals(150_000L, Valuation.saleAdjustment(650_000, 500_000))
    }

    private val http = object : Http {
        override suspend fun get(url: String): String = if (url.contains("Bond/GetAll")) fixture("pasardana_bonds_sample.json") else throw java.io.IOException("offline")
    }

    // I2: an unknown FR series is valued at 100% but flagged.
    @Test fun unknownFrSeriesIsFlagged() = runBlocking {
        val b = c.accounts.create(Account(0, "Bibit", AccountType.INVESTMENT, null, 0, false, false, Instant.EPOCH))
        c.holdings.save(HoldingEntity(0, b, "BOND", "FR100", "FR100", 20_000, null, null, null, 0, costRp = 2_000_000)).getOrThrow()
        c.holdings.save(HoldingEntity(0, b, "BOND", "ORI025", "ORI025", 10_000, null, null, null, 0, costRp = 1_000_000)).getOrThrow()
        PriceUpdater(c, http) { Instant.parse("2026-11-15T11:00:00Z") }.run()
        val all = c.holdings.all()
        val typo = all.single { it.code == "FR100" }; val ori = all.single { it.code == "ORI025" }
        assertEquals(Bonds.unitPriceE4(100.0), typo.priceE4); assertNotNull(typo.priceError)
        assertEquals(Bonds.unitPriceE4(100.0), ori.priceE4); assertNull(ori.priceError)
    }

    // Upgraded minor: entering modal (same code/kind) keeps the current price.
    @Test fun editingModalKeepsPrice() = runBlocking {
        val b = c.accounts.create(Account(0, "Bibit", AccountType.INVESTMENT, null, 0, false, false, Instant.EPOCH))
        val id = c.holdings.save(HoldingEntity(0, b, "STOCK", "BBCA", "BBCA", 3_000_000, null, null, null, 0)).getOrThrow()
        c.holdings.setPrice(id, 55_000_000, 1_000)
        c.holdings.save(HoldingEntity(id, b, "STOCK", "BBCA", "BBCA", 3_000_000, null, null, null, 0, costRp = 1_620_000)).getOrThrow()
        assertEquals(55_000_000L, c.holdings.all().single().priceE4)
        // A different code is a different holding: the old price must not carry over.
        c.holdings.save(HoldingEntity(id, b, "STOCK", "TLKM", "TLKM", 3_000_000, null, null, null, 0, costRp = 1_620_000)).getOrThrow()
        assertNull(c.holdings.all().single().priceE4)
    }
}
