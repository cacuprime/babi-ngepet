package id.dompetku.importer

import id.dompetku.domain.JAKARTA
import id.dompetku.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TableParserTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)
    private fun Draft.day() = date.atZone(JAKARTA).toLocalDate()

    @Test fun csvDebitCredit() {
        val csv = "Tanggal,Keterangan,Debit,Kredit,Saldo\n01/11/2026,Gojek,25.000,,975.000\n02/11/2026,\"Gaji, November\",,8.500.000,9.475.000\n"
        val r = TableParser.parse(Csv.rows(csv))
        assertEquals(2, r.size)
        assertEquals(TxType.EXPENSE, r[0].type); assertEquals(25_000L, r[0].amount); assertEquals(day(2026, 11, 1), r[0].day())
        assertEquals("Gojek", r[0].description); assertEquals(975_000L, r[0].balanceAfter)
        assertEquals(TxType.INCOME, r[1].type); assertEquals(8_500_000L, r[1].amount); assertEquals("Gaji, November", r[1].description)
    }

    @Test fun csvSignedAmountSemicolon() {
        val r = TableParser.parse(Csv.rows("Date;Description;Amount\n2026-11-03;Spotify;-54.990\n2026-11-04;Refund;10.000\n"))
        assertEquals(listOf(TxType.EXPENSE, TxType.INCOME), r.map { it.type })
        assertEquals(listOf(54_990L, 10_000L), r.map { it.amount })
    }

    @Test fun headerlessOrGarbageGivesEmpty() {
        assertEquals(emptyList<Draft>(), TableParser.parse(Csv.rows("a,b\nc,d\n")))
    }

    @Test fun xlsxFirstSheet() {
        val bytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
                put("xl/sharedStrings.xml", """<sst><si><t>Tanggal</t></si><si><t>Keterangan</t></si><si><t>Jumlah</t></si><si><t>Indomaret</t></si></sst>""")
                put("xl/worksheets/sheet1.xml", """<worksheet><sheetData>
                    <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
                    <row r="2"><c r="A2"><v>46330</v></c><c r="B2" t="s"><v>3</v></c><c r="C2"><v>-45000</v></c></row>
                    <row r="3"><c r="A3" t="inlineStr"><is><t>05/11/2026</t></is></c><c r="B3" t="inlineStr"><is><t>Gaji</t></is></c><c r="C3"><v>100000</v></c></row>
                    </sheetData></worksheet>""")
            }
        }.toByteArray()
        val rows = Xlsx.rows(ByteArrayInputStream(bytes))
        assertEquals(listOf("Tanggal", "Keterangan", "Jumlah"), rows[0])
        val r = TableParser.parse(rows)
        assertEquals(2, r.size)
        assertEquals(day(2026, 11, 4), r[0].day()); assertEquals(TxType.EXPENSE, r[0].type); assertEquals(45_000L, r[0].amount)
        assertEquals("Indomaret", r[0].description)
        assertEquals(TxType.INCOME, r[1].type); assertEquals(day(2026, 11, 5), r[1].day())
    }
}
