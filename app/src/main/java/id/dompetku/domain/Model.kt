package id.dompetku.domain

import java.time.Instant

enum class AccountType(val defaultLiquid: Boolean, val isValued: Boolean, val labelId: String) {
    CASH(true, false, "Tunai"),
    BANK(true, false, "Bank"),
    EWALLET(true, false, "E-Wallet"),
    SAVINGS(true, false, "Tabungan"),
    DEPOSIT(false, false, "Deposito"),
    INVESTMENT(false, true, "Investasi"),
    GOLD(false, true, "Emas"),
    OTHER_ASSET(false, false, "Aset Lain"),
    ;
    val label: String get() = id.dompetku.core.t(labelId)
}

enum class TxType(val labelId: String) {
    INCOME("Pemasukan"), EXPENSE("Pengeluaran"), TRANSFER("Transfer"), ADJUSTMENT("Penyesuaian");

    val label: String get() = id.dompetku.core.t(labelId)
}

data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val provider: String?,
    val openingBalance: Long,
    val isLiquid: Boolean,
    val isArchived: Boolean,
    val createdAt: Instant,
)

/** amount > 0, except ADJUSTMENT which is signed and non-zero. */
data class Tx(
    val id: Long,
    val type: TxType,
    val amount: Long,
    val categoryId: Long?,
    val sourceAccountId: Long,
    val destinationAccountId: Long?,
    val goldTenThousandths: Long?,
    val date: Instant,
    val description: String?,
    val merchant: String?,
    val notes: String?,
    val tags: String? = null,
)

data class Snapshot(
    val accountId: Long,
    val date: Instant,
    val currentValue: Long,
    val goldTenThousandths: Long?,
    val goldPricePerGram: Long?,
)

data class Summary(
    val totalAssets: Long,
    val liquid: Long,
    val investmentValue: Long,
    val income: Long,
    val expense: Long,
    val netCashFlow: Long,
)
