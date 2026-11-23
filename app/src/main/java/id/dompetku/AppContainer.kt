package id.dompetku

import android.content.Context
import id.dompetku.data.AccountRepository
import id.dompetku.data.CategoryRepository
import id.dompetku.data.DashboardRepository
import id.dompetku.data.GroupRepository
import id.dompetku.data.SnapshotRepository
import id.dompetku.data.TransactionRepository
import androidx.room.withTransaction
import id.dompetku.data.SettingsStore
import id.dompetku.data.db.AppDatabase
import id.dompetku.data.db.DbKey
import id.dompetku.data.db.DefaultCategories
import id.dompetku.data.db.DefaultRules
import kotlinx.coroutines.flow.first

/** Why the database could not be opened. Only [KeyUnavailable] may offer a fresh start. */
sealed class StorageError(cause: Throwable) : Exception(cause) {
    class KeyUnavailable(cause: Throwable) : StorageError(cause)
    class OpenFailed(cause: Throwable) : StorageError(cause)
}

/** Manual dependency container: one instance per process. */
class AppContainer(val context: Context, val db: AppDatabase) {
    val accounts = AccountRepository(db)
    val transactions = TransactionRepository(db)
    val snapshots = SnapshotRepository(db)
    val categories = CategoryRepository(db)
    val groups = GroupRepository(db)
    val dashboard = DashboardRepository(accounts, transactions, snapshots, groups)
    val settings = SettingsStore(context)
    val imports = id.dompetku.data.ImportRepository(db)
    val rules = id.dompetku.data.RuleRepository(db)
    val attachments = id.dompetku.data.AttachmentStore(context, db)
    val holdings = id.dompetku.data.HoldingRepository(db)
    val budgets = id.dompetku.data.BudgetRepository(db)
    val goals = id.dompetku.data.GoalRepository(db)
    val inbox = id.dompetku.data.InboxRepository(
        db, rules,
        accountFor = { app -> settings.captureAccounts.first()[app.name] },
        autoSave = { settings.captureAutoSave.first() },
    )

    /** Erase every record and restore the default categories. */
    suspend fun wipeAll() {
        db.withTransaction {
            val b = db.backup()
            b.clearTransactions(); b.clearSnapshots(); b.clearGroupMembers(); b.clearGroups()
            b.clearAccounts(); b.clearCategories()
            db.rules().clear(); db.attachments().clear(); db.holdings().clear(); db.inbox().clear()
            db.budgets().clear(); db.goals().clear()
        }
        DefaultCategories.seed(db.openHelper.writableDatabase)
        DefaultRules.seed(db.openHelper.writableDatabase)
    }

    companion object {
        /**
         * Opens the encrypted database, forcing the open so a wrong or missing key
         * (device transfer, Keystore reset) surfaces here as a failure instead of
         * crashing later on every launch.
         */
        fun open(
            context: Context,
            key: (Context) -> ByteArray = DbKey::getOrCreate,
            build: (Context, ByteArray) -> AppDatabase = { c, k -> AppDatabase.build(c, k) },
        ): Result<AppContainer> {
            val raw = try {
                key(context)
            } catch (e: Throwable) {
                return Result.failure(StorageError.KeyUnavailable(e))
            }
            var db: AppDatabase? = null
            return try {
                db = build(context, raw)
                db.openHelper.writableDatabase
                Result.success(AppContainer(context, db))
            } catch (e: Throwable) {
                runCatching { db?.close() }
                // Only "wrong key" may lead to the fresh-start screen. Anything else (e.g. a failed
                // upgrade) keeps the data file untouched so nothing is ever wiped by mistake.
                Result.failure(if (isWrongKey(e)) StorageError.KeyUnavailable(e) else StorageError.OpenFailed(e))
            }
        }

        private fun isWrongKey(e: Throwable): Boolean =
            generateSequence(e) { it.cause }.any { it.message?.contains("file is not a database", ignoreCase = true) == true }

        /** Deletes the database and its key so the app can start fresh (then restore from a backup file). */
        fun resetStorage(context: Context) {
            context.deleteDatabase(AppDatabase.NAME)
            context.getSharedPreferences("dbkey", Context.MODE_PRIVATE).edit().clear().commit()
            DbKey.deleteWrapKey()
        }

        fun inMemory(context: Context) = AppContainer(context, AppDatabase.build(context, null))
    }
}
