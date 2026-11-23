package id.dompetku.prices

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Indonesian government bonds as Bibit sells them: 1 unit = Rp1.000.000 face value. */
object Bonds {
    const val UNIT_FACE_RP = 1_000_000L
    private val RETAIL = Regex("""^(ORI|SR|SBR|ST)\d+""", RegexOption.IGNORE_CASE)

    /** Retail SBN can't be traded on the market: valued at face value (100%). */
    fun isRetailSbn(code: String) = RETAIL.containsMatchIn(code.trim())

    /** Price per unit ×10 000 from a percentage of face value (e.g. 99.8875 → Rp998.875). */
    fun unitPriceE4(percent: Double): Long =
        BigDecimal(percent.toString()).multiply(BigDecimal(UNIT_FACE_RP)).movePointLeft(2).movePointRight(4)
            .setScale(0, RoundingMode.HALF_UP).toLong()

    /** Accrued (unpaid) coupon on semi-annual coupons, actual/actual, before tax. Information only. */
    fun accruedCoupon(nominalRp: Long, annualRate: Double, maturity: LocalDate, today: LocalDate): Long {
        if (!today.isBefore(maturity)) return 0
        var next = maturity
        while (next.minusMonths(6).isAfter(today)) next = next.minusMonths(6)
        val last = next.minusMonths(6)
        val period = ChronoUnit.DAYS.between(last, next).toDouble()
        val elapsed = ChronoUnit.DAYS.between(last, today).toDouble()
        if (elapsed <= 0) return 0
        return BigDecimal(nominalRp * annualRate / 2 * elapsed / period).setScale(0, RoundingMode.HALF_UP).toLong()
    }
}
