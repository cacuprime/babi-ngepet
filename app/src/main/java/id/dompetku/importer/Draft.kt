package id.dompetku.importer

import id.dompetku.domain.TxType
import java.time.Instant

/** A transaction read from a document, not yet saved. Never affects balances. */
data class Draft(
    val type: TxType,
    val amount: Long,
    val date: Instant,
    val description: String,
    val merchant: String?,
    val reference: String?,
    val balanceAfter: Long?,
    /** 0..1 — how sure the reader is about amount and direction. */
    val confidence: Float,
)
