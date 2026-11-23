package id.dompetku.prices

import id.dompetku.data.db.HoldingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class RdnBondTest {
    private fun h(kind: String, units: Long, priceE4: Long?, cost: Long?, updatedAt: Long = 0) =
        HoldingEntity(0, 1, kind, "X", "X", units, priceE4, null, null, updatedAt, costRp = cost)
    private fun lots(n: Long) = n * 100 * 10_000

    @Test fun bibitBondExample() {
        // Owner's Bibit: 2 units at 99,8875% = Rp1.997.750, modal Rp1.962.500.
        val price = Bonds.unitPriceE4(99.8875)
        assertEquals(9_988_750_000L, price)
        val value = Valuation.holdingValue(2 * 10_000, price)
        assertEquals(1_997_750L, value)
        val (gain, pct) = Valuation.profit(value, 1_962_500)
        assertEquals(35_250L, gain)
        assertEquals(1.80, pct!!, 0.005)
    }

    @Test fun rdnTopUpThenBuy() {
        val bbca = h("STOCK", lots(3), 55_000_000, 1_620_000)
        // 5 jt put in, BBCA bought for 1,62 jt → RDN 3,38 jt.
        assertEquals(3_380_000L, Valuation.rdnCash(5_000_000, listOf(bbca)))
        assertEquals(1_650_000L + 3_380_000L, Valuation.accountValue(listOf(bbca), 5_000_000) { 999 })
        // Top up 1 jt, then buy TLKM 5 lot for 1 jt: RDN back to 3,38 jt, nothing counted twice.
        val tlkm = h("STOCK", lots(5), 21_600_000, 1_000_000)
        assertEquals(1_650_000L + 1_080_000L + 3_380_000L, Valuation.accountValue(listOf(bbca, tlkm), 6_000_000) { 999 })
    }

    @Test fun noModalFallsBack() {
        val withCost = h("STOCK", lots(3), 55_000_000, 1_620_000)
        val noCost = h("STOCK", lots(5), 21_600_000, null)
        assertNull(Valuation.rdnCash(5_000_000, listOf(withCost, noCost)))
        // 1.3 rule: holdings + flows since units were edited.
        assertEquals(1_650_000L + 1_080_000L + 500_000L, Valuation.accountValue(listOf(withCost, noCost), 5_000_000) { 500_000 })
    }

    @Test fun negativeRdnAllowed() {
        val bbca = h("STOCK", lots(3), 55_000_000, 1_620_000)
        assertEquals(-620_000L, Valuation.rdnCash(1_000_000, listOf(bbca)))
        assertEquals(1_650_000L - 620_000L, Valuation.accountValue(listOf(bbca), 1_000_000) { 0 })
    }

    @Test fun profitWithoutModal() {
        assertEquals(null, Valuation.profit(100, 0).second)
    }

    @Test fun bondParserPicksCodes() {
        val json = javaClass.classLoader!!.getResource("prices/pasardana_bonds_sample.json")!!.readText()
        val m = PriceParsers.pasardanaBonds(json, setOf("FR0100", "FR0098", "ORI025"))
        assertEquals(setOf("FR0100", "FR0098"), m.keys)
        assertEquals(97.5, m["FR0100"]!!.first, 0.0)
        assertEquals(95.251, m["FR0098"]!!.first, 0.0)
        val info = PriceParsers.bondInfo(json)["FR0100"]!!
        assertEquals(0.06625, info.rate, 0.0); assertEquals(LocalDate.of(2034, 9, 15), info.maturity)
    }

    @Test fun retailSbnAtPar() {
        assertTrue(Bonds.isRetailSbn("ORI025")); assertTrue(Bonds.isRetailSbn("SBR013")); assertTrue(Bonds.isRetailSbn("ST012"))
        assertTrue(Bonds.isRetailSbn("SR020")); assertTrue(!Bonds.isRetailSbn("FR0100"))
        assertEquals(10_000_000_000L, Bonds.unitPriceE4(100.0))
    }

    @Test fun accruedCouponHalfYear() {
        // FR0100 6,625%, coupons 15 Mar / 15 Sep; 61 of 181 days since 15 Sep 2026.
        assertEquals(22_327L, Bonds.accruedCoupon(2_000_000, 0.06625, LocalDate.of(2034, 9, 15), LocalDate.of(2026, 11, 15)))
        assertEquals(0L, Bonds.accruedCoupon(2_000_000, 0.06625, LocalDate.of(2034, 9, 15), LocalDate.of(2026, 9, 15)))
    }
}
