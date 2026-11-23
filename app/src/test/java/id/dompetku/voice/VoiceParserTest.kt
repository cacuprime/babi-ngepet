package id.dompetku.voice

import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class VoiceParserTest {
    private val today = LocalDate.of(2026, 11, 18)
    private fun acc(id: Long, name: String, type: AccountType, provider: String? = null) =
        Account(id, name, type, provider, 0, true, false, Instant.EPOCH)
    private val accounts = listOf(
        acc(1, "Dompet", AccountType.CASH), acc(2, "BNI", AccountType.BANK, "BNI"),
        acc(3, "SeaBank", AccountType.BANK, "SeaBank"), acc(4, "Mandiri", AccountType.BANK, "Mandiri"),
    )
    private fun parse(s: String) = VoiceParser.parse(s, today, accounts, defaultAccountId = 1)

    @Test fun simpleCashExpense() {
        val i = parse("bakso dua puluh lima ribu pakai cash").items.single()
        assertEquals(TxType.EXPENSE, i.type)
        assertEquals(25_000L, i.amount)
        assertEquals("Bakso", i.description)
        assertEquals(1L, i.accountId)
        assertEquals(today, i.date)
    }

    @Test fun twoItemsInOneSentence() {
        val items = parse("makan siang 30 ribu dan parkir 5rb").items
        assertEquals(listOf(30_000L, 5_000L), items.map { it.amount })
        assertEquals(listOf("Makan siang", "Parkir"), items.map { it.description })
    }

    @Test fun wordsWithoutAmountJoinNextItem() {
        val i = parse("beli roti dan susu 20 ribu").items.single()
        assertEquals(20_000L, i.amount)
        assertEquals("Roti dan susu", i.description)
    }

    @Test fun numberWords() {
        assertEquals(150_000L, VoiceParser.words("seratus lima puluh ribu"))
        assertEquals(1_500_000L, VoiceParser.words("satu setengah juta"))
        assertEquals(12_500L, VoiceParser.words("dua belas ribu lima ratus"))
        assertEquals(1_200_000L, VoiceParser.words("satu juta dua ratus ribu"))
        assertEquals(5_000L, VoiceParser.words("goceng"))
        assertEquals(10_000L, VoiceParser.words("ceban"))
        assertEquals(1_000L, VoiceParser.words("seribu"))
    }

    @Test fun digitForms() {
        assertEquals(1_500_000L, parse("servis motor 1,5 juta").items.single().amount)
        assertEquals(1_500_000L, parse("servis motor 1.5jt").items.single().amount)
        assertEquals(25_000L, parse("kopi Rp25.000").items.single().amount)
        assertEquals(1_500_000L, parse("hp 1 juta 500 ribu").items.single().amount)
    }

    @Test fun yesterdayAndAccount() {
        val i = parse("kemarin bensin 50 ribu pakai BNI").items.single()
        assertEquals(today.minusDays(1), i.date)
        assertEquals(2L, i.accountId)
        assertEquals("Bensin", i.description)
    }

    @Test fun appNamesMapToBank() {
        assertEquals(4L, parse("listrik 200 ribu pake livin").items.single().accountId)
        assertEquals(2L, parse("pulsa 50 ribu via wondr").items.single().accountId)
    }

    @Test fun income() {
        val i = parse("dapat gaji 5 juta masuk seabank").items.single()
        assertEquals(TxType.INCOME, i.type)
        assertEquals(5_000_000L, i.amount)
        assertEquals(3L, i.accountId)
    }

    @Test fun noAmountIsError() {
        val r = parse("beli kopi")
        assertTrue(r.items.isEmpty())
        assertNotNull(r.error)
    }
}
