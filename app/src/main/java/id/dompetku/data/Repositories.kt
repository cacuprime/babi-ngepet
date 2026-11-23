package id.dompetku.data

import id.dompetku.core.t

import androidx.room.withTransaction
import id.dompetku.data.db.AccountGroupEntity
import id.dompetku.data.db.AccountGroupMemberEntity
import id.dompetku.data.db.AppDatabase
import id.dompetku.data.db.CategoryEntity
import id.dompetku.data.db.InvestmentSnapshotEntity
import id.dompetku.data.db.toDomain
import id.dompetku.data.db.toEntity
import id.dompetku.domain.Account
import id.dompetku.domain.Ledger
import id.dompetku.domain.Period
import id.dompetku.domain.Snapshot
import id.dompetku.domain.Summary
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class TxFilter(
    val period: Period? = null,
    val query: String? = null,
    val type: TxType? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
)

data class AccountView(val account: Account, val balance: Long, val currentValue: Long, val goldTenThousandths: Long?)

data class AccountGroup(val id: Long, val name: String, val accountIds: List<Long>)

data class GroupView(val group: AccountGroup, val total: Long, val members: List<AccountView>)

class GroupRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.groups()

    fun observeAll(): Flow<List<AccountGroup>> = combine(dao.observeGroups(), dao.observeMembers()) { groups, members ->
        val byGroup = members.groupBy({ it.groupId }, { it.accountId })
        groups.map { AccountGroup(it.id, it.name, byGroup[it.id].orEmpty().sorted()) }
    }

    /** Insert when id == 0, otherwise rename and replace the member list. */
    suspend fun save(name: String, accountIds: List<Long>, id: Long = 0): Result<Long> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException(t("Nama grup wajib diisi")))
        if (accountIds.isEmpty()) return Result.failure(IllegalArgumentException(t("Pilih minimal satu akun")))
        return db.withTransaction {
            val gid = if (id == 0L) {
                dao.insert(AccountGroupEntity(name = name.trim(), createdAt = clock()))
            } else {
                val existing = dao.all().firstOrNull { it.id == id }
                    ?: return@withTransaction Result.failure(IllegalArgumentException(t("Grup tidak ditemukan")))
                dao.update(existing.copy(name = name.trim())); dao.clearMembers(id); id
            }
            dao.insertMembers(accountIds.distinct().map { AccountGroupMemberEntity(gid, it) })
            Result.success(gid)
        }
    }

    suspend fun delete(id: Long) = db.withTransaction { dao.clearMembers(id); dao.delete(id) }
}

class AccountRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.accounts()

    fun observeAll(): Flow<List<Account>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun get(id: Long): Account? = dao.get(id)?.toDomain()

    suspend fun create(a: Account): Long {
        require(a.name.isNotBlank()) { t("Nama akun wajib diisi") }
        return dao.insert(a.copy(id = 0).toEntity(clock()))
    }

    suspend fun update(a: Account) = dao.update(a.toEntity(clock()))
    suspend fun archive(id: Long, archived: Boolean) = dao.setArchived(id, archived, clock())

    /** Refused when the account has transactions: archive it instead. */
    suspend fun delete(id: Long): Result<Unit> = db.withTransaction {
        if (db.transactions().countForAccount(id) > 0) {
            Result.failure(IllegalStateException(t("Akun ini punya transaksi. Arsipkan saja agar riwayat tetap utuh.")))
        } else {
            db.snapshots().deleteForAccount(id)
            db.groups().removeAccount(id)
            db.holdings().deleteForAccount(id)
            db.inbox().detachAccount(id)
            db.goals().detachAccount(id)
            dao.delete(id)
            Result.success(Unit)
        }
    }
}

class TransactionRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.transactions()

    fun observe(filter: TxFilter): Flow<List<Tx>> = dao.observe(
        filter.period?.start?.toEpochMilli(), filter.period?.endExclusive?.toEpochMilli(), filter.type?.name,
        filter.categoryId, filter.accountId, filter.minAmount, filter.maxAmount, filter.query?.trim()?.removePrefix("#")?.ifEmpty { null },
    ).map { l -> l.map { it.toDomain() } }

    fun observeAll(): Flow<List<Tx>> = dao.observeAllConfirmed().map { l -> l.map { it.toDomain() } }
    suspend fun get(id: Long): Tx? = dao.get(id)?.toDomain()
    suspend fun lastUsedAccount(type: TxType): Long? = dao.lastUsedAccount(type.name)
    suspend fun recentCategories(type: TxType): List<Long> = dao.recentCategories(type.name)

    /** Insert when id == 0, otherwise update. Validation messages are shown to the user. */
    suspend fun save(tx: Tx, source: String = "MANUAL"): Result<Long> {
        validate(tx)?.let { return Result.failure(IllegalArgumentException(it)) }
        val clean = when (tx.type) {
            TxType.TRANSFER -> tx.copy(categoryId = null)
            else -> tx.copy(destinationAccountId = null, goldTenThousandths = null)
        }
        val now = clock()
        return db.withTransaction {
            if (clean.id == 0L) {
                Result.success(dao.insert(clean.toEntity(now, now).copy(inputSource = source)))
            } else {
                val existing = dao.get(clean.id) ?: return@withTransaction Result.failure(
                    IllegalArgumentException(t("Transaksi tidak ditemukan")))
                // Keep what the edit screen doesn't show (proof file, bank reference, origin).
                dao.update(clean.toEntity(existing.createdAt, now).copy(
                    attachmentId = existing.attachmentId, referenceNumber = existing.referenceNumber,
                    inputSource = existing.inputSource, status = existing.status,
                ))
                Result.success(clean.id)
            }
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    private fun validate(tx: Tx): String? = when {
        tx.type == TxType.ADJUSTMENT && tx.amount == 0L -> t("Nominal penyesuaian tidak boleh 0")
        tx.type != TxType.ADJUSTMENT && tx.amount <= 0L -> t("Nominal harus lebih dari 0")
        tx.type == TxType.TRANSFER && tx.destinationAccountId == null -> t("Pilih akun tujuan")
        tx.type == TxType.TRANSFER && tx.destinationAccountId == tx.sourceAccountId -> t("Akun asal dan tujuan harus berbeda")
        (tx.type == TxType.INCOME || tx.type == TxType.EXPENSE) && tx.categoryId == null -> t("Pilih kategori")
        else -> null
    }
}

class SnapshotRepository(private val db: AppDatabase) {
    companion object { const val AUTO = "AUTO" }
    private val dao = db.snapshots()

    suspend fun add(s: Snapshot, notes: String? = null) {
        dao.insert(
            InvestmentSnapshotEntity(
                accountId = s.accountId, totalContribution = null, currentValue = s.currentValue,
                goldTenThousandths = s.goldTenThousandths, goldPricePerGram = s.goldPricePerGram,
                date = s.date.toEpochMilli(), notes = notes,
            )
        )
    }

    /** The owner's last manual valuation (never an automatic one): the baseline for daily gold values. */
    suspend fun lastManual(accountId: Long): Snapshot? = dao.lastManual(accountId)?.toDomain()

    /** Automatic daily valuation: replaces today's earlier AUTO snapshot (Jakarta day) instead of adding another. */
    suspend fun upsertAuto(s: Snapshot) = db.withTransaction {
        val day = Period.today(s.date)
        dao.deleteAuto(s.accountId, day.start.toEpochMilli(), day.endExclusive.toEpochMilli())
        add(s, notes = AUTO)
    }

    /** Latest snapshot per account (by date, then insertion order). */
    fun observeLatest(): Flow<Map<Long, Snapshot>> = dao.observeAll().map { all ->
        all.groupBy { it.accountId }.mapValues { (_, l) -> l.maxWith(compareBy({ it.date }, { it.id })).toDomain() }
    }

    fun observeHistory(accountId: Long): Flow<List<Snapshot>> =
        dao.observeHistory(accountId).map { l -> l.map { it.toDomain() } }
}

class CategoryRepository(private val db: AppDatabase) {
    private val dao = db.categories()

    fun observe(type: String): Flow<List<CategoryEntity>> = dao.observe(type)
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()

    suspend fun upsert(c: CategoryEntity): Long {
        require(c.name.isNotBlank()) { t("Nama kategori wajib diisi") }
        return dao.upsert(c)
    }

    suspend fun setBucket(id: Long, bucket: id.dompetku.domain.Bucket) = dao.setBucket(id, bucket.name)

    suspend fun delete(id: Long): Result<Unit> = db.withTransaction {
        if (db.transactions().countForCategory(id) > 0) {
            Result.failure(IllegalStateException(t("Kategori ini sudah dipakai transaksi, jadi tidak bisa dihapus.")))
        } else if (dao.childCount(id) > 0) {
            Result.failure(IllegalStateException(t("Hapus subkategorinya dulu.")))
        } else {
            dao.delete(id); Result.success(Unit)
        }
    }
}

class DashboardRepository(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val snapshots: SnapshotRepository,
    private val groups: GroupRepository,
) {
    /** Group total = sum of current values of member accounts that still exist (archived included). */
    fun groupViews(): Flow<List<GroupView>> = combine(groups.observeAll(), accountViews()) { gs, views ->
        val byId = views.associateBy { it.account.id }
        gs.map { g ->
            val members = g.accountIds.mapNotNull { byId[it] }
            GroupView(g, members.sumOf { it.currentValue }, members)
        }
    }

    fun summary(period: Period): Flow<Summary> =
        combine(accounts.observeAll(), transactions.observeAll(), snapshots.observeLatest()) { a, t, s ->
            Ledger.summary(a, t, s, period)
        }

    fun accountViews(): Flow<List<AccountView>> =
        combine(accounts.observeAll(), transactions.observeAll(), snapshots.observeLatest()) { accs, txs, snaps ->
            accs.map { a ->
                val related = txs.filter { it.sourceAccountId == a.id || it.destinationAccountId == a.id }
                AccountView(
                    a, Ledger.balance(a, related), Ledger.currentValue(a, related, snaps[a.id]),
                    Ledger.goldGrams(a, related, snaps[a.id]),
                )
            }
        }
}
