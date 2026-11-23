package id.dompetku.domain

import id.dompetku.core.Grams

/**
 * The accounting rules. Every number on screen comes from here, so every total
 * can be traced back to transactions, opening balances and snapshots.
 */
object Ledger {

    /** Signed effect of one transaction on one account's ledger balance. */
    fun effectOn(accountId: Long, tx: Tx): Long = when (tx.type) {
        TxType.INCOME -> if (tx.sourceAccountId == accountId) tx.amount else 0
        TxType.EXPENSE -> if (tx.sourceAccountId == accountId) -tx.amount else 0
        TxType.ADJUSTMENT -> if (tx.sourceAccountId == accountId) tx.amount else 0
        TxType.TRANSFER -> when (accountId) {
            tx.sourceAccountId -> -tx.amount
            tx.destinationAccountId -> tx.amount
            else -> 0
        }
    }

    /** Opening balance + income − expense − transfers out + transfers in ± adjustments. */
    fun balance(account: Account, txs: List<Tx>): Long =
        account.openingBalance + txs.sumOf { effectOn(account.id, it) }

    /** Valued accounts: latest snapshot + net flows after it. Others: ledger balance. */
    fun currentValue(account: Account, txs: List<Tx>, latest: Snapshot?): Long {
        if (!account.type.isValued || latest == null) return balance(account, txs)
        val after = txs.filter { it.date.isAfter(latest.date) }
        if (account.type == AccountType.GOLD && latest.goldTenThousandths != null && latest.goldPricePerGram != null) {
            val grams = goldGrams(account, txs, latest) ?: latest.goldTenThousandths
            // Grams known: value follows grams. Flows with no grams recorded still move rupiah.
            val unknownGramFlows = after.filter { it.goldTenThousandths == null }.sumOf { effectOn(account.id, it) }
            // Rupiah the snapshot already carries beyond its grams (an automatic snapshot keeps gram-less transfers).
            val carried = latest.currentValue - Grams.valueOf(latest.goldTenThousandths, latest.goldPricePerGram)
            return Grams.valueOf(grams, latest.goldPricePerGram) + unknownGramFlows + carried
        }
        return latest.currentValue + after.sumOf { effectOn(account.id, it) }
    }

    /** Gold grams = snapshot grams + grams recorded on later transfers in/out. Null if unknown. */
    fun goldGrams(account: Account, txs: List<Tx>, latest: Snapshot?): Long? {
        if (account.type != AccountType.GOLD || latest?.goldTenThousandths == null) return null
        val delta = txs.filter { it.date.isAfter(latest.date) && it.goldTenThousandths != null }.sumOf {
            when (account.id) {
                it.destinationAccountId -> it.goldTenThousandths!!
                it.sourceAccountId -> -it.goldTenThousandths!!
                else -> 0L
            }
        }
        return latest.goldTenThousandths + delta
    }

    fun summary(accounts: List<Account>, txs: List<Tx>, snapshots: Map<Long, Snapshot>, period: Period): Summary {
        var total = 0L; var liquid = 0L; var invest = 0L
        for (a in accounts) {
            val related = txs.filter { it.sourceAccountId == a.id || it.destinationAccountId == a.id }
            val v = currentValue(a, related, snapshots[a.id])
            total += v
            if (a.isLiquid) liquid += v
            if (a.type.isValued) invest += v
        }
        val inPeriod = txs.filter { period.contains(it.date) }
        val income = inPeriod.filter { it.type == TxType.INCOME }.sumOf { it.amount }
        val expense = inPeriod.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
        return Summary(total, liquid, invest, income, expense, income - expense)
    }
}
