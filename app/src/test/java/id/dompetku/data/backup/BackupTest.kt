package id.dompetku.data.backup

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.Snapshot
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private lateinit var c: AppContainer
    private val now = Instant.parse("2026-11-11T03:00:00Z")

    @Before fun setUp() { c = AppContainer.inMemory(ApplicationProvider.getApplicationContext()) }
    @After fun tearDown() { c.db.close() }

    @Test fun roundTrip() {
        val plain = "data rahasia".toByteArray()
        val blob = BackupCrypto.encrypt(plain, "sandi123".toCharArray())
        assertArrayEquals(plain, BackupCrypto.decrypt(blob, "sandi123".toCharArray()).getOrThrow())
    }

    @Test fun wrongPasswordFails() {
        val blob = BackupCrypto.encrypt("x".toByteArray(), "benar".toCharArray())
        assertTrue(BackupCrypto.decrypt(blob, "salah".toCharArray()).isFailure)
    }

    @Test fun corruptedBlobFails() {
        val blob = BackupCrypto.encrypt("x".toByteArray(), "benar".toCharArray())
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        assertTrue(BackupCrypto.decrypt(blob, "benar".toCharArray()).isFailure)
        assertTrue(BackupCrypto.decrypt("bukan backup".toByteArray(), "benar".toCharArray()).isFailure)
    }

    private suspend fun seed() {
        val a = c.accounts.create(Account(0, "BCA", AccountType.BANK, "BCA", 1_000_000, true, false, now))
        val g = c.accounts.create(Account(0, "Emas", AccountType.GOLD, null, 0, false, false, now))
        c.snapshots.add(Snapshot(g, now, 24_507_000, 105_000, 2_334_000))
        val cat = c.categories.observe("EXPENSE").first().first().id
        c.transactions.save(Tx(0, TxType.EXPENSE, 35_000, cat, a, null, null, now, "Makan", "Warung", null)).getOrThrow()
    }

    @Test fun restoreWrongPasswordKeepsData() = runBlocking {
        seed()
        val blob = BackupManager(c).exportBytes("pw1".toCharArray())
        assertTrue(BackupManager(c).restoreBytes(blob, "pw2".toCharArray()).isFailure)
        assertEquals(2, c.accounts.observeAll().first().size)
        assertEquals(1, c.transactions.observeAll().first().size)
    }

    @Test fun budgetGoalTagsAndSubcategoriesRoundTrip() = runBlocking {
        seed()
        val acct = c.accounts.observeAll().first().first()
        val parent = c.categories.observe("EXPENSE").first().first { it.name == "Makanan & Minuman" }
        assertEquals("NEEDS", parent.bucket)
        val sub = c.categories.upsert(id.dompetku.data.db.CategoryEntity(0, "Bakso", "EXPENSE", "more", 0, false, null, parent.id))
        c.transactions.save(Tx(0, TxType.EXPENSE, 25_000, sub, acct.id, null, null, now, "bakso", null, null, "liburan bali")).getOrThrow()
        val p = id.dompetku.domain.Budgets.monthlyPeriod(1, now)
        c.budgets.save(id.dompetku.domain.Budget(0, p, 1, id.dompetku.domain.BudgetMode.PERCENT, 8_000_000,
            id.dompetku.domain.Budgets.DEFAULT_PERCENTS, id.dompetku.domain.Budgets.limitsFrom(8_000_000, id.dompetku.domain.Budgets.DEFAULT_PERCENTS))).getOrThrow()
        c.goals.save(id.dompetku.data.db.GoalEntity(0, "Liburan", 5_000_000, now.toEpochMilli(), acct.id, 0, 0)).getOrThrow()

        val blob = BackupManager(c).exportBytes("pw1".toCharArray())
        c.wipeAll()
        BackupManager(c).restoreBytes(blob, "pw1".toCharArray()).getOrThrow()

        assertEquals("liburan bali", c.transactions.observeAll().first().single { it.amount == 25_000L }.tags)
        assertEquals(parent.id, c.categories.observeAll().first().single { it.name == "Bakso" }.parentId)
        val b = c.budgets.observeAll().first().single()
        assertEquals(4_000_000L, b.limits[id.dompetku.domain.Bucket.NEEDS])
        assertEquals(p, b.period)
        assertEquals(acct.id, c.goals.observeAll().first().single().accountId)
    }

    @Test fun editKeepsAttachmentAndTags() = runBlocking {
        seed()
        val acct = c.accounts.observeAll().first().first()
        val cat = c.categories.observe("EXPENSE").first().first().id
        val id = c.transactions.save(Tx(0, TxType.EXPENSE, 10_000, cat, acct.id, null, null, now, "a", null, null, "x")).getOrThrow()
        c.db.transactions().update(c.db.transactions().get(id)!!.copy(attachmentId = 77, referenceNumber = "REF1"))
        c.transactions.save(c.transactions.get(id)!!.copy(amount = 12_000)).getOrThrow()
        val e = c.db.transactions().get(id)!!
        assertEquals(77L, e.attachmentId)
        assertEquals("REF1", e.referenceNumber)
        assertEquals("x", e.tags)
        assertEquals(12_000L, e.amount)
    }

    @Test fun groupsRoundTrip() = runBlocking {
        seed()
        val ids = c.accounts.observeAll().first().map { it.id }
        c.groups.save("Dana harian", ids).getOrThrow()
        val blob = BackupManager(c).exportBytes("pw1".toCharArray())
        c.groups.delete(c.groups.observeAll().first().single().id)
        BackupManager(c).restoreBytes(blob, "pw1".toCharArray()).getOrThrow()
        val g = c.groups.observeAll().first().single()
        assertEquals("Dana harian", g.name)
        assertEquals(ids.sorted(), g.accountIds.sorted())
    }

    @Test fun restoreV1BackupWithoutGroups() = runBlocking {
        val v1 = """{"format":"dompetku-backup","version":1,"schema":1,
            "accounts":[{"id":1,"name":"BSI","type":"BANK","provider":null,"openingBalance":100,"currency":"IDR","isLiquid":true,"isArchived":false,"createdAt":0,"updatedAt":0}],
            "categories":[],"transactions":[],"snapshots":[]}"""
        val blob = BackupCrypto.encrypt(v1.toByteArray(), "pw1".toCharArray())
        BackupManager(c).restoreBytes(blob, "pw1".toCharArray()).getOrThrow()
        assertEquals(1, c.accounts.observeAll().first().size)
        assertEquals(0, c.groups.observeAll().first().size)
    }

    @Test fun attachmentsRoundTrip() = runBlocking {
        seed()
        val att = c.attachments.save("bukti-struk".toByteArray().inputStream(), "struk.jpg", "image/jpeg").getOrThrow()
        val food = c.categories.observe("EXPENSE").first().first().id
        c.rules.learn("Sumber Rejeki Abadi", food)
        val acct = c.accounts.observeAll().first().first().id
        c.imports.saveAll(listOf(id.dompetku.data.ImportedTx(
            Tx(0, TxType.EXPENSE, 1_000, food, acct, null, null, now, "struk", null, null), null, "RECEIPT", att.id,
        ))).getOrThrow()
        val orphan = c.attachments.save("batal".toByteArray().inputStream(), "batal.jpg", "image/jpeg").getOrThrow()
        val blob = BackupManager(c).exportBytes("pw1".toCharArray())
        c.attachments.file(orphan).delete()
        c.attachments.file(att).delete()
        c.wipeAll()
        BackupManager(c).restoreBytes(blob, "pw1".toCharArray()).getOrThrow()
        val back = c.attachments.get(att.id)!!
        assertEquals("bukti-struk", c.attachments.file(back).readText())
        assertTrue(c.rules.all().any { it.keyword == "sumber rejeki abadi" && it.source == "LEARNED" })
        assertEquals(2, c.accounts.observeAll().first().size)
        // Attachments no transaction refers to (cancelled imports) are not carried into backups.
        assertEquals(null, c.attachments.get(orphan.id))
    }

    @Test fun holdingsRoundTrip() = runBlocking {
        seed()
        val acct = c.accounts.observeAll().first().first().id
        c.holdings.save(id.dompetku.data.db.HoldingEntity(0, acct, "STOCK", "BBCA", "BBCA", 3_000_000, 55_000_000, 1, null, 0)).getOrThrow()
        val blob = BackupManager(c).exportBytes("pw1".toCharArray())
        c.wipeAll()
        BackupManager(c).restoreBytes(blob, "pw1".toCharArray()).getOrThrow()
        val h = c.holdings.all().single()
        assertEquals("BBCA", h.code); assertEquals(3_000_000L, h.unitsE4); assertEquals(55_000_000L, h.priceE4)
    }

    @Test fun costRoundTrip() = runBlocking {
        seed()
        val acct = c.accounts.observeAll().first().first().id
        c.holdings.save(id.dompetku.data.db.HoldingEntity(0, acct, "BOND", "FR0100", "FR0100", 20_000, null, null, null, 0, costRp = 1_962_500)).getOrThrow()
        val blob = BackupManager(c).exportBytes("pw1".toCharArray())
        c.wipeAll()
        BackupManager(c).restoreBytes(blob, "pw1".toCharArray()).getOrThrow()
        assertEquals(1_962_500L, c.holdings.all().single().costRp)
    }

    @Test fun unsafeAttachmentPathIsSkipped() = runBlocking {
        val v3 = """{"format":"dompetku-backup","version":3,"schema":3,"accounts":[],"categories":[],"transactions":[],
            "snapshots":[],"groups":[],"rules":[],
            "attachments":[{"id":1,"fileName":"x.jpg","filePath":"../../shared_prefs/dbkey.xml","fileType":"image/jpeg","fileSize":1,"createdAt":0}]}"""
        val zip = java.io.ByteArrayOutputStream().also { bos ->
            java.util.zip.ZipOutputStream(bos).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("data.json")); z.write(v3.toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        BackupManager(c).restoreBytes(BackupCrypto.encrypt(zip, "pw1".toCharArray()), "pw1".toCharArray()).getOrThrow()
        assertEquals(null, c.attachments.get(1))
    }

    @Test fun restoreV2JsonStillWorks() = runBlocking {
        val v2 = """{"format":"dompetku-backup","version":2,"schema":2,
            "accounts":[{"id":1,"name":"BSI","type":"BANK","provider":null,"openingBalance":100,"currency":"IDR","isLiquid":true,"isArchived":false,"createdAt":0,"updatedAt":0}],
            "categories":[{"id":1,"name":"Transportasi","type":"EXPENSE","icon":"x","color":0,"isDefault":true}],
            "transactions":[],"snapshots":[],
            "groups":[{"id":3,"name":"Dana harian","createdAt":0,"accountIds":[1]}]}"""
        val blob = BackupCrypto.encrypt(v2.toByteArray(), "pw1".toCharArray())
        BackupManager(c).restoreBytes(blob, "pw1".toCharArray()).getOrThrow()
        assertEquals(1, c.groups.observeAll().first().size)
        // Built-in rules are re-created for the restored categories.
        assertTrue(c.rules.all().any { it.keyword == "gojek" && it.suggestedCategoryId == 1L })
    }

    @Test fun restoreReplacesData() = runBlocking {
        seed()
        val before = c.dashboard.accountViews().first()
        val blob = BackupManager(c).exportBytes("pw1".toCharArray())
        c.accounts.create(Account(0, "Baru", AccountType.CASH, null, 5, true, false, now))
        BackupManager(c).restoreBytes(blob, "pw1".toCharArray()).getOrThrow()
        assertEquals(before, c.dashboard.accountViews().first())
        assertEquals(20, c.categories.observeAll().first().size)
    }
}
