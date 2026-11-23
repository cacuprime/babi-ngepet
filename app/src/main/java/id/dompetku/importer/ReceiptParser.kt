package id.dompetku.importer

import id.dompetku.domain.JAKARTA
import id.dompetku.domain.TxType
import java.time.Instant
import java.time.LocalTime

/** Reads one transaction from OCR text of a payment screenshot, transfer proof or shop receipt. */
object ReceiptParser {
    private val TOTAL = Regex("""\b(grand total|total bayar|total pembayaran|total)\b""", RegexOption.IGNORE_CASE)
    private val NOMINAL = Regex("""\b(jumlah|nominal|amount|jumlah transfer|nilai)\b""", RegexOption.IGNORE_CASE)
    private val EXCLUDE = Regex("""saldo|sisa|cashback|biaya|diskon|tunai|kembali|harga jual|poin|limit|potongan|item|qty|hemat""", RegexOption.IGNORE_CASE)
    private val INCOME = Regex("""uang masuk|dana masuk|transfer masuk|terima dana|uang diterima|dana diterima|kamu menerima|anda menerima|telah menerima|menerima uang""", RegexOption.IGNORE_CASE)
    private val GROUPED = Regex("""\d[.,]\d{3}""")
    private val MERCHANT = Regex(
        """^(nama merchant|nama toko|nama penerima|merchant|penerima|tujuan|toko|ke|dari)\b[:\s]*(.*)$""", RegexOption.IGNORE_CASE,
    )
    private val REF = Regex(
        """^(no\.? ?ref(?:erensi)?|ref(?:erence)?|id transaksi|no\.? transaksi|nomor transaksi|kode transaksi)\b[:.\s]*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val HEADER = Regex(
        """berhasil|sukses|pembayaran|transaksi|detail|bukti|receipt|struk|terima kasih|rincian|transfer|metode|status|uang masuk|menerima|^jl\.|jalan""",
        RegexOption.IGNORE_CASE,
    )
    private val RP = Regex("""Rp\.?\s?\d[\d.,]*""", RegexOption.IGNORE_CASE)

    private fun lineAmount(l: String): Long? =
        RP.find(l)?.let { Tokens.amount(it.value.replace(Regex("(?i)rp\\.?\\s?"), "Rp")) } ?: Tokens.amountsAtEnd(l).lastOrNull()

    fun parse(lines: List<String>, now: Instant): Draft? {
        val ls = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (ls.isEmpty()) return null

        // A labelled total must look like money (Rp prefix or thousands grouping), so "Total Qty 3" isn't Rp3.
        fun strongAmount(l: String): Long? = if (RP.containsMatchIn(l) || GROUPED.containsMatchIn(l)) lineAmount(l) else null
        fun amountAt(i: Int): Long? = strongAmount(ls[i]) ?: ls.getOrNull(i + 1)?.takeIf { !EXCLUDE.containsMatchIn(it) }?.let { strongAmount(it) }

        var amount: Long? = null
        var sure = false
        ls.indices.lastOrNull { TOTAL.containsMatchIn(ls[it]) && !EXCLUDE.containsMatchIn(ls[it]) && amountAt(it) != null }
            ?.let { amount = amountAt(it); sure = true }
        if (amount == null) {
            ls.indices.firstOrNull { NOMINAL.containsMatchIn(ls[it]) && !EXCLUDE.containsMatchIn(ls[it]) && amountAt(it) != null }
                ?.let { amount = amountAt(it); sure = true }
        }
        if (amount == null) {
            // Largest amount not on (or right after) an excluded label like "Saldo" or "Kembali".
            amount = ls.indices.filter { i ->
                !EXCLUDE.containsMatchIn(ls[i]) && !(i > 0 && EXCLUDE.containsMatchIn(ls[i - 1]) && lineAmount(ls[i - 1]) == null)
            }.mapNotNull { lineAmount(ls[it]) }.maxOrNull()
        }
        val value = amount?.takeIf { it > 0 } ?: return null

        val dateIdx = ls.indexOfFirst { Tokens.date(it, null) != null }
        val date = if (dateIdx >= 0) {
            val d = Tokens.date(ls[dateIdx], null)!!
            val t = Tokens.time(ls[dateIdx]) ?: ls.getOrNull(dateIdx + 1)?.let { Tokens.time(it) } ?: LocalTime.NOON
            d.atTime(t).atZone(JAKARTA).toInstant()
        } else now

        fun labelled(re: Regex): String? = ls.indices.firstNotNullOfOrNull { i ->
            re.matchEntire(ls[i])?.let { m -> m.groupValues[2].trim().ifEmpty { ls.getOrNull(i + 1)?.trim() } }
        }
        val merchant = labelled(MERCHANT)?.takeIf { lineAmount(it) == null }
            ?: ls.firstOrNull { l ->
                l.any { it.isLetter() } && !HEADER.containsMatchIn(l) && lineAmount(l) == null && Tokens.date(l, null) == null
            }
        val reference = labelled(REF)?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.length >= 4 }
        val type = if (INCOME.containsMatchIn(ls.joinToString(" "))) TxType.INCOME else TxType.EXPENSE

        return Draft(
            type = type, amount = value, date = date,
            description = merchant ?: ls.first(), merchant = merchant, reference = reference,
            // Income from a screenshot is rarer and easier to get wrong: always ask the owner to check it.
            balanceAfter = null, confidence = if (sure && type == TxType.EXPENSE) 0.8f else 0.5f,
        )
    }
}
