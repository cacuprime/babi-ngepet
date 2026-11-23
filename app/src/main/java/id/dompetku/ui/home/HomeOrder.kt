package id.dompetku.ui.home

/** The owner's order and visibility of Home swipe cards. Keys: "T" total, "G:<id>" group, "A:<id>" account. */
object HomeOrder {
    fun key(p: HomePage): String = when (p) {
        is HomePage.Total -> "T"
        is HomePage.Group -> "G:${p.view.group.id}"
        is HomePage.Single -> "A:${p.view.account.id}"
    }

    /** Pages in the saved order (cards not in it yet go last, in their default order), minus hidden ones; never empty. */
    fun apply(pages: List<HomePage>, order: List<String>, hidden: Set<String>): List<HomePage> {
        val rank = order.withIndex().associate { it.value to it.index }
        val sorted = pages.withIndex().sortedWith(compareBy({ rank[key(it.value)] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
        return sorted.filter { key(it) !in hidden }.ifEmpty { sorted.take(1) }
    }

    /** Moves [k] one place up (delta = -1) or down (+1) within [order]. */
    fun move(order: List<String>, k: String, delta: Int): List<String> {
        val i = order.indexOf(k)
        val j = i + delta
        if (i < 0 || j !in order.indices) return order
        return order.toMutableList().apply { this[i] = this[j]; this[j] = k }
    }
}
