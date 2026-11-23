package id.dompetku.data

import id.dompetku.core.t

import androidx.room.withTransaction
import id.dompetku.capture.BankApp
import id.dompetku.capture.BankNotificationParser.Parsed
import id.dompetku.data.db.AppDatabase
import id.dompetku.data.db.InboxItemEntity
import id.dompetku.data.db.TransactionEntity
import id.dompetku.data.db.toDomain
import id.dompetku.domain.TxType
import id.dompetku.importer.Categorizer
import id.dompetku.importer.Duplicates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

enum class CaptureOutcome { AUTO_SAVED, WAITING, UNREAD, DUPLICATE_NOTIFICATION, IGNORED }

/** The owner's final values for one inbox item. TRANSFER needs [destinationAccountId]; income/expense need a category. */
data class InboxEdit(
    val id: Long,
    val type: TxType,
    val amount: Long,
    val categoryId: Long?,
    val accountId: Long?,
    val destinationAccountId: Long? = null,
    val description: String?,
    val merchant: String?,
)

/**
 * Bank-notification captures. Waiting items never touch balances; saving turns them into normal
 * confirmed transactions (input_source NOTIFICATION) and teaches the merchant's category.
 */
class InboxRepository(
    private val db: AppDatabase,
    private val rules: RuleRepository,
    private val accountFor: suspend (BankApp) -> Long?,
    private val autoSave: suspend () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.inbox()

    fun observeWaiting(): Flow<List<InboxItemEntity>> = dao.observeWaiting()
    fun observeUnread(): Flow<List<InboxItemEntity>> = dao.observeUnread()
    fun observeLastCapture(): Flow<Long?> = dao.observeLastCapture()
    suspend fun waitingCount(): Int = dao.waiting().size

    suspend fun capture(app: BankApp, raw: String, parsed: Parsed): CaptureOutcome {
        if (parsed is Parsed.Ignore) return CaptureOutcome.IGNORED
        val now = clock()
        val hash = hash(app.name + "|" + raw.trim())
        if (dao.countSince(hash, now - REPOST_WINDOW_MS) > 0) return CaptureOutcome.DUPLICATE_NOTIFICATION
        if (parsed !is Parsed.Tx) {
            dao.insert(InboxItemEntity(0, app.name, raw.take(MAX_RAW), hash, null, null, null, null, now, null, null, null, UNREAD, null, now))
            dao.trimUnread(KEEP_UNREAD)
            return CaptureOutcome.UNREAD
        }
        val d = parsed.draft
        // Same app, type and amount minutes ago: maybe one payment announced twice. Kept for review, never auto-saved.
        val sameRecently = dao.countSame(app.name, d.type.name, d.amount, now - SAME_PAYMENT_WINDOW_MS) > 0

        val account = accountFor(app)?.let { id -> db.accounts().get(id)?.takeIf { !it.isArchived }?.id }
        val categories = db.categories().observeAll().first()
        val suggestion = Categorizer(rules.all(), categories).suggest(d)
        val existing = db.transactions().observeAllConfirmed().first().map { it.toDomain() }
        val refs = db.transactions().references().associate { it.reference to it.id }
        /** Existing transaction id, or [SIMILAR_NOTIFICATION] when only another capture looks the same. */
        val dup = account?.let { Duplicates.find(d, it, existing, refs) } ?: if (sameRecently) SIMILAR_NOTIFICATION else null
        val item = InboxItemEntity(
            0, app.name, raw.take(MAX_RAW), hash, d.type.name, d.amount, d.merchant, d.description,
            d.date.toEpochMilli(), account, suggestion?.categoryId, dup, WAITING, null, now,
        )

        val learned = d.merchant?.let { rules.learnedFor(it) }
        val known = learned != null && learned.usageCount >= AUTO_SAVE_AFTER &&
            categories.any { it.id == learned.suggestedCategoryId && it.type == "EXPENSE" }
        if (d.type == TxType.EXPENSE && account != null && dup == null && known && autoSave()) {
            db.withTransaction {
                val txId = db.transactions().insert(
                    txEntity(d.type, d.amount, learned!!.suggestedCategoryId, account, null, d.description, d.merchant,
                        d.date.toEpochMilli(), d.reference, notes = "otomatis", now = now)
                )
                dao.insert(item.copy(categoryId = learned.suggestedCategoryId, status = SAVED, savedTxId = txId))
            }
            return CaptureOutcome.AUTO_SAVED
        }
        dao.insert(item)
        return CaptureOutcome.WAITING
    }

    /** Saves the edited items in one database transaction; nothing is saved if any is invalid. */
    suspend fun save(edits: List<InboxEdit>): Result<Int> {
        if (edits.isEmpty()) return Result.success(0)
        edits.firstNotNullOfOrNull { problem(it) }?.let { return Result.failure(IllegalArgumentException(it)) }
        val now = clock()
        val saved = db.withTransaction {
            var n = 0
            for (e in edits) {
                val item = dao.get(e.id)?.takeIf { it.status == WAITING } ?: continue
                val isTransfer = e.type == TxType.TRANSFER
                val txId = db.transactions().insert(
                    txEntity(e.type, e.amount, if (isTransfer) null else e.categoryId, e.accountId!!,
                        if (isTransfer) e.destinationAccountId else null, e.description, e.merchant, item.txDate, null, null, now)
                )
                dao.update(item.copy(status = SAVED, savedTxId = txId, txType = e.type.name, amount = e.amount,
                    accountId = e.accountId, categoryId = e.categoryId))
                n++
            }
            n
        }
        edits.filter { it.type != TxType.TRANSFER && it.categoryId != null }.forEach { e ->
            runCatching { rules.confirm(e.merchant ?: e.description ?: return@forEach, e.categoryId!!) }
        }
        return Result.success(saved)
    }

    suspend fun dismiss(id: Long) {
        dao.get(id)?.takeIf { it.status == WAITING || it.status == UNREAD }?.let { dao.update(it.copy(status = DISMISSED)) }
    }

    /** Drops handled items and unread texts older than 30 days. */
    suspend fun cleanup() = dao.deleteOld(clock() - KEEP_DAYS_MS)

    private suspend fun problem(e: InboxEdit): String? {
        if (e.amount <= 0) return t("Nominal harus lebih dari 0")
        val src = e.accountId?.let { db.accounts().get(it) }?.takeIf { !it.isArchived } ?: return t("Pilih akun untuk setiap transaksi")
        return when (e.type) {
            TxType.TRANSFER -> if (e.destinationAccountId == null || e.destinationAccountId == src.id) t("Pilih akun tujuan transfer") else null
            TxType.INCOME, TxType.EXPENSE -> if (e.categoryId == null) t("Pilih kategori untuk setiap transaksi") else null
            TxType.ADJUSTMENT -> t("Jenis tidak didukung")
        }
    }

    private fun txEntity(
        type: TxType, amount: Long, categoryId: Long?, account: Long, destination: Long?, description: String?,
        merchant: String?, date: Long, reference: String?, notes: String?, now: Long,
    ) = TransactionEntity(
        type = type.name, amount = amount, categoryId = categoryId, sourceAccountId = account, destinationAccountId = destination,
        goldTenThousandths = null, description = description?.take(200), merchant = merchant?.take(100), date = date,
        referenceNumber = reference, notes = notes, inputSource = "NOTIFICATION", createdAt = now, updatedAt = now,
    )

    companion object {
        const val WAITING = "WAITING"
        const val SAVED = "SAVED"
        const val DISMISSED = "DISMISSED"
        const val UNREAD = "UNREAD"
        const val SIMILAR_NOTIFICATION = 0L
        const val AUTO_SAVE_AFTER = 2
        const val KEEP_UNREAD = 30
        const val MAX_RAW = 1_000
        const val REPOST_WINDOW_MS = 30 * 60 * 1000L
        const val SAME_PAYMENT_WINDOW_MS = 3 * 60 * 1000L
        const val KEEP_DAYS_MS = 30L * 24 * 3600 * 1000

        fun hash(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
