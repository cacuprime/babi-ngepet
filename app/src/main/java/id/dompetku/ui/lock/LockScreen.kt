package id.dompetku.ui.lock

import id.dompetku.core.t

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import id.dompetku.ui.components.CircleIcon
import id.dompetku.ui.components.VSpace
import id.dompetku.ui.theme.LocalDompetkuColors

/** WEAK | DEVICE_CREDENTIAL is the only combination supported on every API level from 26 up. */
const val LOCK_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

object LockPolicy {
    const val RELOCK_AFTER_MS = 60_000L

    fun shouldRelock(lockOn: Boolean, stoppedAt: Long, now: Long): Boolean =
        lockOn && stoppedAt != 0L && now - stoppedAt > RELOCK_AFTER_MS
}

fun canUseAppLock(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

fun promptUnlock(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(t("Buka Babi Ngepet"))
            .setSubtitle(t("Gunakan sidik jari, wajah, atau PIN perangkat"))
            .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
            .build()
    )
}

@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    val c = LocalDompetkuColors.current
    LaunchedEffect(Unit) {
        if (canUseAppLock(activity)) promptUnlock(activity, onUnlocked) else onUnlocked()
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircleIcon(Icons.Outlined.Lock, c.accent, c.onHero, size = 64)
        VSpace(20)
        Text(t("Babi Ngepet terkunci"), style = MaterialTheme.typography.titleLarge, color = c.text)
        VSpace(24)
        Button(onClick = { promptUnlock(activity, onUnlocked) }) { Text(t("Buka kunci")) }
    }
}
