package id.dompetku.voice

import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.TxType
import id.dompetku.importer.Tokens
import java.time.LocalDate
import kotlin.math.roundToLong

data class VoiceItem(val type: TxType, val amount: Long, val description: String, val accountId: Long?, val date: LocalDate)
data class VoiceResult(val items: List<VoiceItem>, val error: String?)

/**
 * Turns a spoken sentence ("bakso dua puluh lima ribu pakai cash dan parkir 5 ribu") into
 * transactions. Local rules only: amounts in digits or Indonesian number words, the account
 * after "pakai/pake/via/dari…", "kemarin"-style dates, income words. Nothing is saved here.
 */
object VoiceParser {
    const val NO_AMOUNT = "Nominalnya tidak terdengar. Coba ucapkan misalnya: \"bakso 25 ribu pakai cash\"."

    private val UNITS = mapOf(
        "nol" to 0, "satu" to 1, "dua" to 2, "tiga" to 3, "empat" to 4, "lima" to 5,
        "enam" to 6, "tujuh" to 7, "delapan" to 8, "sembilan" to 9,
    )
    private val SLANG = mapOf("seceng" to 1_000L, "noceng" to 2_000L, "goceng" to 5_000L, "ceban" to 10_000L, "goban" to 50_000L, "gocap" to 50_000L)
    private val THOUSAND = setOf("ribu", "rb", "rebu", "k")
    private val MILLION = setOf("juta", "jt")
    private val NUMBER_WORDS = UNITS.keys + SLANG.keys + THOUSAND + MILLION +
        setOf("sepuluh", "sebelas", "seratus", "seribu", "sejuta", "setengah", "belas", "puluh", "ratus")
    private val DIGITS = Regex("""^\d+([.,]\d+)*$""")
    private val DECIMAL = Regex("""^\d+[.,]\d{1,2}$""")

    private val SPLIT = Regex("""\s+(?:dan|terus|lalu|kemudian|plus)\s+|\s*[;,]\s+""")
    private val INCOME = Regex("""\b(dapat|dapet|terima|menerima|diterima|gaji|gajian|dikasih|transferan|bonus|thr)\b""")
    private val DATE = Regex("""\b(kemarin lusa|kemarin|kemaren|hari ini|tadi pagi|tadi siang|tadi sore|tadi malam|tadi|(\d+|dua|tiga|empat|lima) hari (yang )?lalu)\b""")
    private val ACCOUNT = Regex("""\b(pakai|pake|pakek|dari|via|lewat|dengan|masuk ke|masuk|ke)\s+(sea bank|\S+)""")
    private val FILLER = setOf("beli", "bayar", "buat", "untuk", "habis", "abis", "aku", "saya", "rupiah", "rp", "dapat", "dapet",
        "terima", "menerima", "harga", "seharga", "sebesar", "total", "tadi", "nya")

    fun parse(text: String, today: LocalDate, accounts: List<Account>, defaultAccountId: Long?): VoiceResult {
        var s = normalize(text)
        val date = dateOf(s, today)
        s = DATE.replace(s, " ")
        val sentenceAccount = account(s, accounts)?.first

        val items = ArrayList<VoiceItem>()
        var pending = ""
        for (raw in SPLIT.split(s).map { it.trim() }.filter { it.isNotEmpty() }) {
            val acct = account(raw, accounts)
            var seg = acct?.let { raw.replace(it.second, " ") } ?: raw
            val run = bestRun(seg.split(Regex("\\s+")).filter { it.isNotEmpty() })
            if (run == null) { pending = listOf(pending, seg).filter { it.isNotBlank() }.joinToString(" dan "); continue }
            val tokens = seg.split(Regex("\\s+")).filter { it.isNotEmpty() }
            seg = (tokens.subList(0, run.first) + tokens.subList(run.last + 1, tokens.size)).joinToString(" ")
            val type = if (INCOME.containsMatchIn(raw) || INCOME.containsMatchIn(pending)) TxType.INCOME else TxType.EXPENSE
            val desc = listOf(clean(pending), clean(seg)).filter { it.isNotBlank() }.joinToString(" dan ")
                .ifBlank { type.labelId }.replaceFirstChar { it.uppercase() }
            items += VoiceItem(type, run.value, desc.take(100), acct?.first ?: sentenceAccount ?: defaultAccountId, date)
            pending = ""
        }
        return VoiceResult(items, if (items.isEmpty()) NO_AMOUNT else null)
    }

    /** Value of a run of number words/digits, e.g. "seratus lima puluh ribu" → 150 000. Null if nothing numeric. */
    fun words(s: String): Long? {
        val t = normalize(s).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return value(t)?.takeIf { it > 0 }
    }

