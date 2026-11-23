package id.dompetku.data

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.domain.AccountType
import id.dompetku.domain.Period
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class StartingDataTest {
    private val json = """
        [
          {"name":"BSI Easy Wadiah","type":"BANK","provider":"BSI","balance":10000000},
          {"name":"SeaBank Deposito","type":"DEPOSIT","provider":"SeaBank","balance":25000000},
          {"name":"Tabungan Emas","type":"GOLD","provider":"Pegadaian","balance":24507000,"grams":"10,5000","goldPricePerGram":2334000},
          {"name":"Bibit","type":"INVESTMENT","provider":"Bibit","balance":15000000,"value":15000000}
        ]
    """.trimIndent()

    @Test fun parsesGoldEntry() {
        val gold = StartingData.parse(json).single { it.type == AccountType.GOLD }
        assertEquals(105_000L, gold.goldTenThousandths)
        assertEquals(2_334_000L, gold.goldPricePerGram)
    }

    @Test fun applyCreatesAccountsAndSnapshots() = runBlocking {
        val c = AppContainer.inMemory(ApplicationProvider.getApplicationContext())
        val now = Instant.parse("2026-11-11T03:00:00Z")
        StartingData.apply(StartingData.parse(json), c, now)
        val s = c.dashboard.summary(Period.thisMonth(now)).first()
        assertEquals(10_000_000L + 25_000_000L + 24_507_000L + 15_000_000L, s.totalAssets)
        assertEquals(10_000_000L, s.liquid)
        assertEquals(24_507_000L + 15_000_000L, s.investmentValue)
        val deposit = c.accounts.observeAll().first().single { it.type == AccountType.DEPOSIT }
        assertFalse(deposit.isLiquid)
        c.db.close()
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeBalance() {
        StartingData.parse("""[{"name":"X","type":"BANK","balance":-5}]""")
    }
}
