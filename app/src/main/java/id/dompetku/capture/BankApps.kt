package id.dompetku.capture

/** Bank apps whose notifications Dompetku reads. Anything else is ignored without being looked at. */
enum class BankApp(val packages: List<String>, val label: String, val labelHints: List<String>, val accountHints: List<String>) {
    WONDR(listOf("id.bni.wondr"), "wondr by BNI", listOf("wondr"), listOf("bni", "wondr")),
    SEABANK(listOf("id.co.bankbkemobile.digitalbank", "id.co.seabank"), "SeaBank", listOf("seabank"), listOf("seabank", "sea bank")),
    LIVIN(listOf("id.bmri.livin", "id.bmri.livinmerchant.customer"), "Livin' by Mandiri", listOf("livin"), listOf("mandiri", "livin")),
    ;

    companion object {
        fun of(pkg: String, appLabel: String?): BankApp? {
            entries.firstOrNull { pkg in it.packages }?.let { return it }
            val l = appLabel?.lowercase() ?: return null
            return entries.firstOrNull { b -> b.labelHints.any { l.contains(it) } }
        }
    }
}
