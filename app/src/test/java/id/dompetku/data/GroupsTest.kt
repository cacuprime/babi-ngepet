package id.dompetku.data

import androidx.test.core.app.ApplicationProvider
import id.dompetku.AppContainer
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class GroupsTest {
    private lateinit var c: AppContainer
    private val now = Instant.parse("2026-11-15T03:00:00Z")

    @Before fun setUp() { c = AppContainer.inMemory(ApplicationProvider.getApplicationContext()) }
    @After fun tearDown() { c.db.close() }

    private suspend fun bank(name: String, bal: Long) =
        c.accounts.create(Account(0, name, AccountType.BANK, null, bal, true, false, now))

    @Test fun groupTotalSumsMembers() = runBlocking {
        val m = bank("Mandiri", 981_771); val b = bank("BNI", 335_743); val s = bank("SeaBank", 5_290_064)
        bank("BSI", 10_000_000)
        c.groups.save("Dana harian", listOf(m, b, s)).getOrThrow()
        val v = c.dashboard.groupViews().first().single()
        assertEquals("Dana harian", v.group.name)
        assertEquals(6_607_578L, v.total)
        assertEquals(3, v.members.size)
    }

    @Test fun groupTotalSkipsDeletedMember() = runBlocking {
        val m = bank("Mandiri", 981_771); val b = bank("BNI", 335_743); val s = bank("SeaBank", 5_290_064)
        c.groups.save("Dana harian", listOf(m, b, s)).getOrThrow()
        c.accounts.delete(b).getOrThrow()
        c.accounts.archive(s, true)
        val v = c.dashboard.groupViews().first().single()
        assertEquals(981_771L + 5_290_064L, v.total)
        assertEquals(listOf(m, s), c.groups.observeAll().first().single().accountIds.sorted())
    }

    @Test fun saveRejectsBlankOrEmpty() = runBlocking {
        val m = bank("Mandiri", 1)
        assertTrue(c.groups.save(" ", listOf(m)).isFailure)
        assertTrue(c.groups.save("Grup", emptyList()).isFailure)
    }

    @Test fun editAndDeleteGroup() = runBlocking {
        val m = bank("Mandiri", 1); val b = bank("BNI", 2)
        val id = c.groups.save("Grup", listOf(m)).getOrThrow()
        c.groups.save("Grup 2", listOf(m, b), id).getOrThrow()
        val g = c.groups.observeAll().first().single()
        assertEquals("Grup 2", g.name); assertEquals(listOf(m, b), g.accountIds.sorted())
        c.groups.delete(id)
        assertTrue(c.groups.observeAll().first().isEmpty())
    }
}
