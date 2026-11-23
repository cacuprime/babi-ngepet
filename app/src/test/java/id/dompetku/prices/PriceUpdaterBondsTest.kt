package id.dompetku.prices

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.data.db.HoldingEntity
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class PriceUpdaterBondsTest {
    private lateinit var c: AppContainer
    private val run = Instant.parse("2026-11-15T11:00:00Z")
    private fun fixture(n: String) = javaClass.classLoader!!.getResource("prices/$n")!!.readText()

    private val http = object : Http {
        override suspend fun get(url: String): String = when {
            url.contains("Bond/GetAll") -> fixture("pasardana_bonds_sample.json")
            url.contains("BBCA.JK") -> fixture("yahoo_bbca.json")
            else -> throw java.io.IOException("offline")
        }
    }

    @Before fun setUp() = runBlocking<Unit> {
        c = AppContainer.inMemory(ApplicationProvider.getApplicationContext())
        c.settings.setAutoPrices(true)
    }
    @After fun tearDown() { c.db.close() }

    private suspend fun bibit(opening: Long) = c.accounts.create(Account(0, "Bibit", AccountType.INVESTMENT, null, opening, false, false, Instant.EPOCH))
    private suspend fun value(id: Long) = c.dashboard.accountViews().first().single { it.account.id == id }.currentValue
    private suspend fun bond(acct: Long, code: String, units: Long, cost: Long?) =
        c.holdings.save(HoldingEntity(0, acct, "BOND", code, code, units * 10_000, null, null, null, 0, costRp = cost)).getOrThrow()

    @Test fun bondsPricedDaily() = runBlocking {
        val b = bibit(1_962_500)
        bond(b, "FR0100", 2, 1_962_500)
        PriceUpdater(c, http) { run }.run()
        // 2 × Rp1.000.000 × 97,5% + RDN (1.962.500 − 1.962.500 = 0)
        assertEquals(1_950_000L, value(b))
    }

    @Test fun retailSbnAtPar() = runBlocking {
        val b = bibit(2_000_000)
        bond(b, "ORI025", 2, 2_000_000)
        PriceUpdater(c, http) { run }.run()
        assertEquals(2_000_000L, value(b))
        assertEquals(10_000_000_000L, c.holdings.all().single().priceE4)
    }

    @Test fun rdnIncludedInDailyValue() = runBlocking {
        val b = bibit(5_000_000)
        c.holdings.save(HoldingEntity(0, b, "STOCK", "BBCA", "BBCA", 3_000_000, null, null, null, 0, costRp = 1_620_000)).getOrThrow()
        PriceUpdater(c, http) { run }.run()
        val bbca = Valuation.holdingValue(3_000_000, PriceParsers.toE4(PriceParsers.yahoo(fixture("yahoo_bbca.json")).getOrThrow().first))
        assertEquals(bbca + 3_380_000L, value(b))
    }
}
