package com.azarai.goworkbro.core.util

/** List-order helpers behind the long-press drag sorting on the todo and habit pages. */
object Ordering {

    /**
     * A copy of [items] with the element at [from] moved to index [to].
     * Out-of-range indexes and no-op moves return the list untouched, so a
     * drag that never crosses another card writes nothing.
     */
    fun <T> moved(items: List<T>, from: Int, to: Int): List<T> {
        if (from == to) return items
        if (from !in items.indices || to !in items.indices) return items
        val out = items.toMutableList()
        out.add(to, out.removeAt(from))
        return out
    }

    /**
     * [items] re-ordered to follow [ids], or null when the two no longer hold
     * the same ids — a drag whose list changed under it is dropped and left to
     * the next database emission.
     */
    fun <T> byId(items: List<T>, ids: List<String>, idOf: (T) -> String): List<T>? {
        val byId = items.associateBy(idOf)
        if (ids.size != byId.size) return null
        return ids.map { byId[it] ?: return null }
    }

    /**
     * `sort_order` values for a freshly dragged list, top row first. The step
     * of 10 leaves room for a single insertion later without renumbering.
     * Ordering is DESC for todos (newest/biggest first) and ASC for habits, so
     * the values are handed out biggest-first and the two DAOs pick the right
     * direction.
     */
    fun rankOrders(size: Int): List<Int> = List(size) { index -> (size - index) * 10 }
}
