package id.dompetku.data

import id.dompetku.core.t

import android.content.Context
import androidx.room.withTransaction
import id.dompetku.data.db.AppDatabase
import id.dompetku.data.db.AttachmentEntity
import id.dompetku.data.db.CategorizationRuleEntity
import id.dompetku.data.db.toEntity
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import id.dompetku.importer.Categorizer
import java.io.File
import java.io.InputStream
import java.util.UUID

/** A confirmed import row: the transaction plus where it came from. */
data class ImportedTx(val tx: Tx, val reference: String?, val source: String, val attachmentId: Long?)

class ImportRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun referenceMap(): Map<String, Long> = db.transactions().references().associate { it.reference to it.id }

    /** Saves all rows in one database transaction. Income/expense rows must have a category. */
    suspend fun saveAll(items: List<ImportedTx>): Result<Int> {
        if (items.any { (it.tx.type == TxType.INCOME || it.tx.type == TxType.EXPENSE) && it.tx.categoryId == null }) {
            return Result.failure(IllegalArgumentException(t("Pilih kategori untuk semua transaksi yang dipilih")))
        }
        if (items.any { it.tx.amount <= 0 }) return Result.failure(IllegalArgumentException(t("Nominal harus lebih dari 0")))
        val now = clock()
        db.withTransaction {
            db.transactions().insertAll(items.map {
                it.tx.copy(id = 0).toEntity(now, now)
                    .copy(referenceNumber = it.reference, inputSource = it.source, attachmentId = it.attachmentId)
            })
        }
        return Result.success(items.size)
    }
}

class RuleRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun all(): List<CategorizationRuleEntity> = db.rules().all()

    /** Remember the owner's choice: next time this merchant gets this category automatically. */
    suspend fun learn(text: String, categoryId: Long) {
        val keyword = Categorizer.normalize(text)
        if (keyword.length < 3) return
        val now = clock()
        val existing = db.rules().byKeyword(keyword)
        db.rules().upsert(
            existing?.copy(suggestedCategoryId = categoryId, confidence = 0.9f, source = "LEARNED",
                usageCount = existing.usageCount + 1, updatedAt = now)
                ?: CategorizationRuleEntity(0, keyword, categoryId, null, 0.9f, "LEARNED", 1, now, now)
        )
    }

    /**
     * The owner confirmed [categoryId] for this merchant (inbox / voice). Same category again counts
     * up (used for auto-save); a different one starts the count over.
     */
    suspend fun confirm(text: String, categoryId: Long) {
        val keyword = Categorizer.normalize(text)
        if (keyword.length < 3) return
        val now = clock()
        val existing = db.rules().byKeyword(keyword)
        val same = existing != null && existing.source == "LEARNED" && existing.suggestedCategoryId == categoryId
        db.rules().upsert(
            existing?.copy(suggestedCategoryId = categoryId, confidence = 0.9f, source = "LEARNED",
                usageCount = if (same) existing.usageCount + 1 else 1, updatedAt = now)
                ?: CategorizationRuleEntity(0, keyword, categoryId, null, 0.9f, "LEARNED", 1, now, now)
        )
    }

    /** Learned rule for exactly this merchant, if any. */
    suspend fun learnedFor(text: String): CategorizationRuleEntity? =
        db.rules().byKeyword(Categorizer.normalize(text))?.takeIf { it.source == "LEARNED" }

    suspend fun resetLearned() = db.rules().deleteLearned()
}

/** Proof files (photos, screenshots, statements) in private storage: filesDir/attachments. */
class AttachmentStore(private val context: Context, private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dir get() = File(context.filesDir, "attachments").apply { mkdirs() }

    suspend fun save(input: InputStream, name: String, mime: String): Result<AttachmentEntity> = runCatching {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(60).ifEmpty { "file" }
        val file = File(dir, "${UUID.randomUUID()}-$safe")
        var size = 0L
        input.use { inp ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = inp.read(buf); if (n < 0) break
                    size += n
                    if (size > MAX_BYTES) { out.close(); file.delete(); error(t("File terlalu besar (maks 15 MB)")) }
                    out.write(buf, 0, n)
                }
            }
        }
        if (mime.startsWith("image/") && size > SHRINK_ABOVE) size = shrink(file) ?: size
        val e = AttachmentEntity(0, safe, file.name, if (mime.startsWith("image/")) "image/jpeg" else mime, size, clock())
        e.copy(id = db.attachments().insert(e))
    }

    /** Camera photos are re-saved at most 2048 px, JPEG 85, so proof files (and backups) stay small. */
    private fun shrink(file: File): Long? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val bmp = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        if (tmp.length() in 1 until file.length()) { tmp.renameTo(file); file.length() } else { tmp.delete(); null }
    }.getOrNull()

    suspend fun delete(a: AttachmentEntity) { file(a).delete(); db.attachments().delete(a.id) }

    fun file(a: AttachmentEntity): File = File(dir, a.filePath)
    suspend fun get(id: Long): AttachmentEntity? = db.attachments().get(id)

    companion object {
        const val MAX_BYTES = 15L * 1024 * 1024
        const val SHRINK_ABOVE = 1_500_000L
    }
}
