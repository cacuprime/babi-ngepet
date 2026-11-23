package id.dompetku.data.backup

import id.dompetku.core.t

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import id.dompetku.AppContainer
import id.dompetku.data.db.AccountEntity
import id.dompetku.data.db.AttachmentEntity
import id.dompetku.data.db.CategorizationRuleEntity
import id.dompetku.data.db.DefaultRules
import id.dompetku.data.db.HoldingEntity
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import id.dompetku.data.db.AccountGroupEntity
import id.dompetku.data.db.AccountGroupMemberEntity
import id.dompetku.data.db.CategoryEntity
import id.dompetku.data.db.InvestmentSnapshotEntity
import id.dompetku.data.db.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** File format: "DMPK1" | 16-byte salt | 12-byte IV | AES-256-GCM ciphertext. Key: PBKDF2-SHA256, 210 000 rounds. */
object BackupCrypto {
    private val MAGIC = "DMPK1".toByteArray()
    private const val ITERATIONS = 210_000

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray, password: CharArray): Result<ByteArray> = runCatching {
        require(blob.size > MAGIC.size + 28 && blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            t("Ini bukan file cadangan Babi Ngepet")
        }
        val salt = blob.copyOfRange(5, 21)
        val iv = blob.copyOfRange(21, 33)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        try {
            cipher.doFinal(blob, 33, blob.size - 33)
        } catch (e: Exception) {
            throw IllegalArgumentException(t("Kata sandi salah atau file rusak"))
        }
    }
}

/**
 * Backup payload is a versioned JSON dump of every table. Restore parses and validates
 * everything first, then replaces all rows inside one database transaction, so a failure
 * at any point leaves the current data untouched.
 */
private val SAFE_NAME = Regex("""^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$""")

class BackupManager(private val c: AppContainer) {
    private val dao = c.db.backup()

