package id.dompetku.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun fullFormatsWithDots() {
        assertEquals("Rp15.750.000", Rupiah.full(15_750_000))
        assertEquals("Rp0", Rupiah.full(0))
        assertEquals("-Rp35.000", Rupiah.full(-35_000))
    }

    @Test fun signedAddsPlus() {
        assertEquals("+Rp4.300.000", Rupiah.signed(4_300_000))
        assertEquals("-Rp1.000", Rupiah.signed(-1_000))
    }

    @Test fun compactUsesJtRbM() {
        assertEquals("8,5jt", Rupiah.compact(8_500_000))
        assertEquals("500rb", Rupiah.compact(500_000))
        assertEquals("1,25M", Rupiah.compact(1_250_000_000))
        assertEquals("950", Rupiah.compact(950))
    }

    @Test fun parseDigitsIgnoresSeparators() {
        assertEquals(1_250_000L, Rupiah.parseDigits("1.250.000"))
        assertNull(Rupiah.parseDigits(""))
        assertNull(Rupiah.parseDigits("abc"))
    }

    @Test fun gramsFormatAndParse() {
        assertEquals("10,5000 gr", Grams.format(105_000))
        assertEquals(105_000L, Grams.parse("10,5000"))
        assertEquals(105_000L, Grams.parse("10.5000"))
        assertEquals(10_000L, Grams.parse("1"))
        assertNull(Grams.parse("abc"))
    }

    @Test fun maskConstant() {
        assertEquals("Rp •••••", Rupiah.MASK)
    }

    @Test fun gramsValueRoundsHalfUp() {
        assertEquals(24_507_000L, Grams.valueOf(105_000, 2_334_000))
    }
}
