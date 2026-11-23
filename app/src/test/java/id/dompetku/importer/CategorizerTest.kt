package id.dompetku.importer

import id.dompetku.data.db.CategorizationRuleEntity
import id.dompetku.data.db.CategoryEntity
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class CategorizerTest {
    private val cats = listOf(
        CategoryEntity(1, "Transportasi", "EXPENSE", "", 0, true),
        CategoryEntity(2, "Belanja", "EXPENSE", "", 0, true),
        CategoryEntity(3, "Lain-lain", "EXPENSE", "", 0, true),
        CategoryEntity(4, "Hasil Investasi", "INCOME", "", 0, true),
        CategoryEntity(5, "Makanan & Minuman", "EXPENSE", "", 0, true),
    )
    private fun rule(k: String, cat: Long, src: String = "BUILTIN", conf: Float = 0.7f) =
        CategorizationRuleEntity(0, k, cat, null, conf, src, 0, 0, 0)
    private val rules = listOf(
        rule("gojek", 1), rule("indomaret", 2), rule("bunga", 4), rule("pajak bunga", 3),
        rule("indomaret point", 5, "LEARNED", 0.9f),
    )
    private val c = Categorizer(rules, cats)
    private fun draft(type: TxType, desc: String, merchant: String? = null) =
        Draft(type, 1000, Instant.EPOCH, desc, merchant, null, null, 0.9f)

    @Test fun builtinGojek() {
        val s = c.suggest(draft(TxType.EXPENSE, "Pembayaran", "GOJEK *GORIDE"))!!
        assertEquals(1L, s.categoryId); assertEquals(0.7f, s.confidence)
    }

    @Test fun learnedBeatsBuiltin() {
        assertEquals(5L, c.suggest(draft(TxType.EXPENSE, "QRIS", "Indomaret Point Sudirman"))!!.categoryId)
        assertEquals(2L, c.suggest(draft(TxType.EXPENSE, "QRIS", "Indomaret Sudirman"))!!.categoryId)
    }

    @Test fun pajakBungaNotBunga() {
        assertEquals(3L, c.suggest(draft(TxType.EXPENSE, "Pajak Bunga Tabungan Pajak"))!!.categoryId)
        assertEquals(4L, c.suggest(draft(TxType.INCOME, "Bunga Tabungan Bunga"))!!.categoryId)
    }

    @Test fun unknownBelowThreshold() {
        assertNull(c.suggest(draft(TxType.EXPENSE, "XYZ ABC")))
        assertNull(c.suggest(draft(TxType.TRANSFER, "gojek")))
    }

    @Test fun normalizeKeyword() {
        assertEquals("sipaling nasi padang", Categorizer.normalize("SIPALING NASI PADANG 0812-3456 Batam"))
        assertEquals("", Categorizer.normalize("12345 !!"))
    }

    private fun tx(id: Long, type: TxType, amount: Long, acct: Long, iso: String) =
        Tx(id, type, amount, 1, acct, null, null, Instant.parse(iso), null, null, null)

    @Test fun sameRefIsDuplicate() {
        val d = Draft(TxType.EXPENSE, 50_000, Instant.parse("2026-11-10T03:00:00Z"), "x", null, "REF123", null, 0.9f)
        assertEquals(7L, Duplicates.find(d, 1, emptyList(), mapOf("REF123" to 7L)))
    }

    @Test fun sameDayAmountIsDuplicate() {
        val d = Draft(TxType.EXPENSE, 50_000, Instant.parse("2026-11-10T03:00:00Z"), "x", null, null, null, 0.9f)
        val existing = listOf(tx(9, TxType.EXPENSE, 50_000, 1, "2026-11-10T14:00:00Z"), tx(10, TxType.EXPENSE, 50_000, 2, "2026-11-10T03:00:00Z"))
        assertEquals(9L, Duplicates.find(d, 1, existing, emptyMap()))
    }

    @Test fun differentDayNotDuplicate() {
        val d = Draft(TxType.EXPENSE, 50_000, Instant.parse("2026-11-10T03:00:00Z"), "x", null, null, null, 0.9f)
        // 2026-11-10T17:30Z is 00:30 WIB on the 11th.
        assertNull(Duplicates.find(d, 1, listOf(tx(9, TxType.EXPENSE, 50_000, 1, "2026-11-10T17:30:00Z")), emptyMap()))
        assertNull(Duplicates.find(d, 1, listOf(tx(9, TxType.INCOME, 50_000, 1, "2026-11-10T03:00:00Z")), emptyMap()))
    }
}