    /** v3: encrypted ZIP with data.json plus every attachment file under attachments/. */
    suspend fun exportBytes(password: CharArray): ByteArray {
        // Only proof files a transaction refers to; cancelled imports are not carried along.
        val attachments = c.db.attachments().referenced()
        val root = JSONObject()
            .put("format", "dompetku-backup")
            .put("version", 6)
            .put("schema", 7)
            .put("accounts", JSONArray(dao.accounts().map { it.toJson() }))
            .put("categories", JSONArray(dao.categories().map { it.toJson() }))
            .put("transactions", JSONArray(dao.transactions().map { it.toJson() }))
            .put("snapshots", JSONArray(dao.snapshots().map { it.toJson() }))
            .put("groups", groupsJson())
            .put("attachments", JSONArray(attachments.map {
                JSONObject().put("id", it.id).put("fileName", it.fileName).put("filePath", it.filePath)
                    .put("fileType", it.fileType).put("fileSize", it.fileSize).put("createdAt", it.createdAt)
            }))
            .put("holdings", JSONArray(c.db.holdings().all().map {
                JSONObject().put("id", it.id).put("accountId", it.accountId).put("kind", it.kind).put("code", it.code)
                    .put("name", it.name).put("unitsE4", it.unitsE4).put("priceE4", it.priceE4 ?: JSONObject.NULL)
                    .put("priceAt", it.priceAt ?: JSONObject.NULL).put("updatedAt", it.updatedAt)
                    .put("costRp", it.costRp ?: JSONObject.NULL)
            }))
            .put("budgets", JSONArray(c.db.budgets().all().map {
                JSONObject().put("id", it.id).put("start", it.start).put("end", it.endExclusive).put("startDay", it.startDay ?: JSONObject.NULL)
                    .put("mode", it.mode).put("income", it.income).put("needsPct", it.needsPct).put("wantsPct", it.wantsPct)
                    .put("savingsPct", it.savingsPct).put("needs", it.needs).put("wants", it.wants).put("savings", it.savings)
                    .put("createdAt", it.createdAt)
            }))
            .put("goals", JSONArray(c.db.goals().all().map {
                JSONObject().put("id", it.id).put("name", it.name).put("target", it.target).put("deadline", it.deadline)
                    .put("accountId", it.accountId ?: JSONObject.NULL).put("saved", it.saved).put("createdAt", it.createdAt)
            }))
            .put("rules", JSONArray(c.db.rules().all().map {
                JSONObject().put("keyword", it.keyword).put("categoryId", it.suggestedCategoryId)
                    .put("accountId", it.accountId ?: JSONObject.NULL).put("confidence", it.confidence.toDouble())
                    .put("source", it.source).put("usageCount", it.usageCount).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
            }))
        return withContext(Dispatchers.IO) {
            val zip = ByteArrayOutputStream()
            ZipOutputStream(zip).use { z ->
                z.putNextEntry(ZipEntry("data.json")); z.write(root.toString().toByteArray()); z.closeEntry()
                attachments.forEach { a ->
                    val f = c.attachments.file(a)
                    if (f.exists()) { z.putNextEntry(ZipEntry("attachments/${a.filePath}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
                }
            }
            BackupCrypto.encrypt(zip.toByteArray(), password)
        }
    }

    /** Legacy backups (v1/v2) are plain JSON; v3 is a ZIP ("PK" header). */
    private fun unpack(plain: ByteArray): Pair<JSONObject, Map<String, ByteArray>> {
        if (plain.size < 2 || plain[0] != 'P'.code.toByte() || plain[1] != 'K'.code.toByte()) return JSONObject(String(plain)) to emptyMap()
        var json: JSONObject? = null
        val files = HashMap<String, ByteArray>()
        ZipInputStream(plain.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                when {
                    e.name == "data.json" -> json = JSONObject(String(z.readBytes()))
                    e.name.startsWith("attachments/") && !e.name.contains("..") -> files[e.name.removePrefix("attachments/")] = z.readBytes()
                }
            }
        }
        return (json ?: error(t("Ini bukan file cadangan Babi Ngepet"))) to files
    }

    private suspend fun groupsJson(): JSONArray {
        val members = dao.groupMembers().groupBy({ it.groupId }, { it.accountId })
        return JSONArray(dao.groups().map { g ->
            JSONObject().put("id", g.id).put("name", g.name).put("createdAt", g.createdAt)
                .put("accountIds", JSONArray(members[g.id].orEmpty()))
        })
    }

    suspend fun restoreBytes(blob: ByteArray, password: CharArray): Result<Unit> = runCatching {
        val plain = withContext(Dispatchers.Default) { BackupCrypto.decrypt(blob, password) }.getOrThrow()
        val (root, files) = unpack(plain)
        require(root.optString("format") == "dompetku-backup") { t("Ini bukan file cadangan Babi Ngepet") }
        require(root.getInt("version") <= 6) { t("Cadangan dibuat oleh versi aplikasi yang lebih baru") }
        val attachments = root.optJSONArray("attachments")?.objects().orEmpty().map {
            AttachmentEntity(it.getLong("id"), it.getString("fileName"), it.getString("filePath"),
                it.getString("fileType"), it.getLong("fileSize"), it.getLong("createdAt"))
        }.filter { SAFE_NAME.matches(it.filePath) } // a crafted file must never point outside attachments/
        val holdings = root.optJSONArray("holdings")?.objects().orEmpty().map {
            HoldingEntity(it.getLong("id"), it.getLong("accountId"), it.getString("kind"), it.getString("code"), it.getString("name"),
                it.getLong("unitsE4"), it.optLongOrNull("priceE4"), it.optLongOrNull("priceAt"), null, it.getLong("updatedAt"),
                it.optLongOrNull("costRp"))
        }
        // v5 and older have no budgets or goals.
        val budgets = root.optJSONArray("budgets")?.objects().orEmpty().map {
            id.dompetku.data.db.BudgetEntity(it.getLong("id"), it.getLong("start"), it.getLong("end"),
                if (it.isNull("startDay")) null else it.getInt("startDay"), it.getString("mode"), it.getLong("income"),
                it.getInt("needsPct"), it.getInt("wantsPct"), it.getInt("savingsPct"), it.getLong("needs"), it.getLong("wants"),
                it.getLong("savings"), it.getLong("createdAt"))
        }
        val goalsList = root.optJSONArray("goals")?.objects().orEmpty().map {
            id.dompetku.data.db.GoalEntity(it.getLong("id"), it.getString("name"), it.getLong("target"), it.getLong("deadline"),
                it.optLongOrNull("accountId"), it.getLong("saved"), it.getLong("createdAt"))
        }
        val rulesJson = root.optJSONArray("rules")
        val rules = rulesJson?.objects()?.map {
            CategorizationRuleEntity(0, it.getString("keyword"), it.getLong("categoryId"), it.optLongOrNull("accountId"),
                it.getDouble("confidence").toFloat(), it.getString("source"), it.getInt("usageCount"),
                it.getLong("createdAt"), it.getLong("updatedAt"))
        }.orEmpty()
        // Files first (unique names, never overwrite existing ones); then the database swap in one transaction.
        withContext(Dispatchers.IO) {
            attachments.forEach { a ->
                val bytes = files[a.filePath] ?: return@forEach
                val f = c.attachments.file(a)
                if (!f.exists()) { f.parentFile?.mkdirs(); f.writeBytes(bytes) }
            }
        }
        val accounts = root.getJSONArray("accounts").objects().map { accountFrom(it) }
        val categories = root.getJSONArray("categories").objects().map { categoryFrom(it) }
        val transactions = root.getJSONArray("transactions").objects().map { txFrom(it) }
        val snapshots = root.getJSONArray("snapshots").objects().map { snapshotFrom(it) }
        // Version 1 backups have no groups.
        val groupObjs = root.optJSONArray("groups")?.objects().orEmpty()
        val groups = groupObjs.map { AccountGroupEntity(it.getLong("id"), it.getString("name"), it.getLong("createdAt")) }
        val accountIds = accounts.map { it.id }.toSet()
        val members = groupObjs.flatMap { g ->
            val ids = g.getJSONArray("accountIds")
            (0 until ids.length()).map { AccountGroupMemberEntity(g.getLong("id"), ids.getLong(it)) }
        }.filter { it.accountId in accountIds }
        c.db.withTransaction {
            dao.clearTransactions(); dao.clearSnapshots(); dao.clearGroupMembers(); dao.clearGroups()
            dao.clearAccounts(); dao.clearCategories()
            dao.insertCategories(categories); dao.insertAccounts(accounts)
            dao.insertTransactions(transactions); dao.insertSnapshots(snapshots)
            dao.insertGroups(groups); dao.insertGroupMembers(members)
            c.db.attachments().clear(); c.db.attachments().insertAll(attachments)
            c.db.holdings().clear(); c.db.holdings().insertAll(holdings.filter { h -> accounts.any { it.id == h.accountId } })
            c.db.rules().clear(); c.db.inbox().clear()
            c.db.budgets().clear(); c.db.budgets().insertAll(budgets)
            c.db.goals().clear(); c.db.goals().insertAll(goalsList.map { g -> if (accounts.any { it.id == g.accountId }) g else g.copy(accountId = null) })
            if (root.getInt("version") < 6) id.dompetku.data.db.DefaultCategories.setBuckets(c.db.openHelper.writableDatabase)
            // Older backups have no rules: rebuild the built-ins for the restored categories.
            if (rulesJson == null) DefaultRules.seed(c.db.openHelper.writableDatabase) else c.db.rules().insertAll(rules)
        }
    }

    suspend fun export(context: Context, uri: Uri, password: CharArray): Result<Unit> = runCatching {
        val bytes = exportBytes(password)
        withContext(Dispatchers.IO) {
            // Some providers (e.g. Google Drive) reject "wt"; a freshly created document is empty anyway.
            val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                ?: context.contentResolver.openOutputStream(uri, "w")
                ?: error(t("File cadangan tidak bisa ditulis"))
            out.use { it.write(bytes) }
        }
    }

    suspend fun restore(context: Context, uri: Uri, password: CharArray): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
    }.mapCatching { restoreBytes(it, password).getOrThrow() }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
    private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)

