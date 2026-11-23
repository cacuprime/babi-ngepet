package id.dompetku.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.Snapshot
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import java.time.Instant

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "account_name") val name: String,
    @ColumnInfo(name = "account_type") val type: String,
    @ColumnInfo(name = "provider_name") val provider: String?,
    @ColumnInfo(name = "opening_balance") val openingBalance: Long,
    val currency: String = "IDR",
    @ColumnInfo(name = "is_liquid") val isLiquid: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "category_name") val name: String,
    @ColumnInfo(name = "category_type") val type: String,
    val icon: String,
    val color: Long,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    /** Budget bucket (NEEDS/WANTS/SAVINGS); null = WANTS. Subcategories use their parent's. */
    val bucket: String? = null,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("transaction_date"), Index("source_account_id"),
        Index("destination_account_id"), Index("category_id"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "transaction_type") val type: String,
    val amount: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "source_account_id") val sourceAccountId: Long,
    @ColumnInfo(name = "destination_account_id") val destinationAccountId: Long?,
    @ColumnInfo(name = "gold_ten_thousandths") val goldTenThousandths: Long?,
    val description: String?,
    val merchant: String?,
    @ColumnInfo(name = "transaction_date") val date: Long,
    @ColumnInfo(name = "reference_number") val referenceNumber: String? = null,
    val notes: String?,
    @ColumnInfo(name = "attachment_id") val attachmentId: Long? = null,
    @ColumnInfo(name = "input_source") val inputSource: String = "MANUAL",
    val status: String = "CONFIRMED",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Space-separated lowercase tags without "#", e.g. "liburan bali". */
    val tags: String? = null,
)

@Entity(tableName = "investment_snapshots", indices = [Index("account_id")])
data class InvestmentSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "total_contribution") val totalContribution: Long?,
    @ColumnInfo(name = "current_value") val currentValue: Long,
    @ColumnInfo(name = "gold_ten_thousandths") val goldTenThousandths: Long?,
    @ColumnInfo(name = "gold_price_per_gram") val goldPricePerGram: Long?,
    @ColumnInfo(name = "snapshot_date") val date: Long,
    val notes: String?,
)

/** A display-only set of accounts whose balances are summed (e.g. "Dana harian"). */
@Entity(tableName = "account_groups")
data class AccountGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "group_name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "account_group_members",
    primaryKeys = ["group_id", "account_id"],
    indices = [Index("account_id")],
)
data class AccountGroupMemberEntity(
    @ColumnInfo(name = "group_id") val groupId: Long,
    @ColumnInfo(name = "account_id") val accountId: Long,
)

/** A receipt photo, screenshot or statement file kept as proof (private app storage). */
@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_type") val fileType: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** PRD §7.13: keyword/merchant → category. BUILTIN rules ship with the app; LEARNED come from the owner's corrections. */
@Entity(tableName = "categorization_rules", indices = [Index(value = ["keyword_or_merchant"], unique = true)])
data class CategorizationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "keyword_or_merchant") val keyword: String,
    @ColumnInfo(name = "suggested_category_id") val suggestedCategoryId: Long,
    @ColumnInfo(name = "account_id_optional") val accountId: Long?,
    val confidence: Float,
    val source: String,
    @ColumnInfo(name = "usage_count") val usageCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * A position inside an investment account: stock shares (code "BBCA") or fund units (Pasardana fund Id).
 * unitsE4 / priceE4 are ×10 000. updatedAt = when the owner last edited units (prices don't change it).
 */
@Entity(tableName = "holdings", indices = [Index("account_id")])
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "account_id") val accountId: Long,
    val kind: String,
    val code: String,
    val name: String,
    @ColumnInfo(name = "units_e4") val unitsE4: Long,
    @ColumnInfo(name = "price_e4") val priceE4: Long?,
    @ColumnInfo(name = "price_at") val priceAt: Long?,
    @ColumnInfo(name = "price_error") val priceError: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Total modal paid (Rp), as Bibit shows it; null until the owner enters it. */
    @ColumnInfo(name = "cost_rp") val costRp: Long? = null,
)

fun AccountEntity.toDomain() = Account(
    id, name, AccountType.valueOf(type), provider, openingBalance, isLiquid, isArchived, Instant.ofEpochMilli(createdAt),
)

fun Account.toEntity(now: Long) = AccountEntity(
    id, name, type.name, provider, openingBalance, "IDR", isLiquid, isArchived, createdAt.toEpochMilli(), now,
)

fun TransactionEntity.toDomain() = Tx(
    id, TxType.valueOf(type), amount, categoryId, sourceAccountId, destinationAccountId, goldTenThousandths,
    Instant.ofEpochMilli(date), description, merchant, notes, tags,
)

fun Tx.toEntity(createdAt: Long, now: Long) = TransactionEntity(
    id = id, type = type.name, amount = amount, categoryId = categoryId, sourceAccountId = sourceAccountId,
    destinationAccountId = destinationAccountId, goldTenThousandths = goldTenThousandths,
    description = description, merchant = merchant, date = date.toEpochMilli(), notes = notes,
    createdAt = createdAt, updatedAt = now, tags = tags,
)

fun InvestmentSnapshotEntity.toDomain() =
    Snapshot(accountId, Instant.ofEpochMilli(date), currentValue, goldTenThousandths, goldPricePerGram)

/**
 * A transaction read from a bank-app notification. WAITING items wait for review and never
 * affect balances; SAVED points at the created transaction; UNREAD = had an amount but couldn't be read.
 */
@Entity(tableName = "inbox_items", indices = [Index("status"), Index("text_hash")])
data class InboxItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bank_app") val bankApp: String,
    @ColumnInfo(name = "raw_text") val rawText: String,
    @ColumnInfo(name = "text_hash") val textHash: String,
    @ColumnInfo(name = "tx_type") val txType: String?,
    val amount: Long?,
    val merchant: String?,
    val description: String?,
    @ColumnInfo(name = "tx_date") val txDate: Long,
    @ColumnInfo(name = "account_id") val accountId: Long?,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "duplicate_of") val duplicateOf: Long?,
    val status: String,
    @ColumnInfo(name = "saved_tx_id") val savedTxId: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * One budget period. Limits are stored in Rp even in percentage mode (percent × income at save time),
 * so a later income change never silently moves a saved limit. startDay null = custom range.
 */
@Entity(tableName = "budgets", indices = [Index("period_start")])
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "period_start") val start: Long,
    @ColumnInfo(name = "period_end") val endExclusive: Long,
    @ColumnInfo(name = "start_day") val startDay: Int?,
    val mode: String,
    val income: Long,
    @ColumnInfo(name = "needs_pct") val needsPct: Int,
    @ColumnInfo(name = "wants_pct") val wantsPct: Int,
    @ColumnInfo(name = "savings_pct") val savingsPct: Int,
    val needs: Long,
    val wants: Long,
    val savings: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A savings goal. Progress = linked account's value when accountId is set, otherwise [saved]. */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "goal_name") val name: String,
    val target: Long,
    val deadline: Long,
    @ColumnInfo(name = "account_id") val accountId: Long?,
    val saved: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
