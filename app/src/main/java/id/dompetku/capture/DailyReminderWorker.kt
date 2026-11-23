package id.dompetku.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.dompetku.DompetkuApp
import id.dompetku.MainActivity
import id.dompetku.R
import id.dompetku.core.I18n
import id.dompetku.core.t
import id.dompetku.domain.JAKARTA
import id.dompetku.domain.Period
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Daily 20:00 WIB: "Sudah catat hari ini?" — only when nothing was recorded today. */
class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as DompetkuApp).openContainer().getOrNull() ?: return Result.success()
        I18n.english = c.settings.english.first()
        val since = Period.today(Instant.now()).start.toEpochMilli()
        if (c.db.transactions().countSince(since) == 0) notify(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-reminder"
        private const val CHANNEL = "daily"
        private const val NOTIF_ID = 2102

        fun schedule(context: Context, on: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!on) { wm.cancelUniqueWork(NAME); return }
            val now = ZonedDateTime.now(JAKARTA)
            var next = now.with(LocalTime.of(20, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, t("Pengingat harian"), NotificationManager.IMPORTANCE_DEFAULT))
            }
            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif_wallet)
                .setContentTitle(t("Sudah catat pengeluaran hari ini?"))
                .setContentText(t("Belum ada transaksi hari ini. Ketuk untuk mencatat."))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, n) }
        }
    }
}
