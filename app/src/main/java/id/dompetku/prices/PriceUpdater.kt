package id.dompetku.prices

import id.dompetku.core.t

import id.dompetku.AppContainer
import id.dompetku.core.Grams
import id.dompetku.domain.AccountType
import id.dompetku.domain.Ledger
import id.dompetku.domain.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** The only network access in the app. Requests carry tickers only, never personal data. */
interface Http { suspend fun get(url: String): String }

/** The server answered, but not with 2xx; [body] may still explain why (e.g. Yahoo's "symbol not found"). */
class HttpStatusException(val code: Int, val body: String) : Exception("HTTP $code")

class UrlHttp : Http {
    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BabiNgepet")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) {
                val body = runCatching { conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull().orEmpty()
                throw HttpStatusException(conn.responseCode, body)
            }
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }
}

data class RunResult(val updatedAccounts: Int, val errors: List<String>, val skipped: Boolean = false)

/**
 * Fetches today's prices and writes one AUTO snapshot per account per day.
 * Never throws; on any failure the previous values stay and the error is recorded on the holding.
 */
class PriceUpdater(private val c: AppContainer, private val http: Http, private val now: () -> Instant = Instant::now) {
    companion object {
        fun yahooUrl(code: String) = "https://query1.finance.yahoo.com/v8/finance/chart/${code.uppercase()}.JK?range=5d&interval=1d"
        const val PASARDANA = "https://pasardana.id/api/FundSearchResult/GetAll"
        const val PASARDANA_BONDS = "https://pasardana.id/api/Bond/GetAll"
        const val PEGADAIAN = "https://pegadaian.co.id/gold/prices"
    }

    suspend fun run(): RunResult = runCatching { doRun() }.getOrElse { RunResult(0, listOf(it.message ?: t("Gagal memperbarui harga"))) }

