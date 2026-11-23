package id.dompetku.prices

import id.dompetku.data.db.HoldingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class PriceParsersTest {
    private fun fixture(n: String) = javaClass.classLoader!!.getResource("prices/$n")!!.readText()

    @Test fun yahooPriceAndTime() {
        val (price, at) = PriceParsers.yahoo(fixture("yahoo_bbca.json")).getOrThrow()
        val expected = Regex(""""regularMarketPrice":([0-9.]+)""").find(fixture("yahoo_bbca.json"))!!.groupValues[1].toDouble()
        assertEquals(expected, price, 0.0)
        assertTrue(at.isAfter(Instant.parse("2026-01-01T00:00:00Z")))
    }

    @Test fun yahooMissingPrice() {
        val r = PriceParsers.yahoo(fixture("yahoo_notfound.json"))
        assertTrue(r.isFailure)
        assertEquals("Kode saham tidak ditemukan", r.exceptionOrNull()!!.message)
        assertTrue(PriceParsers.yahoo("<html>rate limited</html>").isFailure)
    }

    @Test fun pasardanaPicksIds() {
        val m = PriceParsers.pasardana(fixture("pasardana_sample.json"), setOf("1106", "785", "999999"))
        assertEquals(setOf("1106", "785"), m.keys)
        assertEquals(1864.94, m["1106"]!!.first, 0.0)
        assertEquals(LocalDate.of(2026, 11, 13), m["1106"]!!.second.atOffset(ZoneOffset.ofHours(7)).toLocalDate())
    }

    @Test fun fundIndexListsNames() {
        val idx = PriceParsers.fundIndex(fixture("pasardana_sample.json"))
        assertEquals(3, idx.size)
        assertTrue(idx.any { it.id == "1106" && it.name == "Mandiri Investa Pasar Uang Kelas A" })
    }

    @Test fun pegadaianBuyback() {
        val (perGram, at) = PriceParsers.pegadaian(fixture("pegadaian_gold.json")).getOrThrow()
        assertEquals(2_297_000L, perGram)
        assertEquals(LocalDate.of(2026, 11, 15), at.atOffset(ZoneOffset.ofHours(7)).toLocalDate())
    }

    @Test fun pegadaianBadResponse() {
        assertTrue(PriceParsers.pegadaian("{}").isFailure)
        assertTrue(PriceParsers.pegadaian("""{"responseCode":"5000","data":null}""").isFailure)
    }

    @Test fun toE4Rounding() {
        assertEquals(18_649_400L, PriceParsers.toE4(1864.94))
        assertEquals(19_321_772L, PriceParsers.toE4(1932.1772))
        assertEquals(1L, PriceParsers.toE4(0.00005))
    }

    private fun h(units: Long, price: Long?, updatedAt: Long = 0) = HoldingEntity(0, 1, "STOCK", "X", "X", units, price, null, null, updatedAt)

    @Test fun holdingValueExact() {
        assertEquals(2_109_999L, Valuation.holdingValue(3_000_000, 70_333_300))
        // 1 234 567.8901 units of a 1 878.7031 NAV, no overflow, half-up.
        assertEquals(2_319_386_522L, Valuation.holdingValue(12_345_678_901, 18_787_031))
    }

    @Test fun sumOfHoldings() {
        val v = Valuation.accountValue(listOf(h(3_000_000, 55_000_000), h(5_000_000, 20_400_000))) { 0 }
        assertEquals(1_650_000L + 1_020_000L, v)
    }

    @Test fun missingPriceGivesNull() {
        assertNull(Valuation.accountValue(listOf(h(3_000_000, 55_000_000), h(5_000_000, null))) { 0 })
        assertNull(Valuation.accountValue(emptyList()) { 0 })
    }

    @Test fun flowsAfterUnitsUpdateAreKept() {
        var askedFrom: Instant? = null
        val v = Valuation.accountValue(listOf(h(3_000_000, 55_000_000, 1_000), h(5_000_000, 20_400_000, 9_000))) {
            askedFrom = it; 500_000
        }
        assertEquals(1_650_000L + 1_020_000L + 500_000L, v)
        assertEquals(Instant.ofEpochMilli(9_000), askedFrom)
    }
}
