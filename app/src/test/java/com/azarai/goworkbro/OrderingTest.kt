package com.azarai.goworkbro

import com.azarai.goworkbro.core.util.Ordering
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure logic behind the long-press drag sorting. */
class OrderingTest {

    private val list = listOf("a", "b", "c", "d")

    @Test
    fun `moving one step either way`() {
        assertEquals(listOf("b", "a", "c", "d"), Ordering.moved(list, 0, 1))
        assertEquals(listOf("a", "b", "d", "c"), Ordering.moved(list, 3, 2))
    }

    @Test
    fun `moving across the grid`() {
        assertEquals(listOf("b", "c", "d", "a"), Ordering.moved(list, 0, 3))
        assertEquals(listOf("d", "a", "b", "c"), Ordering.moved(list, 3, 0))
    }

    @Test
    fun `no-op and out-of-range moves change nothing`() {
        assertEquals(list, Ordering.moved(list, 2, 2))
        assertEquals(list, Ordering.moved(list, -1, 2))
        assertEquals(list, Ordering.moved(list, 0, 4))
        assertEquals(emptyList<String>(), Ordering.moved(emptyList<String>(), 0, 0))
    }

    @Test
    fun `byId follows the dragged ids and refuses a stale set`() {
        val rows = listOf("a", "b", "c")
        assertEquals(
            listOf("c", "a", "b"),
            Ordering.byId(rows, listOf("c", "a", "b")) { it },
        )
        // an id the list no longer holds (completed / deleted mid-drag)
        assertEquals(null, Ordering.byId(rows, listOf("c", "gone", "b")) { it })
        // a shorter list (one row disappeared)
        assertEquals(null, Ordering.byId(rows, listOf("c", "a")) { it })
        assertEquals(emptyList<String>(), Ordering.byId(emptyList<String>(), emptyList()) { it })
    }

    @Test
    fun `rank orders hand out the biggest value first`() {
        assertEquals(listOf(30, 20, 10), Ordering.rankOrders(3))
        assertEquals(listOf(10), Ordering.rankOrders(1))
        assertEquals(emptyList<Int>(), Ordering.rankOrders(0))
    }
}
