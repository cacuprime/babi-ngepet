package id.dompetku.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class RefRow(val id: Long, @androidx.room.ColumnInfo(name = "reference_number") val reference: String)

/** Whole-database dump and replace, used by backup/restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM accounts") suspend fun accounts(): List<AccountEntity>
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM investment_snapshots") suspend fun snapshots(): List<InvestmentSnapshotEntity>

    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM investment_snapshots") suspend fun clearSnapshots()

    @Insert suspend fun insertAccounts(l: List<AccountEntity>)
    @Insert suspend fun insertCategories(l: List<CategoryEntity>)
    @Insert suspend fun insertTransactions(l: List<TransactionEntity>)
    @Insert suspend fun insertSnapshots(l: List<InvestmentSnapshotEntity>)

    @Query("SELECT * FROM account_groups") suspend fun groups(): List<AccountGroupEntity>
    @Query("SELECT * FROM account_group_members") suspend fun groupMembers(): List<AccountGroupMemberEntity>
    @Query("DELETE FROM account_groups") suspend fun clearGroups()
    @Query("DELETE FROM account_group_members") suspend fun clearGroupMembers()
    @Insert suspend fun insertGroups(l: List<AccountGroupEntity>)
    @Insert suspend fun insertGroupMembers(l: List<AccountGroupMemberEntity>)
}

@Dao
interface AttachmentDao {
    @Insert suspend fun insert(a: AttachmentEntity): Long
    @Query("SELECT * FROM attachments WHERE id = :id") suspend fun get(id: Long): AttachmentEntity?
    @Query("SELECT * FROM attachments") suspend fun all(): List<AttachmentEntity>
    @Query("SELECT * FROM attachments WHERE id IN (SELECT attachment_id FROM transactions WHERE attachment_id IS NOT NULL)")
    suspend fun referenced(): List<AttachmentEntity>
    @Query("DELETE FROM attachments WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM attachments") suspend fun count(): Int
    @Query("DELETE FROM attachments") suspend fun clear()
    @Insert suspend fun insertAll(l: List<AttachmentEntity>)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM categorization_rules") suspend fun all(): List<CategorizationRuleEntity>
    @Query("SELECT * FROM categorization_rules WHERE keyword_or_merchant = :keyword") suspend fun byKeyword(keyword: String): CategorizationRuleEntity?
    @Upsert suspend fun upsert(r: CategorizationRuleEntity): Long
    @Query("DELETE FROM categorization_rules WHERE source = 'LEARNED'") suspend fun deleteLearned()
    @Query("DELETE FROM categorization_rules") suspend fun clear()
    @Insert suspend fun insertAll(l: List<CategorizationRuleEntity>)
}

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings WHERE account_id = :accountId ORDER BY id") fun observe(accountId: Long): Flow<List<HoldingEntity>>
    @Query("SELECT * FROM holdings ORDER BY id") fun observeAll(): Flow<List<HoldingEntity>>
    @Query("SELECT * FROM holdings ORDER BY id") suspend fun all(): List<HoldingEntity>
    @Upsert suspend fun upsert(h: HoldingEntity): Long
    @Query("DELETE FROM holdings WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM holdings WHERE account_id = :accountId") suspend fun deleteForAccount(accountId: Long)
    @Query("UPDATE holdings SET price_e4 = :priceE4, price_at = :at, price_error = NULL WHERE id = :id")
    suspend fun setPrice(id: Long, priceE4: Long, at: Long)
    @Query("UPDATE holdings SET price_error = :msg WHERE id = :id") suspend fun setError(id: Long, msg: String)
    @Query("SELECT * FROM holdings WHERE id = :id") suspend fun get(id: Long): HoldingEntity?
    @Query("DELETE FROM holdings") suspend fun clear()
    @Insert suspend fun insertAll(l: List<HoldingEntity>)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM account_groups ORDER BY id") fun observeGroups(): Flow<List<AccountGroupEntity>>
    @Query("SELECT * FROM account_group_members") fun observeMembers(): Flow<List<AccountGroupMemberEntity>>
    @Query("SELECT * FROM account_groups ORDER BY id") suspend fun all(): List<AccountGroupEntity>

    @Insert suspend fun insert(g: AccountGroupEntity): Long
    @Update suspend fun update(g: AccountGroupEntity)
    @Insert suspend fun insertMembers(l: List<AccountGroupMemberEntity>)

    @Query("DELETE FROM account_groups WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM account_group_members WHERE group_id = :groupId") suspend fun clearMembers(groupId: Long)
    @Query("DELETE FROM account_group_members WHERE account_id = :accountId") suspend fun removeAccount(accountId: Long)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY is_archived, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Insert suspend fun insert(a: AccountEntity): Long
    @Update suspend fun update(a: AccountEntity)

    @Query("UPDATE accounts SET is_archived = :archived, updated_at = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE category_type = :type ORDER BY is_default DESC, id")
    fun observe(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Upsert suspend fun upsert(c: CategoryEntity): Long

    @Query("SELECT COUNT(*) FROM categories WHERE parent_id = :id") suspend fun childCount(id: Long): Int
    @Query("UPDATE categories SET bucket = :bucket WHERE id = :id") suspend fun setBucket(id: Long, bucket: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT * FROM transactions
        WHERE status = 'CONFIRMED'
          AND (:start IS NULL OR transaction_date >= :start)
          AND (:end IS NULL OR transaction_date < :end)
          AND (:type IS NULL OR transaction_type = :type)
          AND (:categoryId IS NULL OR category_id = :categoryId
               OR category_id IN (SELECT id FROM categories WHERE parent_id = :categoryId))
          AND (:accountId IS NULL OR source_account_id = :accountId OR destination_account_id = :accountId)
          AND (:minAmount IS NULL OR ABS(amount) >= :minAmount)
          AND (:maxAmount IS NULL OR ABS(amount) <= :maxAmount)
          AND (:query IS NULL OR description LIKE '%' || :query || '%'
               OR merchant LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%'
               OR tags LIKE '%' || :query || '%')
        ORDER BY transaction_date DESC, id DESC
        """
    )
    fun observe(
        start: Long?, end: Long?, type: String?, categoryId: Long?, accountId: Long?,
        minAmount: Long?, maxAmount: Long?, query: String?,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'CONFIRMED'")
    fun observeAllConfirmed(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Insert suspend fun insert(t: TransactionEntity): Long
    @Update suspend fun update(t: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE source_account_id = :accountId OR destination_account_id = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    @Query("SELECT id, reference_number FROM transactions WHERE reference_number IS NOT NULL")
    suspend fun references(): List<RefRow>

    @Insert suspend fun insertAll(l: List<TransactionEntity>)

    @Query("SELECT COUNT(*) FROM transactions WHERE created_at >= :since OR transaction_date >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    @Query("SELECT source_account_id FROM transactions WHERE transaction_type = :type ORDER BY created_at DESC LIMIT 1")
    suspend fun lastUsedAccount(type: String): Long?

    @Query("SELECT category_id FROM transactions WHERE transaction_type = :type AND category_id IS NOT NULL GROUP BY category_id ORDER BY MAX(created_at) DESC")
    suspend fun recentCategories(type: String): List<Long>
}

@Dao
interface SnapshotDao {
    @Insert suspend fun insert(s: InvestmentSnapshotEntity): Long

    @Query("SELECT * FROM investment_snapshots ORDER BY snapshot_date, id")
    fun observeAll(): Flow<List<InvestmentSnapshotEntity>>

    @Query("SELECT * FROM investment_snapshots WHERE account_id = :accountId ORDER BY snapshot_date DESC, id DESC")
    fun observeHistory(accountId: Long): Flow<List<InvestmentSnapshotEntity>>

    @Query("DELETE FROM investment_snapshots WHERE account_id = :accountId")
    suspend fun deleteForAccount(accountId: Long)

    @Query("SELECT * FROM investment_snapshots WHERE account_id = :accountId AND (notes IS NULL OR notes != 'AUTO') ORDER BY snapshot_date DESC, id DESC LIMIT 1")
    suspend fun lastManual(accountId: Long): InvestmentSnapshotEntity?

    @Query("DELETE FROM investment_snapshots WHERE account_id = :accountId AND notes = 'AUTO' AND snapshot_date >= :from AND snapshot_date < :to")
    suspend fun deleteAuto(accountId: Long, from: Long, to: Long)
}

@Dao
interface InboxDao {
    @Insert suspend fun insert(i: InboxItemEntity): Long
    @Update suspend fun update(i: InboxItemEntity)
    @Query("SELECT * FROM inbox_items WHERE id = :id") suspend fun get(id: Long): InboxItemEntity?
    @Query("SELECT * FROM inbox_items WHERE status = 'WAITING' ORDER BY tx_date DESC, id DESC")
    fun observeWaiting(): Flow<List<InboxItemEntity>>
    @Query("SELECT * FROM inbox_items WHERE status = 'WAITING' ORDER BY tx_date DESC, id DESC")
    suspend fun waiting(): List<InboxItemEntity>
    @Query("SELECT * FROM inbox_items WHERE status = 'UNREAD' ORDER BY created_at DESC")
    fun observeUnread(): Flow<List<InboxItemEntity>>
    @Query("SELECT MAX(created_at) FROM inbox_items") fun observeLastCapture(): Flow<Long?>
    @Query("SELECT COUNT(*) FROM inbox_items WHERE text_hash = :hash AND created_at >= :since")
    suspend fun countSince(hash: String, since: Long): Int
    @Query(
        "SELECT COUNT(*) FROM inbox_items WHERE bank_app = :app AND tx_type = :type AND amount = :amount " +
            "AND status IN ('WAITING','SAVED') AND created_at >= :since"
    )
    suspend fun countSame(app: String, type: String, amount: Long, since: Long): Int
    @Query("DELETE FROM inbox_items WHERE status = 'UNREAD' AND id NOT IN (SELECT id FROM inbox_items WHERE status = 'UNREAD' ORDER BY created_at DESC LIMIT :keep)")
    suspend fun trimUnread(keep: Int)
    @Query("DELETE FROM inbox_items WHERE status != 'WAITING' AND created_at < :before") suspend fun deleteOld(before: Long)
    @Query("UPDATE inbox_items SET account_id = NULL WHERE account_id = :accountId AND status = 'WAITING'")
    suspend fun detachAccount(accountId: Long)
    @Query("DELETE FROM inbox_items") suspend fun clear()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY period_start DESC") fun observeAll(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets ORDER BY period_start DESC") suspend fun all(): List<BudgetEntity>
    @Upsert suspend fun upsert(b: BudgetEntity): Long
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM budgets") suspend fun clear()
    @Insert suspend fun insertAll(l: List<BudgetEntity>)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY deadline, id") fun observeAll(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM goals ORDER BY id") suspend fun all(): List<GoalEntity>
    @Upsert suspend fun upsert(g: GoalEntity): Long
    @Query("DELETE FROM goals WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM goals") suspend fun clear()
    @Insert suspend fun insertAll(l: List<GoalEntity>)
    @Query("UPDATE goals SET account_id = NULL WHERE account_id = :accountId") suspend fun detachAccount(accountId: Long)
}
