package id.dompetku.ui.lock

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {
    @Test fun authenticatorsSupportedOnAllApiLevels() {
        // BIOMETRIC_STRONG | DEVICE_CREDENTIAL is unsupported on Android 9-10.
        assertEquals(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, LOCK_AUTHENTICATORS)
    }

    @Test fun relocksOnlyAfterSixtySecondsWhenEnabled() {
        assertFalse(LockPolicy.shouldRelock(lockOn = false, stoppedAt = 1_000, now = 999_000))
        assertFalse(LockPolicy.shouldRelock(lockOn = true, stoppedAt = 0, now = 999_000))
        assertFalse(LockPolicy.shouldRelock(lockOn = true, stoppedAt = 1_000, now = 50_000))
        assertTrue(LockPolicy.shouldRelock(lockOn = true, stoppedAt = 1_000, now = 62_000))
    }
}
