package id.dompetku.capture

import id.dompetku.core.t

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
import id.dompetku.domain.JAKARTA
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Daily 21:00 WIB: "Ada N transaksi perlu dicek" when the inbox isn't empty. Also tidies old inbox rows. */
class InboxReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as DompetkuApp).openContainer().getOrNull() ?: return Result.success()
        runCatching { c.inbox.cleanup() }
        BankNotificationListener.rebind(applicationContext)
        val n = c.inbox.waitingCount()
        if (n > 0) notify(applicationContext, n)
        return Result.success()
    }

    companion object {
        private const val NAME = "inbox-reminder"
        private const val CHANNEL = "inbox"
        private const val NOTIF_ID = 2101
        const val EXTRA_OPEN_INBOX = "open_inbox"

        fun schedule(context: Context, on: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!on) { wm.cancelUniqueWork(NAME); return }
            val now = ZonedDateTime.now(JAKARTA)
            var next = now.with(LocalTime.of(21, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<InboxReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun notify(context: Context, count: Int) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, t("Transaksi perlu dicek"), NotificationManager.IMPORTANCE_DEFAULT))
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_INBOX, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif_wallet)
                .setContentTitle(id.dompetku.core.t("Ada %d transaksi perlu dicek", count))
                .setContentText(t("Ketuk untuk cek dan simpan sekaligus."))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, n) }
        }
    }
}