    private fun AccountEntity.toJson() = JSONObject().put("id", id).put("name", name).put("type", type)
        .put("provider", provider ?: JSONObject.NULL).put("openingBalance", openingBalance).put("currency", currency)
        .put("isLiquid", isLiquid).put("isArchived", isArchived).put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun accountFrom(o: JSONObject) = AccountEntity(
        o.getLong("id"), o.getString("name"), o.getString("type"), o.optStringOrNull("provider"),
        o.getLong("openingBalance"), o.getString("currency"), o.getBoolean("isLiquid"), o.getBoolean("isArchived"),
        o.getLong("createdAt"), o.getLong("updatedAt"),
    )

    private fun CategoryEntity.toJson() = JSONObject().put("id", id).put("name", name).put("type", type)
        .put("icon", icon).put("color", color).put("isDefault", isDefault)
        .put("bucket", bucket ?: JSONObject.NULL).put("parentId", parentId ?: JSONObject.NULL)

    private fun categoryFrom(o: JSONObject) = CategoryEntity(
        o.getLong("id"), o.getString("name"), o.getString("type"), o.getString("icon"), o.getLong("color"),
        o.getBoolean("isDefault"), o.optStringOrNull("bucket"), o.optLongOrNull("parentId"),
    )

    private fun TransactionEntity.toJson() = JSONObject().put("id", id).put("type", type).put("amount", amount)
        .put("categoryId", categoryId ?: JSONObject.NULL).put("sourceAccountId", sourceAccountId)
        .put("destinationAccountId", destinationAccountId ?: JSONObject.NULL)
        .put("gold", goldTenThousandths ?: JSONObject.NULL).put("description", description ?: JSONObject.NULL)
        .put("merchant", merchant ?: JSONObject.NULL).put("date", date)
        .put("referenceNumber", referenceNumber ?: JSONObject.NULL).put("notes", notes ?: JSONObject.NULL)
        .put("attachmentId", attachmentId ?: JSONObject.NULL).put("inputSource", inputSource).put("status", status)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("tags", tags ?: JSONObject.NULL)

