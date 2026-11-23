package id.dompetku.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import id.dompetku.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /** Builds a real database at an old [version] from its exported schema, as an installed older app would have. */
    private fun createV(version: Int, name: String) {
        val schema = JSONObject(File("schemas/id.dompetku.data.db.AppDatabase/$version.json").readText()).getJSONObject("database")
        val f = ctx.getDatabasePath(name); f.parentFile?.mkdirs(); f.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(f, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val idx = e.optJSONArray("indices")
            if (idx != null) for (j in 0 until idx.length()) {
                db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.execSQL("INSERT INTO categories (id, category_name, category_type, icon, color, is_default) VALUES (1,'Makan','EXPENSE','restaurant',0,1)")
        db.execSQL("INSERT INTO accounts (id, account_name, account_type, provider_name, opening_balance, currency, is_liquid, is_archived, created_at, updated_at) VALUES (1,'BSI','BANK','BSI',10000000,'IDR',1,0,0,0)")
        db.execSQL("INSERT INTO transactions (id, transaction_type, amount, category_id, source_account_id, transaction_date, input_source, status, created_at, updated_at) VALUES (1,'EXPENSE',35000,1,1,0,'MANUAL','CONFIRMED',0,0)")
        db.execSQL("INSERT INTO investment_snapshots (id, account_id, current_value, snapshot_date) VALUES (1,1,5,0)")
        db.version = version
        db.close()
    }

    @Test fun migrate6To7KeepsDataAndSetsBuckets() = runBlocking {
        createV(6, "mig7.db")
        SQLiteDatabase.openDatabase(ctx.getDatabasePath("mig7.db").path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("INSERT INTO categories (id, category_name, category_type, icon, color, is_default) VALUES (2,'Transportasi','EXPENSE','x',0,1)")
            it.execSQL("INSERT INTO categories (id, category_name, category_type, icon, color, is_default) VALUES (3,'Investasi','EXPENSE','x',0,1)")
        }
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "mig7.db")
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        assertEquals(1, db.backup().transactions().size)
        assertEquals(null, db.backup().transactions().single().tags)
        val cats = db.backup().categories().associateBy { it.id }
        assertEquals(null, cats.getValue(1).bucket) // "Makan" is not a default name → stays WANTS
        assertEquals("NEEDS", cats.getValue(2).bucket)
        assertEquals("SAVINGS", cats.getValue(3).bucket)
        assertEquals(0, db.budgets().all().size)
        assertEquals(0, db.goals().all().size)
        db.close()
    }

    @Test fun migrate5To6AddsInbox() = runBlocking {
        createV(5, "mig6.db")
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "mig6.db")
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        assertEquals(1, db.backup().accounts().size)
        assertEquals(1, db.backup().transactions().size)
        assertEquals(0, db.inbox().waiting().size)
        db.close()
    }

    @Test fun migrate4To5KeepsHoldings() = runBlocking {
        createV(4, "mig5.db")
        SQLiteDatabase.openDatabase(ctx.getDatabasePath("mig5.db").path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("INSERT INTO holdings (id, account_id, kind, code, name, units_e4, price_e4, price_at, price_error, updated_at) VALUES (1,1,'STOCK','BBCA','BBCA',3000000,55000000,1,NULL,1)")
        }
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "mig5.db")
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        val h = db.holdings().all().single()
        assertEquals("BBCA", h.code); assertEquals(3_000_000L, h.unitsE4); assertEquals(null, h.costRp)
        assertEquals(1, db.backup().accounts().size)
        db.close()
    }

    @Test fun migrate3To4KeepsData() = runBlocking {
        createV(3, "mig4.db")
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "mig4.db")
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        assertEquals(1, db.backup().accounts().size)
        assertEquals(1, db.backup().transactions().size)
        assertEquals(0, db.holdings().all().size)
        db.close()
    }

    @Test fun migrate2To3KeepsDataAndSeedsRules() = runBlocking {
        createV(2, "mig3.db")
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "mig3.db")
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        val b = db.backup()
        assertEquals(1, b.accounts().size)
        assertEquals(1, b.transactions().size)
        // Category "Makan" isn't a default name, so no built-in rule can point at it; the table must exist and be queryable.
        assertEquals(0, db.rules().all().count { it.suggestedCategoryId != 1L && it.source == "LEARNED" })
        assertEquals(0, db.attachments().count())
        db.close()
    }

    @Test fun migrate1To2KeepsData() = runBlocking {
        createV(1, "mig.db")
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "mig.db")
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        val b = db.backup()
        assertEquals(1, b.accounts().size)
        assertEquals(1, b.transactions().size)
        assertEquals(1, b.snapshots().size)
        assertEquals(10_000_000L, b.accounts().single().openingBalance)
        assertEquals(0, db.groups().all().size)
        db.close()
    }
}
