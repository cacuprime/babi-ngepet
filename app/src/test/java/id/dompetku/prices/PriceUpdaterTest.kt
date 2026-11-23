package id.dompetku.prices

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.core.Grams
import id.dompetku.data.db.HoldingEntity
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.Snapshot
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
class PriceUpdaterTest {
    private lateinit var c: AppContainer
    private val now = Instant.parse("2026-11-15T11:00:00Z")
    private fun fixture(n: String) = javaClass.classLoader!!.getResource("prices/$n")!!.readText()
    private val bbcaPrice by lazy { PriceParsers.yahoo(fixture("yahoo_bbca.json")).getOrThrow().first }

    /** Fake network: URL substring → response; anything else fails like being offline. */
    private class FakeHttp(val routes: Map<String, String>) : Http {
        val calls = mutableListOf<String>()
        override suspend fun get(url: String): String {
            calls += url
            return routes.entries.firstOrNull { url.contains(it.key) }?.value ?: throw java.io.IOException("offline")
        }
    }

    private val http by lazy {
        FakeHttp(mapOf(
            "BBCA.JK" to fixture("yahoo_bbca.json"),
            "TLKM.JK" to fixture("yahoo_bbca.json"),
            "ZZZZ.JK" to fixture("yahoo_notfound.json"),
            "pasardana" to fixture("pasardana_sample.json"),
            "pegadaian" to fixture("pegadaian_gold.json"),
        ))
    }

    @Before fun setUp() = runBlocking<Unit> {
        c = AppContainer.inMemory(ApplicationProvider.getApplicationContext())
        c.settings.setAutoPrices(true)
    }
    @After fun tearDown() { c.db.close() }

    private suspend fun invest(name: String) = c.accounts.create(Account(0, name, AccountType.INVESTMENT, null, 0, false, false, Instant.EPOCH))
    private suspend fun stock(acct: Long, code: String, lots: Long) =
        c.holdings.save(HoldingEntity(0, acct, "STOCK", code, code, lots * 100 * 10_000, null, null, null, 0)).getOrThrow()
    private suspend fun value(acct: Long) = c.dashboard.accountViews().first().single { it.account.id == acct }.currentValue
    private suspend fun autoToday(acct: Long) = c.snapshots.observeHistory(acct).first().count { it.date == now }

    @Test fun updatesStocksAndWritesSnapshot() = runBlocking<Unit> {
        val bibit = invest("Bibit")
        stock(bibit, "BBCA", 3)
        val r = PriceUpdater(c, http) { now }.run()
        assertEquals(1, r.updatedAccounts)
        assertEquals(Valuation.holdingValue(3_000_000, PriceParsers.toE4(bbcaPrice)), value(bibit))
        assertTrue(c.holdings.all().single().priceAt != null)
    }

    @Test fun fundsUseNavFromPasardana() = runBlocking<Unit> {
        val bibit = invest("Bibit")
        c.holdings.save(HoldingEntity(0, bibit, "FUND", "1106", "Mandiri Investa Pasar Uang Kelas A", 10_000_000, null, null, null, 0)).getOrThrow()
        PriceUpdater(c, http) { now }.run()
        assertEquals(1_864_940L, value(bibit)) // 1000 units × 1864.94
    }

    @Test fun partialFailureSkipsAccount() = runBlocking<Unit> {
        val bibit = invest("Bibit"); val other = invest("Stockbit")
        c.snapshots.add(Snapshot(bibit, Instant.parse("2026-11-01T00:00:00Z"), 4_000_000, null, null))
        stock(bibit, "BBCA", 3); stock(bibit, "ZZZZ", 1); stock(other, "TLKM", 5)
        val r = PriceUpdater(c, http) { now }.run()
        assertEquals(4_000_000L, value(bibit)) // kept the last value, no partial snapshot
        assertEquals(1, r.updatedAccounts)
        assertTrue(r.errors.any { it.contains("ZZZZ") })
        assertEquals("Kode saham tidak ditemukan", c.holdings.all().single { it.code == "ZZZZ" }.priceError)
    }

    @Test fun noHoldingsNoAutoSnapshot() = runBlocking<Unit> {
        val bibit = invest("Bibit")
        c.snapshots.add(Snapshot(bibit, Instant.parse("2026-11-01T00:00:00Z"), 15_000_000, null, null))
        PriceUpdater(c, http) { now }.run()
        assertEquals(15_000_000L, value(bibit))
        assertEquals(0, autoToday(bibit))
    }

    @Test fun oneAutoSnapshotPerDay() = runBlocking<Unit> {
        val bibit = invest("Bibit")
        stock(bibit, "BBCA", 3)
        PriceUpdater(c, http) { now }.run()
        PriceUpdater(c, http) { now.plusSeconds(600) }.run()
        assertEquals(1, c.snapshots.observeHistory(bibit).first().size)
    }

    @Test fun goldUsesPegadaianPrice() = runBlocking<Unit> {
        val gold = c.accounts.create(Account(0, "Tabungan Emas", AccountType.GOLD, null, 25_000_000, false, false, Instant.EPOCH))
        c.snapshots.add(Snapshot(gold, Instant.parse("2026-11-11T00:00:00Z"), Grams.valueOf(105_000, 2_334_000), 105_000, 2_334_000))
        PriceUpdater(c, http) { now }.run()
        assertEquals(Grams.valueOf(105_000, 2_297_000), value(gold))
        assertEquals(2_297_000L, c.snapshots.observeLatest().first()[gold]!!.goldPricePerGram)
    }

    @Test fun autoPricesOffDoesNothing() = runBlocking<Unit> {
        val bibit = invest("Bibit")
        stock(bibit, "BBCA", 3)
        c.settings.setAutoPrices(false)
        val r = PriceUpdater(c, http) { now }.run()
        assertTrue(r.skipped)
        assertTrue(http.calls.isEmpty())
        c.settings.setAutoPrices(true)
    }
}
