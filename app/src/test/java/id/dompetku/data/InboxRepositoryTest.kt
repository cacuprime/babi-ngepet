package id.dompetku.data

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.capture.BankApp
import id.dompetku.capture.BankNotificationParser
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class InboxRepositoryTest {
    private lateinit var c: AppContainer
    private lateinit var inbox: InboxRepository
    private var now = Instant.parse("2026-11-18T05:00:00Z").toEpochMilli()
    private var bni = 0L
    private var auto = true

    @Before fun setUp() = runBlocking {
        c = AppContainer.inMemory(ApplicationProvider.getApplicationContext())
        bni = c.accounts.create(Account(0, "BNI", AccountType.BANK, "BNI", 1_000_000, true, false, Instant.EPOCH))
        inbox = InboxRepository(c.db, c.rules, accountFor = { if (it == BankApp.WONDR) bni else null }, autoSave = { auto }, clock = { now })
    }
    @After fun tearDown() { c.db.close() }

    private fun parsed(text: String) = BankNotificationParser.parse(null, text, Instant.ofEpochMilli(now))
    private fun qris(amount: String, merchant: String) = "Pembayaran QRIS Rp$amount ke $merchant berhasil"
    private suspend fun capture(text: String, app: BankApp = BankApp.WONDR) = inbox.capture(app, text, parsed(text))
    private suspend fun waiting() = inbox.observeWaiting().first()
    private suspend fun food() = c.categories.observe("EXPENSE").first().first { it.name == "Makanan & Minuman" }.id
    private suspend fun balance(id: Long) = c.dashboard.accountViews().first().first { it.account.id == id }.balance
    private fun later(min: Long) { now += min * 60_000 }

    private suspend fun saveAll() = inbox.save(waiting().map {
        InboxEdit(it.id, TxType.valueOf(it.txType!!), it.amount!!, it.categoryId ?: food(), it.accountId, null, it.description, it.merchant)
    })

    @Test fun waitingItemDoesNotChangeBalance() = runBlocking {
        assertEquals(CaptureOutcome.WAITING, capture(qris("25.000", "WARUNG BU SRI")))
        val w = waiting().single()
        assertEquals(25_000L, w.amount); assertEquals(bni, w.accountId)
        assertEquals(1_000_000L, balance(bni))
    }

    @Test fun repostedNotificationIsDroppedOnce() = runBlocking {
        val t = qris("25.000", "WARUNG BU SRI")
        capture(t); later(1)
        assertEquals(CaptureOutcome.DUPLICATE_NOTIFICATION, capture(t))
        assertEquals(1, waiting().size)
    }

    @Test fun ignoredAndUnreadAreNotWaiting() = runBlocking {
        assertEquals(CaptureOutcome.IGNORED, capture("Kode OTP Anda 123456"))
        assertEquals(CaptureOutcome.UNREAD, capture("Saldo Anda Rp500.000"))
        assertEquals(0, waiting().size)
        assertEquals(1, inbox.observeUnread().first().size)
    }

    @Test fun saveCreatesConfirmedTxAndLearns() = runBlocking {
        capture(qris("25.000", "WARUNG BU SRI"))
        assertEquals(1, saveAll().getOrThrow())
        assertEquals(975_000L, balance(bni))
        assertEquals(0, waiting().size)
        assertEquals(1, c.rules.learnedFor("WARUNG BU SRI")!!.usageCount)
    }

    @Test fun autoSaveAfterTwoConfirmations() = runBlocking {
        repeat(2) { capture(qris("25.000", "WARUNG BU SRI")); saveAll(); later(40) }
        assertEquals(CaptureOutcome.AUTO_SAVED, capture(qris("30.000", "WARUNG BU SRI")))
        assertEquals(0, waiting().size)
        assertEquals(1_000_000L - 80_000L, balance(bni))
        val auto = c.db.transactions().observeAllConfirmed().first().maxBy { it.id }
        assertEquals("NOTIFICATION", auto.inputSource); assertEquals(food(), auto.categoryId)
    }

    @Test fun autoSaveOffKeepsWaiting() = runBlocking {
        repeat(2) { capture(qris("25.000", "WARUNG BU SRI")); saveAll(); later(40) }
        auto = false
        assertEquals(CaptureOutcome.WAITING, capture(qris("30.000", "WARUNG BU SRI")))
    }

    @Test fun existingTransactionBlocksAutoSave() = runBlocking {
        repeat(2) { capture(qris("25.000", "WARUNG BU SRI")); saveAll(); later(40) }
        // Same amount, same account, same day as the last saved one → probably the same payment.
        assertEquals(CaptureOutcome.WAITING, capture("Bayar QRIS Rp25.000 di WARUNG BU SRI sukses"))
        assertNotNull(waiting().single().duplicateOf)
    }

    @Test fun similarNotificationFlaggedNotDropped() = runBlocking {
        capture(qris("40.000", "KOPI A")); later(1)
        assertEquals(CaptureOutcome.WAITING, capture("Transaksi QRIS berhasil. Rp40.000 KOPI A"))
        val items = waiting()
        assertEquals(2, items.size)
        assertTrue(items.any { it.duplicateOf == InboxRepository.SIMILAR_NOTIFICATION })
    }

    @Test fun unmappedOrArchivedAccountWaitsWithoutAccount() = runBlocking {
        capture(qris("10.000", "TOKO A"), BankApp.SEABANK)
        assertNull(waiting().single().accountId)
        c.accounts.archive(bni, true)
        capture(qris("12.000", "TOKO B"))
        assertTrue(waiting().all { it.accountId == null })
        val r = saveAll()
        assertTrue(r.isFailure)
    }

    @Test fun transferBetweenOwnAccounts() = runBlocking {
        val sea = c.accounts.create(Account(0, "SeaBank", AccountType.BANK, "SeaBank", 0, true, false, Instant.EPOCH))
        capture("Transfer ke SEABANK SAYA Rp200.000 berhasil")
        val w = waiting().single()
        inbox.save(listOf(InboxEdit(w.id, TxType.TRANSFER, 200_000, null, bni, sea, "Pindah dana", null))).getOrThrow()
        assertEquals(800_000L, balance(bni)); assertEquals(200_000L, balance(sea))
    }

    @Test fun dismissAndCleanup() = runBlocking {
        capture(qris("10.000", "TOKO A"))
        inbox.dismiss(waiting().single().id)
        assertEquals(0, waiting().size)
        later(31L * 24 * 60)
        inbox.cleanup()
        assertEquals(0, c.db.inbox().countSince(InboxRepository.hash("WONDR|" + qris("10.000", "TOKO A")), 0))
    }

}