    private fun txFrom(o: JSONObject) = TransactionEntity(
        id = o.getLong("id"), type = o.getString("type"), amount = o.getLong("amount"),
        categoryId = o.optLongOrNull("categoryId"), sourceAccountId = o.getLong("sourceAccountId"),
        destinationAccountId = o.optLongOrNull("destinationAccountId"), goldTenThousandths = o.optLongOrNull("gold"),
        description = o.optStringOrNull("description"), merchant = o.optStringOrNull("merchant"), date = o.getLong("date"),
        referenceNumber = o.optStringOrNull("referenceNumber"), notes = o.optStringOrNull("notes"),
        attachmentId = o.optLongOrNull("attachmentId"), inputSource = o.getString("inputSource"),
        status = o.getString("status"), createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
        tags = o.optStringOrNull("tags"),
    )

    private fun InvestmentSnapshotEntity.toJson() = JSONObject().put("id", id).put("accountId", accountId)
        .put("totalContribution", totalContribution ?: JSONObject.NULL).put("currentValue", currentValue)
        .put("gold", goldTenThousandths ?: JSONObject.NULL).put("goldPrice", goldPricePerGram ?: JSONObject.NULL)
        .put("date", date).put("notes", notes ?: JSONObject.NULL)

    private fun snapshotFrom(o: JSONObject) = InvestmentSnapshotEntity(
        o.getLong("id"), o.getLong("accountId"), o.optLongOrNull("totalContribution"), o.getLong("currentValue"),
        o.optLongOrNull("gold"), o.optLongOrNull("goldPrice"), o.getLong("date"), o.optStringOrNull("notes"),
    )
}
