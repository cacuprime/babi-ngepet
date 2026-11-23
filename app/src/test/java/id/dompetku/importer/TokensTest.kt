package id.dompetku.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TokensTest {
    @Test fun amountFormats() {
        assertEquals(50_000L, Tokens.amount("50.000,00"))
        assertEquals(3_890_833L, Tokens.amount("3.890.833"))
        assertEquals(125_000L, Tokens.amount("Rp125.000"))
        assertEquals(1_250_000L, Tokens.amount("Rp 1,250,000"))
        assertEquals(1_250_000L, Tokens.amount("Rp1.250.000,50"))
        assertEquals(266L, Tokens.amount("266"))
        assertNull(Tokens.amount("abc"))
        assertNull(Tokens.amount("FT2629895TP8"))
    }

    @Test fun amountsAtEnd() {
        assertEquals(listOf(50_000L, 0L, 11_046_645L), Tokens.amountsAtEnd("FT2629895TP8 50.000,00 0,00 11.046.645,00"))
        assertEquals(listOf(750_000L, 3_140_994L), Tokens.amountsAtEnd("750.000 3.140.994"))
        assertEquals(emptyList<Long>(), Tokens.amountsAtEnd("Bunga Tabungan"))
    }

    @Test fun datesBothLanguages() {
        assertEquals(LocalDate.of(2026, 10, 25), Tokens.date("25 Okt 2026", null))
        assertEquals(LocalDate.of(2026, 10, 1), Tokens.date("01 OCT", 2026))
        assertEquals(LocalDate.of(2026, 11, 10), Tokens.date("10/11/2026", null))
        assertEquals(LocalDate.of(2026, 11, 10), Tokens.date("2026-11-10", null))
        assertEquals(LocalDate.of(2026, 12, 3), Tokens.date("3 Desember 2026", null))
        assertEquals(LocalDate.of(2026, 8, 17), Tokens.date("17 Agu 2026, 14:25", null))
        assertNull(Tokens.date("01 OCT", null))
        assertNull(Tokens.date("Bunga", 2026))
    }

    @Test fun timesAndMonths() {
        assertEquals(LocalTime.of(13, 16), Tokens.time("13:16"))
        assertEquals(LocalTime.of(14, 25), Tokens.time("10 Nov 2026 14.25 WIB"))
        assertEquals(5, Tokens.month("Mei"))
        assertEquals(8, Tokens.month("AUG"))
        assertNull(Tokens.month("Bunga"))
    }
}
