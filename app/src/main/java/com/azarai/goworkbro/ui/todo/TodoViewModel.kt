package com.azarai.goworkbro.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.Rollover
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.FocusSession
import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Todo + habit operations (port of AppProvider's non-sync logic). */
class TodoViewModel : ViewModel() {

    private val db = Graph.db
    private val store = Graph.store

    val todos: StateFlow<List<Todo>> = db.todoDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val habits: StateFlow<List<Habit>> = db.habitDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customUnits: StateFlow<List<String>> = store.observeCustomHabitUnits()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addCustomUnit(unit: String) {
        viewModelScope.launch { store.addCustomHabitUnit(unit) }
    }

    fun addTodo(title: String, timingType: String, durationMinutes: Int, keepTomorrow: Boolean) {
        viewModelScope.launch {
            val count = db.todoDao().getAll().size
            db.todoDao().upsert(
                Todo(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    timingType = timingType,
                    durationMinutes = durationMinutes,
                    keepTomorrow = keepTomorrow,
                    sortOrder = count,
                    createdDate = Dates.nowIso(),
                ),
            )
        }
    }

    fun updateTodo(updated: Todo) {
        viewModelScope.launch { db.todoDao().upsert(updated) }
    }

    /** Toggle completion; keeps lifetime counters and "tomorrow" copies in sync. */
    fun toggleComplete(todo: Todo) {
        viewModelScope.launch {
            val isCompleting = !todo.isCompleted
            db.todoDao().upsert(
                todo.copy(
                    isCompleted = isCompleting,
                    completedDate = if (isCompleting) Dates.nowIso() else null,
                ),
            )
            if (isCompleting) {
                store.incrementCounterOnce(
                    Store.Keys.LIFETIME_TODOS_COMPLETED,
                    "completion.todo.${todo.id}",
                )
                if (todo.keepTomorrow) createTomorrowCopyIfNeeded(todo)
            }
        }
    }

    /** Timer finish: mark complete with accumulated seconds (no double count). */
    fun completeTodoWithDuration(todoId: String, additionalSeconds: Int) {
        viewModelScope.launch {
            val current = db.todoDao().getById(todoId) ?: return@launch
            db.todoDao().upsert(
                current.copy(
                    isCompleted = true,
                    completedDate = Dates.nowIso(),
                    actualDurationSeconds = current.actualDurationSeconds + additionalSeconds,
                ),
            )
            if (!current.isCompleted) {
                store.incrementCounterOnce(
                    Store.Keys.LIFETIME_TODOS_COMPLETED,
                    "completion.todo.${current.id}",
                )
                if (current.keepTomorrow) createTomorrowCopyIfNeeded(current)
            }
        }
    }

    /** For keepTomorrow todos: create an incomplete copy unless one exists. */
    private suspend fun createTomorrowCopyIfNeeded(completed: Todo) {
        val all = db.todoDao().getAll()
        val hasIncompleteCopy = all.any {
            it.title == completed.title && !it.isCompleted && it.createdDate != completed.createdDate
        }
        if (hasIncompleteCopy) return
        db.todoDao().upsert(
            Todo(
                id = UUID.randomUUID().toString(),
                title = completed.title,
                timingType = completed.timingType,
                durationMinutes = completed.durationMinutes,
                sortOrder = completed.sortOrder,
                keepTomorrow = true,
                createdDate = Dates.nowIso(),
            ),
        )
    }

    fun deleteTodo(id: String) {
        viewModelScope.launch { db.todoDao().deleteById(id) }
    }

    fun reorderTodos(oldIndex: Int, newIndexRaw: Int) {
        viewModelScope.launch {
            var newIndex = newIndexRaw
            if (newIndex > oldIndex) newIndex--
            val list = db.todoDao().getAll().toMutableList()
            if (oldIndex !in list.indices || newIndex !in 0..list.size) return@launch
            val item = list.removeAt(oldIndex)
            list.add(newIndex, item)
            db.withTransaction {
                list.forEachIndexed { index, todo ->
                    db.todoDao().upsert(todo.copy(sortOrder = index))
                }
            }
        }
    }

    fun reorderHabits(oldIndex: Int, newIndexRaw: Int) {
        viewModelScope.launch {
            var newIndex = newIndexRaw
            if (newIndex > oldIndex) newIndex--
            val list = db.habitDao().getAll().toMutableList()
            if (oldIndex !in list.indices || newIndex !in 0..list.size) return@launch
            val item = list.removeAt(oldIndex)
            list.add(newIndex, item)
            db.withTransaction {
                list.forEachIndexed { index, habit ->
                    db.habitDao().upsert(habit.copy(sortOrder = index))
                }
            }
        }
    }

    fun addHabit(title: String, targetCount: Int, unit: String) {
        viewModelScope.launch {
            val count = db.habitDao().getAll().size
            db.habitDao().upsert(
                Habit(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    targetCount = targetCount,
                    unit = unit,
                    sortOrder = count,
                    createdDate = Dates.nowIso(),
                ),
            )
        }
    }

    fun updateHabit(updated: Habit) {
        viewModelScope.launch { db.habitDao().upsert(updated) }
    }

    fun incrementHabit(habit: Habit) {
        viewModelScope.launch {
            if (habit.currentCount >= habit.targetCount) return@launch
            val updated = habit.copy(currentCount = habit.currentCount + 1)
            if (!habit.isCompleted && updated.isCompleted) {
                val today = Rollover.logicalToday(store)
                store.incrementCounterOnce(
                    Store.Keys.LIFETIME_HABITS_COMPLETED,
                    "completion.habit.${habit.id}.$today",
                )
            }
            db.habitDao().upsert(updated)
        }
    }

    fun decrementHabit(habit: Habit) {
        viewModelScope.launch {
            if (habit.currentCount == 0) return@launch
            db.habitDao().upsert(habit.copy(currentCount = habit.currentCount - 1))
        }
    }

    fun deleteHabit(id: String) {
        viewModelScope.launch { db.habitDao().deleteById(id) }
    }

    /** Records a focus session into the current logical day (v1 bucketing). */
    fun recordFocusSession(
        todoId: String?,
        sourceTitle: String,
        durationSeconds: Int,
    ) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            db.focusDao().insert(
                FocusSession(
                    id = UUID.randomUUID().toString(),
                    todoId = todoId,
                    sourceType = "todo",
                    sourceTitle = sourceTitle,
                    startTime = Dates.nowIso(),
                    endTime = Dates.nowIso(),
                    durationSeconds = durationSeconds,
                    sessionDate = Rollover.logicalToday(store),
                ),
            )
        }
    }
}
