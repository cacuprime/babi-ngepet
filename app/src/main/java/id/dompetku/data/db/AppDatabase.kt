package id.dompetku.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TransactionEntity::class, InvestmentSnapshotEntity::class,
        AccountGroupEntity::class, AccountGroupMemberEntity::class, AttachmentEntity::class, CategorizationRuleEntity::class, HoldingEntity::class,
        InboxItemEntity::class, BudgetEntity::class, GoalEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun snapshots(): SnapshotDao
    abstract fun backup(): BackupDao
    abstract fun groups(): GroupDao
    abstract fun attachments(): AttachmentDao
    abstract fun rules(): RuleDao
    abstract fun holdings(): HoldingDao
    abstract fun inbox(): InboxDao
    abstract fun budgets(): BudgetDao
    abstract fun goals(): GoalDao

    companion object {
        const val NAME = "dompetku.db"

        /** 1.0 → 1.1: adds account groups. Existing tables are untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `account_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`group_name` TEXT NOT NULL, `created_at` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `account_group_members` (`group_id` INTEGER NOT NULL, " +
                        "`account_id` INTEGER NOT NULL, PRIMARY KEY(`group_id`, `account_id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_account_group_members_account_id` ON `account_group_members` (`account_id`)"
                )
            }
        }

        /** 1.1 → 1.2: adds attachments and categorisation rules (seeded with built-ins). Existing tables untouched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`file_name` TEXT NOT NULL, `file_path` TEXT NOT NULL, `file_type` TEXT NOT NULL, " +
                        "`file_size` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categorization_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`keyword_or_merchant` TEXT NOT NULL, `suggested_category_id` INTEGER NOT NULL, " +
                        "`account_id_optional` INTEGER, `confidence` REAL NOT NULL, `source` TEXT NOT NULL, " +
                        "`usage_count` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_categorization_rules_keyword_or_merchant` " +
                        "ON `categorization_rules` (`keyword_or_merchant`)"
                )
                DefaultRules.seed(db)
            }
        }

        /** 1.2 → 1.3: adds holdings for daily prices. Existing tables untouched. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `holdings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`account_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`units_e4` INTEGER NOT NULL, `price_e4` INTEGER, `price_at` INTEGER, `price_error` TEXT, " +
                        "`updated_at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_holdings_account_id` ON `holdings` (`account_id`)")
            }
        }

        /** 1.3 → 1.4: modal per holding. Adds one nullable column; nothing else changes. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `holdings` ADD COLUMN `cost_rp` INTEGER")
            }
        }

        /** 1.4 → 1.5: inbox for bank-notification captures. Existing tables untouched. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `inbox_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bank_app` TEXT NOT NULL, `raw_text` TEXT NOT NULL, `text_hash` TEXT NOT NULL, `tx_type` TEXT, " +
                        "`amount` INTEGER, `merchant` TEXT, `description` TEXT, `tx_date` INTEGER NOT NULL, `account_id` INTEGER, " +
                        "`category_id` INTEGER, `duplicate_of` INTEGER, `status` TEXT NOT NULL, `saved_tx_id` INTEGER, " +
                        "`created_at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inbox_items_status` ON `inbox_items` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inbox_items_text_hash` ON `inbox_items` (`text_hash`)")
            }
        }

        /** 1.5 → 1.6: budgets, goals, subcategories, buckets, tags. Only adds; sets default buckets by category name. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `bucket` TEXT")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `parent_id` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `tags` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`period_start` INTEGER NOT NULL, `period_end` INTEGER NOT NULL, `start_day` INTEGER, `mode` TEXT NOT NULL, " +
                        "`income` INTEGER NOT NULL, `needs_pct` INTEGER NOT NULL, `wants_pct` INTEGER NOT NULL, " +
                        "`savings_pct` INTEGER NOT NULL, `needs` INTEGER NOT NULL, `wants` INTEGER NOT NULL, " +
                        "`savings` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_period_start` ON `budgets` (`period_start`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`goal_name` TEXT NOT NULL, `target` INTEGER NOT NULL, `deadline` INTEGER NOT NULL, " +
                        "`account_id` INTEGER, `saved` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)"
                )
                DefaultCategories.setBuckets(db)
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        private val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                DefaultCategories.seed(db)
                DefaultRules.seed(db)
            }
        }

        /** key == null builds a plain in-memory database (tests only). */
        fun build(context: Context, key: ByteArray?): AppDatabase {
            if (key == null) {
                return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .addCallback(seedCallback)
                    .build()
            }
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addMigrations(*MIGRATIONS)
                .addCallback(seedCallback)
                .build()
        }
    }
}
