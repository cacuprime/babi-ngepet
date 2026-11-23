package id.dompetku.prices

import id.dompetku.domain.JAKARTA
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

data class FundRef(val id: String, val name: String)

/** Reads the free public price sources. All return failures instead of throwing. */
object PriceParsers {
    /** Yahoo Finance chart API → (price per share in IDR, market time). */
    fun yahoo(json: String): Result<Pair<Double, Instant>> = runCatching {
        val chart = JSONObject(json).getJSONObject("chart")
        val result = chart.optJSONArray("result")
        if (result == null || result.length() == 0) throw IllegalStateException("Kode saham tidak ditemukan")
        val meta = result.getJSONObject(0).getJSONObject("meta")
        val price = meta.optDouble("regularMarketPrice", Double.NaN)
        if (price.isNaN() || price <= 0) throw IllegalStateException("Harga saham tidak tersedia")
        val currency = meta.optString("currency")
        if (currency.isNotBlank() && currency != "IDR") throw IllegalStateException("Harga bukan dalam Rupiah ($currency)")
        price to Instant.ofEpochSecond(meta.optLong("regularMarketTime", 0))
    }.recoverCatching { e -> throw if (e is IllegalStateException) e else IllegalStateException("Data harga saham tidak terbaca") }

    /** Pasardana fund list → NAV per unit and date for the wanted fund ids. */
    fun pasardana(json: String, ids: Set<String>): Map<String, Pair<Double, Instant>> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.opt("Id")?.toString() ?: return@mapNotNull null
            if (id !in ids) return@mapNotNull null
            val nav = o.optDouble("NetAssetValue", Double.NaN).takeIf { !it.isNaN() && it > 0 } ?: return@mapNotNull null
            val date = o.optString("LastUpdate").takeIf { it.length >= 10 }?.let { LocalDate.parse(it.take(10)) } ?: return@mapNotNull null
            id to (nav to date.atTime(17, 0).atZone(JAKARTA).toInstant())
        }.toMap()
    }.getOrDefault(emptyMap())

    /** Pasardana bond list → last market price (% of face) and date for the wanted series codes. */
    fun pasardanaBonds(json: String, codes: Set<String>): Map<String, Pair<Double, Instant>> = runCatching {
        val wanted = codes.map { it.uppercase() }.toSet()
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val code = o.optString("SecurityCode").uppercase().takeIf { it in wanted } ?: return@mapNotNull null
            val last = o.optJSONObject("BondLastData") ?: return@mapNotNull null
            val pct = last.optDouble("LastPrice", Double.NaN).takeIf { !it.isNaN() && it in 30.0..200.0 } ?: return@mapNotNull null
            val date = last.optString("DateModified").takeIf { it.length >= 10 }?.let { LocalDate.parse(it.take(10)) } ?: return@mapNotNull null
            code to (pct to date.atTime(17, 0).atZone(JAKARTA).toInstant())
        }.toMap()
    }.getOrDefault(emptyMap())

    data class BondInfo(val rate: Double, val maturity: LocalDate)

    /** Coupon rate and maturity per series (for the accrued-coupon line). */
    fun bondInfo(json: String): Map<String, BondInfo> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val code = o.optString("SecurityCode").uppercase().ifBlank { return@mapNotNull null }
            val rate = o.optDouble("InterestRate", Double.NaN).takeIf { !it.isNaN() } ?: return@mapNotNull null
            val mat = o.optString("MatureDate").takeIf { it.length >= 10 }?.let { LocalDate.parse(it.take(10)) } ?: return@mapNotNull null
            code to BondInfo(rate, mat)
        }.toMap()
    }.getOrDefault(emptyMap())

    /** All funds (id + name) for the fund search box. */
    fun fundIndex(json: String): List<FundRef> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.opt("Id")?.toString() ?: return@mapNotNull null
            val name = o.optString("Name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (o.optDouble("NetAssetValue", 0.0) <= 0) null else FundRef(id, name)
        }
    }.getOrDefault(emptyList())

    /** Pegadaian Tabungan Emas → buy-back (owner's sell) price per gram in rupiah, and its date. */
    fun pegadaian(json: String): Result<Pair<Long, Instant>> = runCatching {
        val data = JSONObject(json).optJSONObject("data") ?: throw IllegalStateException("Harga emas Pegadaian tidak tersedia")
        val perUnit = data.optLong("hargaBeli", 0)
        val unit = data.optString("unit").toDoubleOrNull() ?: 0.0
        if (perUnit <= 0 || unit <= 0) throw IllegalStateException("Harga emas Pegadaian tidak tersedia")
        val perGram = BigDecimal(perUnit).divide(BigDecimal(data.optString("unit")), 0, RoundingMode.HALF_UP).toLong()
        // Guard against a silent format change (e.g. a per-gram price with unit still "0.01" → 100× too high).
        if (perGram !in 300_000L..20_000_000L) throw IllegalStateException("Format harga Pegadaian berubah, harga tidak dipakai")
        val day = data.optString("tglBerlaku").takeIf { it.length >= 10 }?.let { LocalDate.parse(it.take(10)) }
        perGram to (day?.atTime(LocalDateTime.now(JAKARTA).toLocalTime())?.atZone(JAKARTA)?.toInstant() ?: Instant.now())
    }.recoverCatching { e -> throw if (e is IllegalStateException) e else IllegalStateException("Data harga emas tidak terbaca") }

    /** Price ×10 000 as Long, half up (NAVs have up to 4 decimals). */
    fun toE4(price: Double): Long = BigDecimal(price.toString()).movePointRight(4).setScale(0, RoundingMode.HALF_UP).toLong()
}
