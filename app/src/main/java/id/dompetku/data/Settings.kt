package id.dompetku.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import id.dompetku.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val appLock = booleanPreferencesKey("app_lock")
        val onboarded = booleanPreferencesKey("onboarded")
        val userName = stringPreferencesKey("user_name")
        val hideBalances = booleanPreferencesKey("hide_balances")
        val autoPrices = booleanPreferencesKey("auto_prices")
        val lastPriceRun = androidx.datastore.preferences.core.longPreferencesKey("last_price_run")
    }

    private val homeOrderKey = stringPreferencesKey("home_order")
    private val homeHiddenKey = androidx.datastore.preferences.core.stringSetPreferencesKey("home_hidden")

    /** Order of Home swipe cards ("T", "G:<id>", "A:<id>"); empty = default order. */
    val homeOrder: Flow<List<String>> = context.dataStore.data.map { p -> p[homeOrderKey]?.split(',')?.filter { it.isNotBlank() }.orEmpty() }
    val homeHidden: Flow<Set<String>> = context.dataStore.data.map { it[homeHiddenKey].orEmpty() }
    suspend fun setHomeOrder(keys: List<String>) = context.dataStore.edit { it[homeOrderKey] = keys.joinToString(",") }
    suspend fun setHomeHidden(keys: Set<String>) = context.dataStore.edit { it[homeHiddenKey] = keys }

    /** Daily stock/fund/gold prices from the internet. On by default; off means no network use at all. */
    val autoPrices: Flow<Boolean> = context.dataStore.data.map { it[K.autoPrices] ?: true }
    val lastPriceRun: Flow<Long?> = context.dataStore.data.map { it[K.lastPriceRun] }
    suspend fun setAutoPrices(on: Boolean) = context.dataStore.edit { it[K.autoPrices] = on }
    suspend fun setLastPriceRun(at: Long) = context.dataStore.edit { it[K.lastPriceRun] = at }

    private val captureMapKey = stringPreferencesKey("capture_accounts")
    private val captureAutoKey = booleanPreferencesKey("capture_auto_save")
    private val captureReminderKey = booleanPreferencesKey("capture_reminder")

    /** Bank app name (BankApp.name) → Dompetku account id that its notifications are booked to. */
    val captureAccounts: Flow<Map<String, Long>> = context.dataStore.data.map { p ->
        p[captureMapKey].orEmpty().split(',').mapNotNull { e ->
            val (k, v) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            v.toLongOrNull()?.let { k to it }
        }.toMap()
    }
    suspend fun setCaptureAccount(app: String, accountId: Long?) = context.dataStore.edit { p ->
        val m = p[captureMapKey].orEmpty().split(',').filter { it.contains('=') && !it.startsWith("$app=") }.toMutableList()
        if (accountId != null) m += "$app=$accountId"
        p[captureMapKey] = m.joinToString(",")
    }

    /** Known merchants (confirmed twice) are saved without review. On by default. */
    val captureAutoSave: Flow<Boolean> = context.dataStore.data.map { it[captureAutoKey] ?: true }
    suspend fun setCaptureAutoSave(on: Boolean) = context.dataStore.edit { it[captureAutoKey] = on }

    /** 21:00 reminder when items wait for review. On by default. */
    val captureReminder: Flow<Boolean> = context.dataStore.data.map { it[captureReminderKey] ?: true }
    suspend fun setCaptureReminder(on: Boolean) = context.dataStore.edit { it[captureReminderKey] = on }

    private val englishKey = booleanPreferencesKey("english")
    private val dailyReminderKey = booleanPreferencesKey("daily_reminder")
    private val roastKey = booleanPreferencesKey("roast")
    private val emergencyKey = androidx.datastore.preferences.core.stringSetPreferencesKey("emergency_accounts")

    /** App language: false = Bahasa Indonesia (default), true = English. */
    val english: Flow<Boolean> = context.dataStore.data.map { it[englishKey] ?: false }
    suspend fun setEnglish(on: Boolean) = context.dataStore.edit { it[englishKey] = on }

    /** 20:00 "did you record today?" reminder, only when nothing was recorded. On by default. */
    val dailyReminder: Flow<Boolean> = context.dataStore.data.map { it[dailyReminderKey] ?: true }
    suspend fun setDailyReminder(on: Boolean) = context.dataStore.edit { it[dailyReminderKey] = on }

    /** Teasing tone for the Insights notes. */
    val roast: Flow<Boolean> = context.dataStore.data.map { it[roastKey] ?: false }
    suspend fun setRoast(on: Boolean) = context.dataStore.edit { it[roastKey] = on }

    /** Account ids counted as emergency fund; empty = all savings and deposit accounts. */
    val emergencyAccounts: Flow<Set<Long>> = context.dataStore.data.map { p -> p[emergencyKey].orEmpty().mapNotNull { it.toLongOrNull() }.toSet() }
    suspend fun setEmergencyAccounts(ids: Set<Long>) = context.dataStore.edit { it[emergencyKey] = ids.map { id -> id.toString() }.toSet() }

    val hideBalances: Flow<Boolean> = context.dataStore.data.map { it[K.hideBalances] ?: false }
    suspend fun setHideBalances(on: Boolean) = context.dataStore.edit { it[K.hideBalances] = on }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }
    val appLock: Flow<Boolean> = context.dataStore.data.map { it[K.appLock] ?: false }
    val onboarded: Flow<Boolean?> = context.dataStore.data.map { it[K.onboarded] ?: false }
    val userName: Flow<String> = context.dataStore.data.map { it[K.userName] ?: "" }

    suspend fun setThemeMode(m: ThemeMode) = context.dataStore.edit { it[K.theme] = m.name }
    suspend fun setAppLock(on: Boolean) = context.dataStore.edit { it[K.appLock] = on }
    suspend fun setOnboarded(on: Boolean) = context.dataStore.edit { it[K.onboarded] = on }
    suspend fun setUserName(n: String) = context.dataStore.edit { it[K.userName] = n.trim() }
}
