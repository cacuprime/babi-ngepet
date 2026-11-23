package id.dompetku.ui.home

import id.dompetku.data.AccountGroup
import id.dompetku.data.AccountView
import id.dompetku.data.GroupView
import id.dompetku.domain.Account
import id.dompetku.domain.AccountType
import id.dompetku.domain.Summary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HomeOrderTest {
    private val total = HomePage.Total(Summary(0, 0, 0, 0, 0, 0))
    private fun acc(id: Long) = HomePage.Single(AccountView(Account(id, "A$id", AccountType.BANK, null, 0, true, false, Instant.EPOCH), 0, 0, null))
    private val group = HomePage.Group(GroupView(AccountGroup(7, "Dana harian", listOf(1, 2)), 0, emptyList()))
    private val pages = listOf(total, group, acc(1), acc(2))

    @Test fun keysAreStable() {
        assertEquals(listOf("T", "G:7", "A:1", "A:2"), pages.map { HomeOrder.key(it) })
    }

    @Test fun orderApplied() {
        val r = HomeOrder.apply(pages, listOf("A:2", "G:7", "T", "A:1"), emptySet())
        assertEquals(listOf("A:2", "G:7", "T", "A:1"), r.map { HomeOrder.key(it) })
    }

    @Test fun hiddenRemoved() {
        val r = HomeOrder.apply(pages, emptyList(), setOf("T", "A:1"))
        assertEquals(listOf("G:7", "A:2"), r.map { HomeOrder.key(it) })
    }

    @Test fun newAccountAppended() {
        // A:3 was created after the order was saved: it shows, at the end.
        val r = HomeOrder.apply(pages + acc(3), listOf("A:2", "T", "G:7", "A:1"), emptySet())
        assertEquals(listOf("A:2", "T", "G:7", "A:1", "A:3"), r.map { HomeOrder.key(it) })
    }

    @Test fun orderKeepsAtLeastOne() {
        val r = HomeOrder.apply(pages, emptyList(), setOf("T", "G:7", "A:1", "A:2"))
        assertEquals(listOf("T"), r.map { HomeOrder.key(it) })
        assertEquals(listOf("A:2", "A:1"), HomeOrder.move(listOf("A:1", "A:2"), "A:2", -1))
        assertEquals(listOf("A:1", "A:2"), HomeOrder.move(listOf("A:1", "A:2"), "A:1", -1))
    }
}
