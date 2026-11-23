package id.dompetku

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import id.dompetku.ui.AppNav
import id.dompetku.ui.LocalContainer
import id.dompetku.ui.lock.LockPolicy
import id.dompetku.ui.lock.LockScreen
import id.dompetku.ui.onboarding.OnboardingScreen
import id.dompetku.ui.recovery.OpenFailedScreen
import id.dompetku.ui.recovery.RecoveryScreen
import id.dompetku.ui.theme.DompetkuTheme
import id.dompetku.ui.theme.LocalDompetkuColors
import id.dompetku.ui.theme.ThemeMode
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private data class Gate(val onboarded: Boolean, val lockOn: Boolean)

class MainActivity : AppCompatActivity() {
    /** Survives rotation and theme changes (saved instance state). */
    private val locked = mutableStateOf(true)
    private val container = mutableStateOf<Result<AppContainer>?>(null)
    private val openInbox = mutableStateOf(false)
    private var stoppedAt = 0L
    private var lockOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked.value = savedInstanceState?.getBoolean(KEY_LOCKED, true) ?: true
        stoppedAt = savedInstanceState?.getLong(KEY_STOPPED, 0L) ?: 0L
        if (savedInstanceState == null) openInbox.value = intent?.getBooleanExtra(id.dompetku.capture.InboxReminderWorker.EXTRA_OPEN_INBOX, false) == true
        val app = application as DompetkuApp
        container.value = app.openContainer()

        setContent {
            when (val r = container.value) {
                null -> Unit
                else -> r.fold(
                    onSuccess = { c -> AppRoot(c) },
                    onFailure = { e ->
                        DompetkuTheme(ThemeMode.SYSTEM) {
                            if (e is StorageError.KeyUnavailable) {
                                RecoveryScreen(onStartFresh = { container.value = app.resetAndOpen() })
                            } else {
                                OpenFailedScreen(onRetry = { container.value = app.openContainer() })
                            }
                        }
                    },
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppRoot(c: AppContainer) {
        val theme by c.settings.themeMode.collectAsState(initial = null)
        val gateFlow = remember(c) { combine(c.settings.onboarded, c.settings.appLock) { o, l -> Gate(o == true, l) } }
        val gate by gateFlow.collectAsState(initial = null)
        val g = gate
        val autoPrices by remember(c) { c.settings.autoPrices.map<Boolean, Boolean?> { it } }.collectAsState(initial = null)
        // Wait for the stored setting: scheduling with a placeholder would cancel and re-delay the daily job.
        LaunchedEffect(autoPrices) { autoPrices?.let { id.dompetku.prices.PriceWorker.schedule(applicationContext, it) } }
        val reminder by remember(c) { c.settings.captureReminder.map<Boolean, Boolean?> { it } }.collectAsState(initial = null)
        LaunchedEffect(reminder) { reminder?.let { id.dompetku.capture.InboxReminderWorker.schedule(applicationContext, it) } }
        val daily by remember(c) { c.settings.dailyReminder.map<Boolean, Boolean?> { it } }.collectAsState(initial = null)
        LaunchedEffect(daily) { daily?.let { id.dompetku.capture.DailyReminderWorker.schedule(applicationContext, it) } }
        val english by remember(c) { c.settings.english.map<Boolean, Boolean?> { it } }.collectAsState(initial = null)
        LaunchedEffect(english) { english?.let { id.dompetku.core.I18n.english = it } }
        LaunchedEffect(g?.lockOn) {
            lockOn = g?.lockOn == true
            // Hide balances from the recents screen and screenshots while app lock is on.
            if (lockOn) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        CompositionLocalProvider(LocalContainer provides c) {
            DompetkuTheme(theme ?: ThemeMode.SYSTEM) {
                Box(Modifier.fillMaxSize().background(LocalDompetkuColors.current.background)) {
                    if (theme == null || g == null || english == null) return@Box
                    if (!g.onboarded) { OnboardingScreen(); return@Box }
                    // AppNav stays composed under the lock so screens, drafts and pending
                    // activity results (backup file picker) survive a re-lock.
                    AppNav(openInbox.value) { openInbox.value = false }
                    if (g.lockOn && locked.value) {
                        Box(
                            Modifier.fillMaxSize().background(LocalDompetkuColors.current.background)
                                .clickable(remember { MutableInteractionSource() }, indication = null) {},
                        ) { LockScreen(this@MainActivity) { locked.value = false } }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked.value)
        outState.putLong(KEY_STOPPED, stoppedAt)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(id.dompetku.capture.InboxReminderWorker.EXTRA_OPEN_INBOX, false)) openInbox.value = true
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (LockPolicy.shouldRelock(lockOn, stoppedAt, SystemClock.elapsedRealtime())) locked.value = true
        stoppedAt = 0L
    }

    private companion object {
        const val KEY_LOCKED = "locked"
        const val KEY_STOPPED = "stopped_at"
    }
}