    private data class Run(val first: Int, val last: Int, val value: Long)

    /** The amount in a segment: the numeric run with the largest value ("nasi 2 bungkus 30 ribu" → 30 000). */
    private fun bestRun(tokens: List<String>): Run? {
        val runs = ArrayList<Run>()
        var i = 0
        while (i < tokens.size) {
            if (!isNumberToken(tokens[i])) { i++; continue }
            var j = i
            while (j + 1 < tokens.size && isNumberToken(tokens[j + 1])) j++
            value(tokens.subList(i, j + 1))?.takeIf { it >= 100 }?.let { runs += Run(i, j, it) }
            i = j + 1
        }
        return runs.maxByOrNull { it.value }
    }

    private fun isNumberToken(t: String) = t in NUMBER_WORDS || DIGITS.matches(t)

    private fun value(tokens: List<String>): Long? {
        var total = 0.0; var current = 0.0; var last = 0.0; var any = false
        tokens.forEachIndexed { idx, t ->
            val next = tokens.getOrNull(idx + 1)
            when {
                DIGITS.matches(t) -> {
                    val v = if (DECIMAL.matches(t) && (next in THOUSAND || next in MILLION)) t.replace(',', '.').toDouble()
                    else Tokens.amount(t)?.toDouble() ?: return null
                    current += v; last = v; any = true
                }
                t in UNITS -> { val v = UNITS.getValue(t).toDouble(); current += v; last = v; any = true }
                t in SLANG -> { total += SLANG.getValue(t); any = true }
                t == "sepuluh" -> { current += 10; last = 10.0; any = true }
                t == "sebelas" -> { current += 11; last = 11.0; any = true }
                t == "seratus" -> { current += 100; last = 100.0; any = true }
                t == "setengah" -> { current = if (current == 0.0) 0.5 else current + 0.5; any = true }
                t == "belas" -> { current += 10; last += 10 }
                t == "puluh" -> { current += last * 9; last *= 10 }
                t == "ratus" -> { current += last * 99; last *= 100 }
                t in THOUSAND -> { total += (if (current == 0.0) 1.0 else current) * 1_000; current = 0.0; last = 0.0; any = true }
                t == "seribu" -> { total += 1_000; any = true }
                t in MILLION -> { total += (if (current == 0.0) 1.0 else current) * 1_000_000; current = 0.0; last = 0.0; any = true }
                t == "sejuta" -> { total += 1_000_000; any = true }
                else -> return null
            }
        }
        return if (any) (total + current).roundToLong() else null
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace(Regex("""rp\.?\s?(?=\d)"""), "")
        .replace(Regex("""(\d)(rb|ribu|rebu|k|jt|juta)\b"""), "$1 $2")
        .replace(Regex("""[!?"]"""), " ")
        .replace(Regex("""\.(\s|$)"""), " ")
        .replace(Regex("""\s+"""), " ").trim()

    private fun dateOf(s: String, today: LocalDate): LocalDate {
        val m = DATE.find(s) ?: return today
        val w = m.value
        return when {
            w.startsWith("kemarin lusa") -> today.minusDays(2)
            w.startsWith("kemarin") || w.startsWith("kemaren") -> today.minusDays(1)
            w.endsWith("lalu") -> today.minusDays((m.groupValues[2].toLongOrNull() ?: words(m.groupValues[2]) ?: 0L))
            else -> today
        }
    }

    /** (account id, matched phrase) for "pakai BNI", "pake cash", "via wondr"… or null. */
    private fun account(s: String, accounts: List<Account>): Pair<Long, String>? {
        val live = accounts.filter { !it.isArchived }
        for (m in ACCOUNT.findAll(s)) {
            val word = m.groupValues[2].replace(Regex("[^a-z0-9]"), "")
            val id = when (word) {
                "cash", "tunai", "kas", "dompet", "duit", "uang" -> live.firstOrNull { it.type == AccountType.CASH }?.id
                else -> {
                    val key = when (word) { "wondr", "bni46" -> "bni"; "livin", "livinmandiri" -> "mandiri"; "sea" -> "seabank"; else -> word }
                    if (key.length < 3) null else live.firstOrNull { a ->
                        a.name.lowercase().replace(" ", "").contains(key) || a.provider?.lowercase()?.replace(" ", "")?.contains(key) == true
                    }?.id
                }
            }
            if (id != null) return id to m.value
        }
        return null
    }

    private fun clean(s: String): String =
        s.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in FILLER }.joinToString(" ").trim()
}
