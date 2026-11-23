package id.dompetku.capture

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import id.dompetku.DompetkuApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Receives every notification Android shows and keeps only those from the bank apps in [BankApp].
 * Other apps' notifications are dropped on the first line without reading their content.
 */
class BankNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = BankApp.of(sbn.packageName, null) ?: labelOf(sbn.packageName)?.let { BankApp.of(sbn.packageName, it) } ?: return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
            ?: return
        if (text.isBlank()) return
        val posted = Instant.ofEpochMilli(sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis())
        scope.launch {
            val c = (applicationContext as DompetkuApp).openContainer().getOrNull() ?: return@launch
            val raw = listOfNotNull(title, text).joinToString(" — ")
            runCatching { c.inbox.capture(app, raw, BankNotificationParser.parse(title, text, posted)) }
        }
    }

    /** Label check only for unknown packages whose name hints at a bank (keeps other apps unread). */
    private fun labelOf(pkg: String): String? {
        if (!pkg.contains("bank") && !pkg.contains("bni") && !pkg.contains("bmri") && !pkg.contains("sea")) return null
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, BankNotificationListener::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Asks Android to reconnect the listener (after a Xiaomi battery kill, the service can stay unbound). */
        fun rebind(context: Context) {
            if (!isEnabled(context)) return
            runCatching { requestRebind(ComponentName(context, BankNotificationListener::class.java)) }
        }
    }
}
