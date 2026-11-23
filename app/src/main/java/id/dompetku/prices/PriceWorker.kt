package id.dompetku.prices

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.dompetku.DompetkuApp
import id.dompetku.data.db.AppDatabase
import id.dompetku.domain.JAKARTA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Runs the price update once a day after the IDX close (17:30 WIB), whenever a network is available. */
class PriceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as DompetkuApp).openContainer().getOrNull() ?: return Result.success()
        PriceUpdater(c, UrlHttp()).run()
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-prices"

        fun schedule(context: Context, on: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!on) { wm.cancelUniqueWork(NAME); return }
            val now = ZonedDateTime.now(JAKARTA)
            var next = now.with(LocalTime.of(17, 30))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<PriceWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Fund list for the search box: downloaded on demand, cached for 7 days in private storage. */
object FundCatalog {
    suspend fun load(context: Context, http: Http): kotlin.Result<List<FundRef>> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "funds.json")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 7L * 24 * 3600 * 1000
        runCatching {
            if (!fresh) {
                val json = http.get(PriceUpdater.PASARDANA)
                val list = PriceParsers.fundIndex(json)
                if (list.isEmpty()) error("Daftar reksa dana tidak tersedia")
                file.writeText(JSONArray(list.map { org.json.JSONObject().put("Id", it.id).put("Name", it.name).put("NetAssetValue", 1) }).toString())
            }
            PriceParsers.fundIndex(file.readText())
        }
    }

    @Suppress("unused") private val keep = AppDatabase.NAME
}
