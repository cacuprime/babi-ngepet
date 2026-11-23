package id.dompetku.prices

import id.dompetku.data.db.HoldingEntity
import java.math.BigInteger
import java.time.Instant

object Valuation {
    private val E8 = BigInteger.valueOf(100_000_000)
    private val HALF = BigInteger.valueOf(50_000_000)

    /** units (×10 000) × price (×10 000) → whole rupiah, half up, without overflow. */
    fun holdingValue(unitsE4: Long, priceE4: Long): Long =
        BigInteger.valueOf(unitsE4).multiply(BigInteger.valueOf(priceE4)).add(HALF).divide(E8).toLong()

    /**
     * Sum of holdings, plus net money moved into the account since the owner last edited units
     * (so a transfer to Bibit counts until units are updated). Null if any holding has no price yet.
     */
    fun accountValue(holdings: List<HoldingEntity>, flowsSince: (Instant) -> Long): Long? {
        if (holdings.isEmpty() || holdings.any { it.priceE4 == null }) return null
        val sum = holdings.sumOf { holdingValue(it.unitsE4, it.priceE4!!) }
        val unitsUpdated = Instant.ofEpochMilli(holdings.maxOf { it.updatedAt })
        return sum + flowsSince(unitsUpdated)
    }

    /**
     * Cash waiting in the account (Bibit RDN): money put in (ledger balance) minus the modal of every holding.
     * Null when any holding has no modal yet. May be negative (shown, so the owner can correct it).
     */
    fun rdnCash(ledgerBalance: Long, holdings: List<HoldingEntity>): Long? =
        if (holdings.any { it.costRp == null }) null else ledgerBalance - holdings.sumOf { it.costRp!! }

    /**
     * Holdings at today's prices plus RDN cash when every holding has a modal;
     * otherwise the 1.3 rule (holdings + money moved in since units were last edited).
     */
    fun accountValue(holdings: List<HoldingEntity>, ledgerBalance: Long, flowsSince: (Instant) -> Long): Long? {
        if (holdings.isEmpty() || holdings.any { it.priceE4 == null }) return null
        val rdn = rdnCash(ledgerBalance, holdings) ?: return accountValue(holdings, flowsSince)
        return holdings.sumOf { holdingValue(it.unitsE4, it.priceE4!!) } + rdn
    }

    /**
     * A sale moves the proceeds into RDN, but RDN only rises by the modal removed. The difference
     * (realised profit, or loss if negative) is recorded as an adjustment so net worth keeps it.
     */
    fun saleAdjustment(proceeds: Long, modalRemoved: Long): Long = proceeds - modalRemoved

    /** Profit in rupiah and percent of modal (percent null when there is no modal). */
    fun profit(value: Long, cost: Long): Pair<Long, Double?> =
        (value - cost) to (if (cost > 0) (value - cost) * 100.0 / cost else null)
}
