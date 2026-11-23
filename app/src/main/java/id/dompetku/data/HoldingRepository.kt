package id.dompetku.data

import id.dompetku.core.t

import id.dompetku.data.db.AppDatabase
import id.dompetku.data.db.HoldingEntity
import kotlinx.coroutines.flow.Flow

/** Stocks and fund units inside investment accounts, used for automatic daily valuation. */
class HoldingRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.holdings()

    fun observe(accountId: Long): Flow<List<HoldingEntity>> = dao.observe(accountId)
    fun observeAll(): Flow<List<HoldingEntity>> = dao.observeAll()
    suspend fun all(): List<HoldingEntity> = dao.all()

    /** Insert or update (id == 0 inserts). Editing units resets updatedAt to now. */
    suspend fun save(h: HoldingEntity): Result<Long> {
        if (h.code.isBlank()) return Result.failure(IllegalArgumentException(t("Isi kode saham atau pilih reksa dana")))
        if (h.unitsE4 <= 0) return Result.failure(IllegalArgumentException(t("Jumlah harus lebih dari 0")))
        val code = if (h.kind == "FUND") h.code.trim() else h.code.trim().uppercase()
        // Same instrument (only units or modal changed): keep today's price so values don't blank out.
        val old = if (h.id != 0L) dao.get(h.id) else null
        val keep = old != null && old.kind == h.kind && old.code == code
        val saved = h.copy(
            code = code, updatedAt = clock(),
            priceE4 = if (keep) old!!.priceE4 else h.priceE4, priceAt = if (keep) old!!.priceAt else h.priceAt,
            priceError = if (keep) old!!.priceError else h.priceError,
        )
        return Result.success(dao.upsert(saved).let { if (it == -1L) h.id else it })
    }

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun setPrice(id: Long, priceE4: Long, at: Long) = dao.setPrice(id, priceE4, at)
    suspend fun setError(id: Long, msg: String) = dao.setError(id, msg)
}
