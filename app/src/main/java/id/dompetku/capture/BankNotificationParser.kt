package id.dompetku.capture

import id.dompetku.domain.TxType
import id.dompetku.importer.Draft
import id.dompetku.importer.Tokens
import java.time.Instant

/**
 * Reads one bank-app notification. General rules (exact bank wording isn't published):
 * the first Rp amount not introduced by "saldo" is the transaction amount; money-out vs
 * money-in words decide the type; the words after "ke/di/dari…" are the merchant.
 */
object BankNotificationParser {
    sealed interface Parsed {
        data class Tx(val draft: Draft) : Parsed
        /** Not a transaction (OTP, promo, failed payment, no amount). */
        data object Ignore : Parsed
        /** Has an amount but can't be read safely; kept in the "belum terbaca" log. */
        data object Unknown : Parsed
    }

    private val AMOUNT = Regex("""(?i)\b(rp\.?|idr)\s?(\d[\d.,]*)""")
    private val SALDO_BEFORE = Regex("""(?i)(saldo|balance|limit|sisa)[^\d]{0,25}$""")
    private val IGNORE = Regex("""(?i)\b(otp|kode verifikasi|kode aktivasi|login|masuk ke akun|gagal|dibatalkan|ditolak|tidak berhasil|kedaluwarsa)\b""")
    private val PROMO = Regex("""(?i)\b(promo|diskon|voucher|hingga|s\.d\.|dapatkan|yuk|ayo|spesial)\b""")
    private val DONE = Regex("""(?i)\b(berhasil|sukses|telah|sudah|diterima|menerima|masuk|baru saja)\b""")
    private val IN_WORDS = Regex("""(?i)\b(menerima|diterima|dana masuk|uang masuk|transfer masuk|transfer dari|kredit|refund|pengembalian|received|incoming)\b""")
    private val OUT_WORDS = Regex("""(?i)\b(pembayaran|bayar|membayar|dibayar|qris|transfer ke|kirim|mengirim|debit|pembelian|beli|tarik tunai|top ?up|payment|paid|transaksi keluar)\b""")
    private val OUT_PREFIX = Regex("""(?i)\b(?:ke|di|kepada|merchant|to|at)\s*:?\s+""")
    private val IN_PREFIX = Regex("""(?i)\b(?:dari|from|oleh)\s*:?\s+""")
    private val MERCHANT_END = Regex("""(?i)(\s+(sebesar|senilai|pada|tgl|tanggal|dengan|berhasil|telah|sukses|sudah|sejumlah)\b|\s+(rp|idr)\.?\s?\d|[.,;:!\n]|\s+\d{1,2}[/:-]\d)""")
    private val REF = Regex("""(?i)\b(?:ref(?:erence|erensi)?|no\.? ?ref)\s*[:.#]?\s*([A-Z0-9]{6,})""")

    fun parse(title: String?, text: String, postedAt: Instant): Parsed {
        val full = listOfNotNull(title?.trim()?.takeIf { it.isNotEmpty() }, text.trim()).joinToString(". ")
        if (IGNORE.containsMatchIn(full)) return Parsed.Ignore
        if (PROMO.containsMatchIn(full) && !DONE.containsMatchIn(full)) return Parsed.Ignore

        var amount: Long? = null
        var amountEnd = -1
        var saldo: Long? = null
        for (m in AMOUNT.findAll(full)) {
            val v = Tokens.amount(m.groupValues[2]) ?: continue
            if (v <= 0) continue
            if (SALDO_BEFORE.containsMatchIn(full.substring(0, m.range.first))) {
                if (saldo == null) saldo = v
            } else if (amount == null) {
                amount = v; amountEnd = m.range.last + 1
            }
        }
        if (amount == null) return if (saldo != null) Parsed.Unknown else Parsed.Ignore

        val inHits = IN_WORDS.findAll(full).count()
        val outHits = OUT_WORDS.findAll(full).count()
        val type = when {
            inHits > 0 && outHits == 0 -> TxType.INCOME
            outHits > 0 && inHits == 0 -> TxType.EXPENSE
            inHits > 0 && outHits > 0 -> if (Regex("(?i)transfer dari|menerima|diterima").containsMatchIn(full)) TxType.INCOME else TxType.EXPENSE
            else -> return Parsed.Unknown
        }
        val merchant = merchant(full, if (type == TxType.INCOME) IN_PREFIX else OUT_PREFIX, amountEnd)
        val label = if (Regex("(?i)qris").containsMatchIn(full)) "QRIS" else if (type == TxType.INCOME) "Dana masuk" else "Pembayaran"
        return Parsed.Tx(
            Draft(
                type = type, amount = amount, date = postedAt,
                description = listOfNotNull(label, merchant).joinToString(" · "),
                merchant = merchant, reference = REF.find(full)?.groupValues?.get(1),
                balanceAfter = saldo, confidence = 0.8f,
            )
        )
    }

    private fun merchant(full: String, prefix: Regex, amountEnd: Int): String? {
        val starts = prefix.findAll(full).map { it.range.last + 1 }.toList()
        // Prefer a name right after the amount; else the first one in the text.
        val ordered = starts.sortedBy { if (it >= amountEnd) 0 else 1 }
        for (s in ordered) {
            val rest = full.substring(s)
            val end = MERCHANT_END.find(rest)?.range?.first ?: rest.length
            val name = rest.substring(0, end).trim()
                .replace(Regex("""(?i)^(rekening|rek\.?)\s*\d*\s*(a\.?n\.?\s*)?"""), "").trim()
            if (name.length >= 2 && name.any { it.isLetter() } && !name.lowercase().startsWith("akun")) return name.take(40)
        }
        return null
    }
}