    private suspend fun doRun(): RunResult {
        if (!c.settings.autoPrices.first()) return RunResult(0, emptyList(), skipped = true)
        val at = now()
        val errors = ArrayList<String>()
        val failed = HashSet<Long>()
        val holdings = c.holdings.all()

        holdings.filter { it.kind == "STOCK" }.groupBy { it.code }.forEach { (code, hs) ->
            runCatching {
                val body = try { http.get(yahooUrl(code)) } catch (e: HttpStatusException) { e.body.ifBlank { throw e } }
                PriceParsers.yahoo(body).getOrThrow()
            }
                .onSuccess { (price, time) -> apply(hs, PriceParsers.toE4(price), time, code, failed, errors) }
                .onFailure { e -> fail(hs.map { it.id }, "$code: ${message(e)}", failed, errors) }
        }

        val funds = holdings.filter { it.kind == "FUND" }
        if (funds.isNotEmpty()) {
            runCatching { PriceParsers.pasardana(http.get(PASARDANA), funds.map { it.code }.toSet()) }
                .onSuccess { navs ->
                    funds.forEach { f ->
                        val p = navs[f.code]
                        if (p != null) apply(listOf(f), PriceParsers.toE4(p.first), p.second, f.name, failed, errors)
                        else fail(listOf(f.id), "${f.name}: NAV tidak ditemukan", failed, errors)
                    }
                }
                .onFailure { e -> fail(funds.map { it.id }, "Reksa dana: ${message(e)}", failed, errors) }
        }

        val bonds = holdings.filter { it.kind == "BOND" }
        if (bonds.isNotEmpty()) {
            val prices = runCatching {
                val json = http.get(PASARDANA_BONDS)
                // Keep coupon rate and maturity of the owner's series for the "kupon berjalan" line (small file).
                runCatching {
                    val wanted = bonds.map { it.code.uppercase() }.toSet()
                    val info = PriceParsers.bondInfo(json).filterKeys { it in wanted }
                    java.io.File(c.context.filesDir, "bond_info.txt").writeText(
                        info.entries.joinToString("\n") { (k, v) -> "$k;${v.rate};${v.maturity}" })
                }
                PriceParsers.pasardanaBonds(json, bonds.map { it.code }.toSet())
            }
            bonds.forEach { b ->
                val p = prices.getOrNull()?.get(b.code.uppercase())
                when {
                    p != null -> apply(listOf(b), Bonds.unitPriceE4(p.first), p.second, b.code, failed, errors)
                    // Retail SBN (ORI/SR/SBR/ST) isn't traded: face value. Also when the series has no market price.
                    Bonds.isRetailSbn(b.code) -> c.holdings.setPrice(b.id, Bonds.unitPriceE4(100.0), at.toEpochMilli())
                    prices.isSuccess -> {
                        // Not in the market list (maybe a typo like "FR100"): count at face value, but say so.
                        c.holdings.setPrice(b.id, Bonds.unitPriceE4(100.0), at.toEpochMilli())
                        c.holdings.setError(b.id, "Seri tidak ditemukan di data pasar, dihitung 100% — cek kode seri")
                    }
                    else -> fail(listOf(b.id), "${b.code}: ${message(prices.exceptionOrNull()!!)}", failed, errors)
                }
            }
        }

        val accounts = c.accounts.observeAll().first()
        val txs = c.transactions.observeAll().first()
        var updated = 0
        val refreshed = c.holdings.all().groupBy { it.accountId }
        for ((accountId, hs) in refreshed) {
            if (hs.any { it.id in failed }) continue // never write a partial value
            // Only flows up to now: future-dated ones are added by the ledger after the snapshot (never twice).
            val account = accounts.firstOrNull { it.id == accountId } ?: continue
            val upToNow = txs.filter { !it.date.isAfter(at) && (it.sourceAccountId == accountId || it.destinationAccountId == accountId) }
            // Money put in so far (ledger balance) → RDN cash = that − modal of holdings, when every modal is known.
            val value = Valuation.accountValue(hs, Ledger.balance(account, upToNow)) { since ->
                upToNow.filter { it.date.isAfter(since) }.sumOf { Ledger.effectOn(accountId, it) }
            } ?: continue
            c.snapshots.upsertAuto(Snapshot(accountId, at, value, null, null))
            updated++
        }

        val latest = c.snapshots.observeLatest().first()
        val gold = accounts.filter { it.type == AccountType.GOLD && latest[it.id]?.goldTenThousandths != null }
        if (gold.isNotEmpty()) {
            runCatching { PriceParsers.pegadaian(http.get(PEGADAIAN)).getOrThrow() }
                .onSuccess { (perGram, _) ->
                    gold.forEach { a ->
                        val prev = latest[a.id]?.goldPricePerGram
                        if (prev != null && jumped(prev, perGram, 0.3)) {
                            errors += "Emas: harga Pegadaian berubah lebih dari 30%, tidak dipakai"; return@forEach
                        }
                        // Always rebuild from the owner's last manual valuation + every flow since (up to now),
                        // so late-entered, back-dated purchases and rupiah-only transfers are never lost.
                        val base = c.snapshots.lastManual(a.id)?.takeIf { it.goldTenThousandths != null } ?: return@forEach
                        val flows = txs.filter {
                            (it.sourceAccountId == a.id || it.destinationAccountId == a.id) && it.date.isAfter(base.date) && !it.date.isAfter(at)
                        }
                        val grams = base.goldTenThousandths!! + flows.filter { it.goldTenThousandths != null }.sumOf {
                            if (it.destinationAccountId == a.id) it.goldTenThousandths!! else -it.goldTenThousandths!!
                        }
                        val rupiahOnly = flows.filter { it.goldTenThousandths == null }.sumOf { Ledger.effectOn(a.id, it) }
                        c.snapshots.upsertAuto(Snapshot(a.id, at, Grams.valueOf(grams, perGram) + rupiahOnly, grams, perGram))
                        updated++
                    }
                }
                .onFailure { e -> errors += "Emas: ${message(e)}" }
        }

        c.settings.setLastPriceRun(at.toEpochMilli())
        return RunResult(updated, errors)
    }

    private suspend fun fail(ids: List<Long>, msg: String, failed: MutableSet<Long>, errors: MutableList<String>) {
        failed += ids
        errors += msg
        ids.forEach { c.holdings.setError(it, msg.substringAfter(": ")) }
    }

    private fun jumped(old: Long, new: Long, limit: Double) = new > old * (1 + limit) || new < old * (1 - limit)

    /** Stores a new price unless it jumped more than 50% from the stored one (likely a source error). */
    private suspend fun apply(
        hs: List<id.dompetku.data.db.HoldingEntity>, priceE4: Long, time: Instant, label: String,
        failed: MutableSet<Long>, errors: MutableList<String>,
    ) {
        val suspicious = hs.any { h -> h.priceE4?.let { jumped(it, priceE4, 0.5) } == true }
        if (suspicious) {
            fail(hs.map { it.id }, "$label: Harga berubah lebih dari 50%. Simpan ulang di Isi portofolio untuk menerima harga baru", failed, errors)
            return
        }
        hs.forEach { c.holdings.setPrice(it.id, priceE4, time.toEpochMilli()) }
    }

    private fun message(e: Throwable) = when (e) {
        is HttpStatusException -> "Sumber harga menolak permintaan (HTTP ${e.code})"
        is java.io.IOException -> t("Tidak ada koneksi")
        else -> e.message ?: t("Gagal")
    }
}
