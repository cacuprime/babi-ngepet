package id.dompetku.importer

import id.dompetku.domain.JAKARTA
import id.dompetku.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class StatementParserTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource("statements/$name")!!.readText().lines()

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(JAKARTA).toInstant()

    @Test fun bsiRowsDebitCredit() {
        val r = StatementParser.parse(fixture("bsi_synthetic.txt"))
        assertEquals(3, r.size)
        assertEquals(TxType.EXPENSE, r[0].type); assertEquals(150_000L, r[0].amount)
        assertEquals(at(2026, 10, 5, 9, 30), r[0].date); assertEquals("FT2600000AB1", r[0].reference)
        assertEquals("Toko Contoh Jaya", r[0].merchant); assertEquals(4_850_000L, r[0].balanceAfter)
        assertTrue(r[0].description.contains("TRF Ke - TOKO CONTOH JAYA"))
        assertEquals(TxType.INCOME, r[1].type); assertEquals(2_000_000L, r[1].amount)
        assertEquals(at(2026, 10, 12, 14, 5), r[1].date)
    }

    @Test fun multilineDescriptionWithNumbers() {
        val r = StatementParser.parse(fixture("bsi_synthetic.txt"))
        assertEquals(TxType.EXPENSE, r[2].type)
        assertEquals(25_500L, r[2].amount)
        assertEquals("QR2600000EF3", r[2].reference)
        val sea = StatementParser.parse(fixture("seabank_synthetic.txt"))
        assertEquals(750_000L, sea[2].amount) // account number "901600001111" is not the amount
    }

    @Test fun seabankDirectionFromBalance() {
        val r = StatementParser.parse(fixture("seabank_synthetic.txt"))
        assertEquals(5, r.size) // page header "01 NOV 2026 / halaman dr / 2 3" is not a row
        assertEquals(listOf(TxType.INCOME, TxType.EXPENSE, TxType.EXPENSE, TxType.EXPENSE, TxType.INCOME), r.map { it.type })
        assertEquals(listOf(300L, 53L, 750_000L, 55_000L, 500_000L), r.map { it.amount })
        assertEquals(at(2026, 10, 15), r[4].date)
        assertEquals("ShopeePay", r[3].merchant)
    }

    @Test fun firstRowUsesOpeningBalance() {
        val r = StatementParser.parse(fixture("seabank_synthetic.txt"))
        // 1.000.000 → 1.000.300 means the first row (300) is money in.
        assertEquals(TxType.INCOME, r[0].type)
        assertTrue(r[0].confidence >= 0.8f)
    }

    @Test fun yearRolloverDecToJan() {
        val lines = """
            01 DEC 2026 sampai 05 JAN 2027
            SALDO AWAL 100.000
            TANGGAL TRANSAKSI KELUAR MASUK SALDO AKHIR
            30 DEC
            Kopi
            10.000 90.000
            02 JAN
            Gaji
            50.000 140.000
        """.trimIndent().lines()
        val r = StatementParser.parse(lines)
        assertEquals(at(2026, 12, 30), r[0].date)
        assertEquals(at(2027, 1, 2), r[1].date)
        assertEquals(TxType.INCOME, r[1].type)
    }

    @Test fun nonStatementTextGivesEmpty() {
        assertEquals(emptyList<Draft>(), StatementParser.parse(listOf("Halo", "Terima kasih", "Rp50.000")))
    }
}
