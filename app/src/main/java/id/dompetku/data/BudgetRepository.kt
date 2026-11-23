package id.dompetku.data

import id.dompetku.core.t

import id.dompetku.data.db.AppDatabase
import id.dompetku.data.db.BudgetEntity
import id.dompetku.data.db.GoalEntity
import id.dompetku.domain.Bucket
import id.dompetku.domain.Budget
import id.dompetku.domain.BudgetMode
import id.dompetku.domain.Budgets
import id.dompetku.domain.Period
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

fun BudgetEntity.toDomain() = Budget(
    id, Period(Instant.ofEpochMilli(start), Instant.ofEpochMilli(endExclusive)), startDay,
    runCatching { BudgetMode.valueOf(mode) }.getOrDefault(BudgetMode.FIXED), income,
    mapOf(Bucket.NEEDS to needsPct, Bucket.WANTS to wantsPct, Bucket.SAVINGS to savingsPct),
    mapOf(Bucket.NEEDS to needs, Bucket.WANTS to wants, Bucket.SAVINGS to savings),
)

fun Budget.toEntity(now: Long) = BudgetEntity(
    id, period.start.toEpochMilli(), period.endExclusive.toEpochMilli(), startDay, mode.name, income,
    percents[Bucket.NEEDS] ?: 0, percents[Bucket.WANTS] ?: 0, percents[Bucket.SAVINGS] ?: 0,
    limits[Bucket.NEEDS] ?: 0, limits[Bucket.WANTS] ?: 0, limits[Bucket.SAVINGS] ?: 0, now,
)

class BudgetRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.budgets()

    fun observeAll(): Flow<List<Budget>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    /** The budget whose period contains [now] (newest wins if several overlap). */
    fun current(all: List<Budget>, now: Instant): Budget? = all.firstOrNull { it.period.contains(now) }

    suspend fun save(b: Budget): Result<Long> {
        if (!b.period.endExclusive.isAfter(b.period.start)) return Result.failure(IllegalArgumentException(t("Tanggal akhir harus setelah tanggal mulai")))
        if (b.limits.values.any { it < 0 }) return Result.failure(IllegalArgumentException(t("Nominal tidak boleh negatif")))
        if (b.mode == BudgetMode.PERCENT && b.percents.values.sum() != 100) return Result.failure(IllegalArgumentException(t("Total persentase harus 100%")))
        return Result.success(dao.upsert(b.toEntity(clock())))
    }

    /** Copies [b] to the period right after it; percent budgets keep their split of the same income. */
    suspend fun copyToNext(b: Budget): Result<Long> = save(b.copy(id = 0, period = Budgets.nextPeriod(b)))

    /** Copies [b] forward to the period that contains [now] (skipping months nobody budgeted). */
    suspend fun copyToCurrent(b: Budget, now: Instant): Result<Long> {
        var p = b.copy(id = 0)
        var guard = 0
        do { p = p.copy(period = Budgets.nextPeriod(p)); guard++ } while (!p.period.endExclusive.isAfter(now) && guard < 600)
        return save(p)
    }

    suspend fun delete(id: Long) = dao.delete(id)
}

class GoalRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.goals()

    fun observeAll(): Flow<List<GoalEntity>> = dao.observeAll()

    suspend fun save(g: GoalEntity): Result<Long> = when {
        g.name.isBlank() -> Result.failure(IllegalArgumentException(t("Nama tujuan wajib diisi")))
        g.target <= 0 -> Result.failure(IllegalArgumentException(t("Target harus lebih dari 0")))
        else -> Result.success(dao.upsert(g.copy(name = g.name.trim(), createdAt = if (g.id == 0L) clock() else g.createdAt)))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
