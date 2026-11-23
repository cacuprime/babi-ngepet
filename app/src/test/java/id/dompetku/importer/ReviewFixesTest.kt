package id.dompetku.importer

import id.dompetku.domain.JAKARTA
import id.dompetku.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Regression tests for the 1.2 final-review findings. */
class ReviewFixesTest {
    private val now = Instant.parse("2026-11-15T05:00:00Z")
    private fun Draft.day(): LocalDate = date.atZone(JAKARTA).toLocalDate()

    // C1: SeaBank prints "01 JAN 2027" above the period "01 DEC 2026 sampai 31 DEC 2026".
    @Test fun statementYearFromPeriodNotPrintDate() {
        val lines = """
            REKENING KORAN
            01 JAN 2027
            RINGKASAN REKENING
            01 DEC 2026 sampai 31 DEC 2026
            REKENING SALDO AWAL (IDR) TRANSAKSI KELUAR (IDR) TRANSAKSI MASUK (IDR) SALDO AKHIR (IDR)
            TABUNGAN 100.000 10.000 0 90.000
            TANGGAL TRANSAKSI KELUAR (IDR) MASUK (IDR) SALDO AKHIR (IDR)
            05 DEC
            Kopi
            10.000 90.000
        """.trimIndent().lines()
        assertEquals(LocalDate.of(2026, 12, 5), StatementParser.parse(lines).single().day())
    }

    // I2: a description line ending in two short numbers must not end the row or poison later rows.
    @Test fun failedBalanceCheckKeepsRowOpen() {
        val lines = """
            01 NOV 2026 sampai 30 NOV 2026
            SALDO AWAL 1.000.000
            TANGGAL TRANSAKSI KELUAR MASUK SALDO AKHIR
            03 NOV
            ANDI CONTOH
            BI-FAST 014 1234567
            200.000 800.000
            04 NOV
            Kopi
            20.000 780.000
        """.trimIndent().lines()
        val r = StatementParser.parse(lines)
        assertEquals(listOf(200_000L, 20_000L), r.map { it.amount })
        assertTrue(r[0].description.contains("BI-FAST 014 1234567"))
    }

    @Test fun centsDoNotBreakBalanceCheck() {
        val lines = """
            Periode : 01 Nov 2026 - 30 Nov 2026
            Saldo Awal : 1.000.000,20
            Date & Time Detail Transaksi No Reff Debit Kredit Saldo
            05 Nov 2026
            Zakat
            ZK2600000AA1 100,50 0,00 999.899,70
            06 Nov 2026
            Kopi
            KP2600000BB2 20.000,00 0,00 979.899,70
        """.trimIndent().lines()
        assertEquals(listOf(100L, 20_000L), StatementParser.parse(lines).map { it.amount })
    }

    @Test fun closingBalanceMismatchIsReported() {
        val ok = javaClass.classLoader!!.getResource("statements/seabank_synthetic.txt")!!.readText().lines()
        assertNull(StatementParser.check(ok, StatementParser.parse(ok)))
        val missingLastRow = ok.dropLast(6)
        assertTrue(StatementParser.check(missingLastRow, StatementParser.parse(missingLastRow)) != null)
    }

    // I7
    @Test fun statementDetection() {
        val sea = javaClass.classLoader!!.getResource("statements/seabank_synthetic.txt")!!.readText().lines()
        assertTrue(StatementParser.looksLikeStatement(sea))
        assertFalse(StatementParser.looksLikeStatement(listOf("INDOMARET", "TOTAL : 22.900")))
    }

    // I3
    @Test fun pembayaranDiterimaIsExpense() {
        val d = ReceiptParser.parse("Pembayaran diterima\nWarung Bu Tini\nTotal\nRp27.000".lines(), now)!!
        assertEquals(TxType.EXPENSE, d.type)
        val inc = ReceiptParser.parse("Kamu menerima\nRp50.000\ndari BUDI".lines(), now)!!
        assertEquals(TxType.INCOME, inc.type); assertEquals(0.5f, inc.confidence)
    }

    // I4
    @Test fun totalItemAndHematIgnored() {
        val d = ReceiptParser.parse("ALFAMART\nTOTAL : 22.900\nTOTAL ITEM 2\nTOTAL HEMAT 5.000\nTOTAL QTY 3".lines(), now)!!
        assertEquals(22_900L, d.amount)
    }

    // I5
    @Test fun typeColumnDecidesDirection() {
        val r = TableParser.parse(Csv.rows("Tanggal,Keterangan,Jumlah,D/K\n01/11/2026,Gojek,25.000,D\n02/11/2026,Gaji,100.000,K\n"))
        assertEquals(listOf(TxType.EXPENSE, TxType.INCOME), r.map { it.type })
    }

    @Test fun unsignedAmountUsesBalance() {
        val r = TableParser.parse(Csv.rows("Tanggal,Keterangan,Amount,Saldo\n01/11/2026,Awal,100.000,1.000.000\n02/11/2026,Gojek,25.000,975.000\n03/11/2026,Gaji,50.000,1.025.000\n"))
        assertEquals(listOf(TxType.EXPENSE, TxType.INCOME), r.drop(1).map { it.type })
        assertTrue(r[0].confidence <= 0.5f) // first row has no previous balance
    }

    @Test fun negativeMarkers() {
        val r = TableParser.parse(Csv.rows("Date;Description;Amount\n2026-11-03;A;(50.000)\n2026-11-03;B;Rp -40.000\n2026-11-03;C;30.000-\n"))
        assertEquals(listOf(TxType.EXPENSE, TxType.EXPENSE, TxType.EXPENSE), r.map { it.type })
        assertEquals(listOf(50_000L, 40_000L, 30_000L), r.map { it.amount })
    }

    // I6
    @Test fun plainDecimals() {
        assertEquals(150_000L, Tokens.amount("150000.00"))
        assertEquals(12_345L, Tokens.amount("12345.5"))
        assertEquals(10_050L, Tokens.cents("100,50"))
    }
}
