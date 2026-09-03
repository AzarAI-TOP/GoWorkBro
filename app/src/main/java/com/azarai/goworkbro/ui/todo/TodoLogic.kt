package com.azarai.goworkbro.ui.todo

import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.db.Todo

/** A combined display item: either a todo or a habit. */
sealed interface CombinedItem {
    data class TodoItem(val todo: Todo) : CombinedItem
    data class HabitItem(val habit: Habit) : CombinedItem
}

/**
 * Combined display order (v1 parity):
 * 1. unfinished habits (by sortOrder)
 * 2. incomplete todos (by sortOrder)
 * 3. completed habits (by sortOrder)
 * 4. completed todos (by completedDate desc)
 */
fun buildCombinedList(todos: List<Todo>, habits: List<Habit>): List<CombinedItem> {
    val unfinishedHabits = habits.filter { !it.isCompleted }.sortedBy { it.sortOrder }
    val completedHabits = habits.filter { it.isCompleted }.sortedBy { it.sortOrder }
    val incompleteTodos = todos.filter { !it.isCompleted }.sortedBy { it.sortOrder }
    val completedTodos = todos.filter { it.isCompleted }
        .sortedByDescending { it.completedDate ?: "" }
    return unfinishedHabits.map { CombinedItem.HabitItem(it) } +
        incompleteTodos.map { CombinedItem.TodoItem(it) } +
        completedHabits.map { CombinedItem.HabitItem(it) } +
        completedTodos.map { CombinedItem.TodoItem(it) }
}

data class ReorderMapping(val isHabit: Boolean, val oldIndex: Int, val newIndex: Int)

/**
 * Maps a display-space drag (ReorderableListView semantics: newIndex is the
 * final insertion position with the dragged item conceptually removed) to
 * provider-space indexes. Returns null for no-ops and completed items.
 */
fun mapReorder(
    todos: List<Todo>,
    habits: List<Habit>,
    oldIndex: Int,
    newIndex: Int,
): ReorderMapping? {
    val items = buildCombinedList(todos, habits)
    if (oldIndex < 0 || oldIndex >= items.size) return null
    val oldItem = items[oldIndex]

    val unfinishedHabits = habits.filter { !it.isCompleted }.sortedBy { it.sortOrder }
    val incompleteTodos = todos.filter { !it.isCompleted }.sortedBy { it.sortOrder }
    val uh = unfinishedHabits.size
    val it2 = incompleteTodos.size

    if (oldItem is CombinedItem.HabitItem) {
        val habit = oldItem.habit
        if (habit.isCompleted) return null
        val dropAfter = newIndex > oldIndex
        val finalPos = newIndex.coerceIn(0, uh)
        val oldHabitIndex = habits.indexOfFirst { it.id == habit.id }
        if (oldHabitIndex < 0) return null
        val newHabitIndex = if (finalPos >= uh) {
            habits.indexOfFirst { it.id == unfinishedHabits.last().id } + 1
        } else {
            habits.indexOfFirst { it.id == unfinishedHabits[finalPos].id } + if (dropAfter) 1 else 0
        }
        if (newHabitIndex == oldHabitIndex || newHabitIndex == oldHabitIndex + 1) return null
        return ReorderMapping(true, oldHabitIndex, newHabitIndex)
    }

    if (oldItem is CombinedItem.TodoItem) {
        val todo = oldItem.todo
        if (todo.isCompleted) return null
        val dropAfter = newIndex > oldIndex
        val finalPos = (newIndex - uh).coerceIn(0, it2)
        val oldTodoIndex = todos.indexOfFirst { it.id == todo.id }
        if (oldTodoIndex < 0) return null
        val newTodoIndex = if (finalPos >= it2) {
            todos.size
        } else {
            todos.indexOfFirst { it.id == incompleteTodos[finalPos].id } + if (dropAfter) 1 else 0
        }
        if (newTodoIndex == oldTodoIndex || newTodoIndex == oldTodoIndex + 1) return null
        return ReorderMapping(false, oldTodoIndex, newTodoIndex)
    }
    return null
}
