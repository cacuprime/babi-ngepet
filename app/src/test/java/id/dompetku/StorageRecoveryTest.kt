package id.dompetku

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StorageRecoveryTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun openFailsInsteadOfCrashingWhenKeyUnavailable() {
        val r = AppContainer.open(ctx, key = { throw javax.crypto.AEADBadTagException("key mismatch") })
        assertTrue(r.isFailure)
    }

    @Test fun keyFailureOffersFreshStart() {
        val e = AppContainer.open(ctx, key = { throw javax.crypto.AEADBadTagException("key mismatch") }).exceptionOrNull()
        assertTrue(e is StorageError.KeyUnavailable)
    }

    @Test fun migrationFailureNeverOffersWipe() {
        val e = AppContainer.open(ctx, { ByteArray(32) }) { _, _ ->
            throw IllegalStateException("Migration didn't properly handle: account_groups")
        }.exceptionOrNull()
        assertTrue(e is StorageError.OpenFailed)
    }

    @Test fun wrongKeyOnOpenIsKeyProblem() {
        val e = AppContainer.open(ctx, { ByteArray(32) }) { _, _ ->
            throw android.database.sqlite.SQLiteException("file is not a database (code 26)")
        }.exceptionOrNull()
        assertTrue(e is StorageError.KeyUnavailable)
    }

    @Test fun resetStorageDeletesDatabaseAndKey() {
        val db = ctx.getDatabasePath("dompetku.db")
        db.parentFile?.mkdirs(); db.writeBytes(byteArrayOf(1, 2, 3))
        ctx.getSharedPreferences("dbkey", Context.MODE_PRIVATE).edit().putString("wrapped", "x").commit()
        AppContainer.resetStorage(ctx)
        assertFalse(db.exists())
        assertFalse(ctx.getSharedPreferences("dbkey", Context.MODE_PRIVATE).contains("wrapped"))
    }
}
