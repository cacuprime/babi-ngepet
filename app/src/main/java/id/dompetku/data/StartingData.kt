package id.dompetku.data

import android.content.Context
import id.dompetku.AppContainer
import id.dompetku.core.Grams
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.Snapshot
import org.json.JSONArray
import java.time.Instant

data class StartingAccount(
    val name: String,
    val type: AccountType,
    val provider: String?,
    val balance: Long,
    val value: Long?,
    val goldTenThousandths: Long?,
    val goldPricePerGram: Long?,
    val isLiquid: Boolean?,
)

/**
 * Optional one-time starting data from the git-ignored asset `starting_data.json`.
 * Each entry becomes an account (opening balance = balance) and, for valued
 * accounts, a first snapshot dated at setup time.
 */
object StartingData {
    const val ASSET = "starting_data.json"

    fun load(context: Context): List<StartingAccount>? = runCatching {
        context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
    }.getOrNull()

    fun parse(json: String): List<StartingAccount> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val balance = o.getLong("balance")
            require(balance >= 0) { "Saldo awal tidak boleh negatif: ${o.optString("name")}" }
            val grams = if (o.has("grams")) Grams.parse(o.getString("grams"))
                ?: throw IllegalArgumentException("Gram tidak valid: ${o.getString("grams")}") else null
            StartingAccount(
                name = o.getString("name").also { require(it.isNotBlank()) { "Nama akun kosong" } },
                type = AccountType.valueOf(o.getString("type")),
                provider = o.optString("provider").ifBlank { null },
                balance = balance,
                value = if (o.has("value")) o.getLong("value") else null,
                goldTenThousandths = grams,
                goldPricePerGram = if (o.has("goldPricePerGram")) o.getLong("goldPricePerGram") else null,
                isLiquid = if (o.has("isLiquid")) o.getBoolean("isLiquid") else null,
            )
        }
    }

    suspend fun apply(items: List<StartingAccount>, c: AppContainer, now: Instant) {
        for (it in items) createAccount(c, it, now)
    }

    /** Shared by starting data and the manual onboarding form. */
    suspend fun createAccount(c: AppContainer, it: StartingAccount, now: Instant): Long {
        val id = c.accounts.create(
            Account(0, it.name.trim(), it.type, it.provider, it.balance, it.isLiquid ?: it.type.defaultLiquid, false, now)
        )
        if (it.type == AccountType.GOLD && it.goldTenThousandths != null && it.goldPricePerGram != null) {
            c.snapshots.add(
                Snapshot(id, now, Grams.valueOf(it.goldTenThousandths, it.goldPricePerGram), it.goldTenThousandths, it.goldPricePerGram)
            )
        } else if (it.type.isValued) {
            c.snapshots.add(Snapshot(id, now, it.value ?: it.balance, null, null))
        }
        return id
    }
}
