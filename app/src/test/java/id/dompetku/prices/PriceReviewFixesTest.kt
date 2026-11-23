package id.dompetku.prices

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.core.Grams
import id.dompetku.core.Units
import id.dompetku.data.db.HoldingEntity
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.Snapshot
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

/** Regression tests for the 1.3 final-review findings. */
@RunWith(RobolectricTestRunner::class)
class PriceReviewFixesTest {
    private lateinit var c: AppContainer
    private fun fixture(n: String) = javaClass.classLoader!!.getResource("prices/$n")!!.readText()
    private val day1 = Instant.parse("2026-11-11T05:00:00Z")
    private val day3 = Instant.parse("2026-11-13T11:00:00Z")
    private val run = Instant.parse("2026-11-15T11:00:00Z")

    private class FakeHttp(val routes: Map<String, () -> String>) : Http {
        override suspend fun get(url: String): String =
            (routes.entries.firstOrNull { url.contains(it.key) }?.value ?: { throw java.io.IOException("offline") })()
    }
    private val http by lazy {
        FakeHttp(mapOf(
            "BBCA.JK" to { fixture("yahoo_bbca.json") },
            "ZZZZ.JK" to { throw HttpStatusException(404, fixture("yahoo_notfound.json")) },
            "pegadaian" to { fixture("pegadaian_gold.json") },
        ))
    }

    @Before fun setUp() = runBlocking<Unit> {
        c = AppContainer.inMemory(ApplicationProvider.getApplicationContext())
        c.settings.setAutoPrices(true)
    }
    @After fun tearDown() { c.db.close() }

    private suspend fun value(id: Long) = c.dashboard.accountViews().first().single { it.account.id == id }.currentValue
    private suspend fun bank() = c.accounts.create(Account(0, "BSI", AccountType.BANK, null, 50_000_000, true, false, Instant.EPOCH))
    private suspend fun goldAccount(): Long {
        val g = c.accounts.create(Account(0, "Emas", AccountType.GOLD, null, 25_000_000, false, false, Instant.EPOCH))
        c.snapshots.add(Snapshot(g, day1, Grams.valueOf(105_000, 2_334_000), 105_000, 2_334_000))
        return g
    }
    private suspend fun transfer(from: Long, to: Long, amount: Long, at: Instant, grams: Long? = null) =
        c.transactions.save(Tx(0, TxType.TRANSFER, amount, null, from, to, grams, at, null, null, null)).getOrThrow()

    // C1
    @Test fun goldTransferWithoutGramsSurvivesAutoRun() = runBlocking {
        val b = bank(); val g = goldAccount()
        transfer(b, g, 2_000_000, day3)
        PriceUpdater(c, http) { run }.run()
        assertEquals(Grams.valueOf(105_000, 2_297_000) + 2_000_000, value(g))
    }

    // I2
    @Test fun backdatedGoldPurchaseCountedAfterNextRun() = runBlocking {
        val b = bank(); val g = goldAccount()
        PriceUpdater(c, http) { day3 }.run()
        transfer(b, g, 2_300_000, Instant.parse("2026-11-12T05:00:00Z"), grams = 10_000) // 1 g, entered later, dated before
        PriceUpdater(c, http) { run }.run()
        assertEquals(Grams.valueOf(115_000, 2_297_000), value(g))
    }

    // I3
    @Test fun futureDatedTransferNotDoubleCounted() = runBlocking {
        val b = bank()
        val bibit = c.accounts.create(Account(0, "Bibit", AccountType.INVESTMENT, null, 0, false, false, Instant.EPOCH))
        c.holdings.save(HoldingEntity(0, bibit, "STOCK", "BBCA", "BBCA", 3_000_000, null, null, null, 0)).getOrThrow()
        transfer(b, bibit, 500_000, run.plusSeconds(86_400 * 3))
        PriceUpdater(c, http) { run }.run()
        val price = PriceParsers.toE4(PriceParsers.yahoo(fixture("yahoo_bbca.json")).getOrThrow().first)
        assertEquals(Valuation.holdingValue(3_000_000, price) + 500_000, value(bibit))
    }

    // I5
    @Test fun unknownTickerSaysNotFound() = runBlocking {
        val bibit = c.accounts.create(Account(0, "Bibit", AccountType.INVESTMENT, null, 0, false, false, Instant.EPOCH))
        c.holdings.save(HoldingEntity(0, bibit, "STOCK", "ZZZZ", "ZZZZ", 1_000_000, null, null, null, 0)).getOrThrow()
        PriceUpdater(c, http) { run }.run()
        assertEquals("Kode saham tidak ditemukan", c.holdings.all().single().priceError)
    }

    // I6
    @Test fun fundUnitsIndonesianFormat() {
        assertEquals(12_345_678L, Units.parse("1.234,5678"))
        assertEquals(12_340_000L, Units.parse("1.234"))
        assertEquals(125_000L, Units.parse("12,5"))
        assertEquals(12_345_678L, Units.parse("1234.5678"))
        assertEquals(null, Units.parse("1,2,3"))
        assertEquals("1234,5678", Units.plain(12_345_678))
        assertEquals("1234", Units.plain(12_340_000))
    }

    // I7
    @Test fun nonIdrQuoteRejected() {
        val usd = fixture("yahoo_bbca.json").replace("\"currency\":\"IDR\"", "\"currency\":\"USD\"")
        assertTrue(PriceParsers.yahoo(usd).isFailure)
    }

    @Test fun absurdPegadaianPriceRejected() {
        val perGramFormat = fixture("pegadaian_gold.json").replace("\"hargaBeli\":22970", "\"hargaBeli\":2297000")
        assertTrue(PriceParsers.pegadaian(perGramFormat).isFailure)
    }

    @Test fun hugeStockJumpIsFlaggedNotApplied() = runBlocking {
        val bibit = c.accounts.create(Account(0, "Bibit", AccountType.INVESTMENT, null, 0, false, false, Instant.EPOCH))
        val id = c.holdings.save(HoldingEntity(0, bibit, "STOCK", "BBCA", "BBCA", 3_000_000, null, null, null, 0)).getOrThrow()
        c.holdings.setPrice(id, 10_000, 0) // Rp1 per share stored → today's real price is a >50% jump
        c.snapshots.add(Snapshot(bibit, day1, 777, null, null))
        val r = PriceUpdater(c, http) { run }.run()
        assertEquals(777L, value(bibit))
        assertTrue(r.errors.any { it.contains("BBCA") })
    }
}
