package id.dompetku.core

import id.dompetku.ui.tx.AmountInput
import id.dompetku.ui.tx.Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipInputStream

class CalcExportTest {
    @Test fun calcPrecedenceAndEdges() {
        assertEquals(22_000L, Calc.eval("12000+5000×2"))
        assertEquals(7_000L, Calc.eval("10000-3000"))
        assertEquals(3_333L, Calc.eval("10000÷3"))
        assertEquals(0L, Calc.eval("5000÷0"))
        assertEquals(12_000L, Calc.eval("12000+"))
        assertEquals(0L, Calc.eval(""))
    }

    @Test fun keypadOperators() {
        var a = AmountInput().operator('+')
        assertEquals("", a.digits) // no operator before a number
        a = a.press('2').press('5').press000().operator('+').operator('-').press('5').press000()
        assertEquals("25000-5000", a.digits)
        assertEquals(20_000L, a.value)
        assertEquals("20000", a.evaluate().digits)
        assertEquals(0L, AmountInput("5-10").value) // never negative
        assertEquals("25000+", AmountInput("25000+").press('0').digits) // no leading zero after an operator
    }

    @Test fun tagsNormalizeAndDisplay() {
        assertEquals("liburan bali", Tags.normalize("#Liburan, #bali #liburan"))
        assertNull(Tags.normalize("  # "))
        assertEquals("#liburan #bali", Tags.display("liburan bali"))
    }

    @Test fun csvQuotesAndBom() {
        val s = String(Export.csv(listOf(listOf("a", "b,c", "say \"hi\""), listOf(1, null, -5))))
        assertTrue(s.startsWith("﻿"))
        assertEquals("a,\"b,c\",\"say \"\"hi\"\"\"\r\n1,,-5\r\n", s.removePrefix("﻿"))
    }

    @Test fun xlsxHasSheetWithNumbersAndEscapedText() {
        val bytes = Export.xlsx(listOf(listOf("Nominal", "Toko"), listOf(-25000, "A&B <x>")))
        val files = HashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; files[e.name] = String(z.readBytes()) } }
        assertTrue(files.keys.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml")))
        val sheet = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("<c r=\"A2\"><v>-25000</v></c>"))
        assertTrue(sheet.contains("A&amp;B &lt;x&gt;"))
    }

    @Test fun englishFallsBackToIndonesian() {
        I18n.english = true
        try {
            assertEquals("Teks tanpa terjemahan xyz", t("Teks tanpa terjemahan xyz"))
            assertEquals("Budget", t("Budget"))
        } finally { I18n.english = false }
    }
}
