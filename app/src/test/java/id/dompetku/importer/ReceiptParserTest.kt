package id.dompetku.importer

import id.dompetku.domain.JAKARTA
import id.dompetku.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class ReceiptParserTest {
    private val now = Instant.parse("2026-11-15T05:00:00Z")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = LocalDateTime.of(y, m, d, h, min).atZone(JAKARTA).toInstant()
    private fun parse(s: String) = ReceiptParser.parse(s.trimIndent().lines(), now)

    @Test fun gopayScreenshot() {
        val d = parse("""
            Pembayaran berhasil
            Rp35.000
            Bakso Pak Kumis
            10 Nov 2026, 12:15
            Metode pembayaran
            GoPay Saldo
            ID Transaksi
            A1B2C3D4E5F6
            Total
            Rp35.000
        """)!!
        assertEquals(TxType.EXPENSE, d.type); assertEquals(35_000L, d.amount)
        assertEquals("Bakso Pak Kumis", d.merchant); assertEquals("A1B2C3D4E5F6", d.reference)
        assertEquals(at(2026, 11, 10, 12, 15), d.date)
        assertEquals(0.8f, d.confidence)
    }

    @Test fun bankTransferProof() {
        val d = parse("""
            Transfer Berhasil
            16 Nov 2026 08:42:10 WIB
            Nominal
            Rp 1.000.000
            Biaya Admin
            Rp 2.500
            Total
            Rp 1.002.500
            Penerima
            IBU CONTOH
            BCA - 1234567890
            No. Ref
            202611160842ABC
        """)!!
        assertEquals(1_002_500L, d.amount); assertEquals("IBU CONTOH", d.merchant)
        assertEquals("202611160842ABC", d.reference); assertEquals(at(2026, 11, 16, 8, 42), d.date)
    }

    @Test fun indomaretReceipt() {
        val d = parse("""
            INDOMARET
            JL. CONTOH RAYA NO.5
            10/11/2026 19:03
            AQUA 600ML 2 3.500 7.000
            ROTI TAWAR 1 15.900 15.900
            HARGA JUAL : 22.900
            TOTAL : 22.900
            TUNAI : 50.000
            KEMBALI : 27.100
            TERIMA KASIH
        """)!!
        assertEquals(22_900L, d.amount); assertEquals("INDOMARET", d.merchant); assertEquals(TxType.EXPENSE, d.type)
        assertEquals(at(2026, 11, 10, 19, 3), d.date)
    }

    @Test fun picksTotalOverSaldo() {
        val d = parse("""
            Transfer berhasil
            Saldo GoPay
            Rp2.500.000
            Total bayar
            Rp150.000
            Sisa saldo
            Rp2.350.000
        """)!!
        assertEquals(150_000L, d.amount)
        assertEquals(now, d.date)
    }

    @Test fun incomeKeyword() {
        val d = parse("""
            Uang masuk
            Kamu menerima
            Rp200.000
            dari ANDI CONTOH
            11 Nov 2026 10:00
        """)!!
        assertEquals(TxType.INCOME, d.type); assertEquals(200_000L, d.amount); assertEquals("ANDI CONTOH", d.merchant)
        assertEquals(0.5f, d.confidence)
    }

    @Test fun noAmountReturnsNull() {
        assertNull(parse("Halo\nTerima kasih"))
    }
}
