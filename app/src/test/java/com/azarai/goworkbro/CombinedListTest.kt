package com.azarai.goworkbro

import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.ui.todo.CombinedItem
import com.azarai.goworkbro.ui.todo.buildCombinedList
import com.azarai.goworkbro.ui.todo.mapReorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedListTest {

    private fun todo(id: String, order: Int, completed: Boolean = false, completedDate: String? = null) =
        Todo(id = id, title = "t-$id", sortOrder = order, isCompleted = completed, completedDate = completedDate)

    private fun habit(id: String, order: Int, current: Int = 0, target: Int = 1) =
        Habit(id = id, title = "h-$id", sortOrder = order, currentCount = current, targetCount = target)

    @Test
    fun `order is unfinished habits then todos then completed`() {
        val todos = listOf(todo("t1", 0), todo("t2", 1, completed = true, completedDate = "2026-08-31T10:00"))
        val habits = listOf(habit("h1", 0, current = 1), habit("h2", 1))
        val items = buildCombinedList(todos, habits)
        val ids = items.map {
            when (it) {
                is CombinedItem.TodoItem -> it.todo.id
                is CombinedItem.HabitItem -> it.habit.id
            }
        }
        assertEquals(listOf("h2", "t1", "h1", "t2"), ids)
    }

    @Test
    fun `completed todos sort by completedDate desc`() {
        val todos = listOf(
            todo("older", 0, completed = true, completedDate = "2026-08-30T20:00"),
            todo("newer", 1, completed = true, completedDate = "2026-08-31T09:00"),
        )
        val items = buildCombinedList(todos, emptyList())
        val ids = items.map { (it as CombinedItem.TodoItem).todo.id }
        assertEquals(listOf("newer", "older"), ids)
    }

    @Test
    fun `dragging todo down one slot maps to reorder`() {
        val todos = listOf(todo("a", 0), todo("b", 1), todo("c", 2))
        // onReorder(0, 1): drop a after b -> final order [b, a, c].
        // The mapping is the provider-space index BEFORE the internal
        // "if (new > old) new--" adjustment, so 2 here (VM turns it into 1).
        val mapping = mapReorder(todos, emptyList(), oldIndex = 0, newIndex = 1)
        assertEquals(false, mapping?.isHabit)
        assertEquals(0, mapping?.oldIndex)
        assertEquals(2, mapping?.newIndex)
    }

    @Test
    fun `dragging todo up one slot maps to reorder`() {
        val todos = listOf(todo("a", 0), todo("b", 1), todo("c", 2))
        // onReorder(2, 1): move c above b -> [a, c, b]
        val mapping = mapReorder(todos, emptyList(), oldIndex = 2, newIndex = 1)
        assertEquals(false, mapping?.isHabit)
        assertEquals(2, mapping?.oldIndex)
        assertEquals(1, mapping?.newIndex)
    }

    @Test
    fun `completed items are not draggable`() {
        val todos = listOf(todo("a", 0), todo("done", 1, completed = true, completedDate = "x"))
        assertNull(mapReorder(todos, emptyList(), oldIndex = 1, newIndex = 0))
    }

    @Test
    fun `todo drag cannot enter habit zone`() {
        val todos = listOf(todo("t1", 0))
        val habits = listOf(habit("h1", 0))
        // t1 sits at display index 1 (after h1); dragging it up over h1 is a no-op
        assertNull(mapReorder(todos, habits, oldIndex = 1, newIndex = 0))
    }

    @Test
    fun `habit drag confined to habit zone`() {
        val todos = listOf(todo("t1", 0), todo("t2", 1))
        val habits = listOf(habit("h1", 0), habit("h2", 1))
        // Display: [h1, h2, t1, t2]. Drag h2 down over t1 (index 2): final pos clamps to end of habit zone.
        val mapping = mapReorder(todos, habits, oldIndex = 1, newIndex = 2)
        assertTrue(mapping == null || mapping.isHabit)
    }
}
