package com.azarai.goworkbro.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.db.TimingType
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.core.util.Ordering
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the todo page renders. Finished work is split by the logical day it was
 * completed on: today's stays visible, everything older folds away behind the
 * "更早完成" row. Nothing is deleted — the page just cleans itself every day.
 */
data class TodoListState(
    val undone: List<Todo> = emptyList(),
    val doneToday: List<Todo> = emptyList(),
    val olderDone: List<Todo> = emptyList(),
    /** All completions from previous days — drives the "更早完成" row. */
    val olderDoneTotal: Int = 0,
    val showOlder: Boolean = false,
) {
    /**
     * Room re-runs `observeUndone` and `observeDoneOn` independently, so right
     * after a completion one frame can carry the same todo in BOTH lists — a
     * duplicate lazy key that crashes the grid. The completed row wins.
     */
    fun withoutDuplicates(): TodoListState {
        val done = (doneToday + olderDone).mapTo(HashSet()) { it.id }
        return if (done.isEmpty()) this else copy(undone = undone.filterNot { it.id in done })
    }
}

class TodoViewModel : ViewModel() {
    private val dao = Graph.db.todoDao()

    private val logicalKey = LogicalDay.flow.map { it.dateKey }.distinctUntilChanged()

    /** Older history renders capped; expandDone() lifts the cap for this
     *  session. Collapsed by default, so the query is not even subscribed. */
    private val doneLimit = MutableStateFlow(50)
    private val showOlderFlow = MutableStateFlow(false)

    val state: StateFlow<TodoListState> = combine(
        dao.observeUndone(),
        logicalKey,
        showOlderFlow,
    ) { undone, day, show -> Triple(undone, day, show) }
        .flatMapLatest { (undone, day, show) ->
            if (!show) {
                combine(
                    dao.observeDoneOn(day),
                    dao.observeDoneCountBefore(day),
                ) { today, olderTotal ->
                    TodoListState(undone, today, emptyList(), olderTotal, false)
                }
            } else {
                combine(
                    dao.observeDoneOn(day),
                    dao.observeDoneCountBefore(day),
                    doneLimit.flatMapLatest { dao.observeDoneBefore(day, it) },
                ) { today, olderTotal, older ->
                    TodoListState(undone, today, older, olderTotal, true)
                }
            }
        }
        .map { it.withoutDuplicates() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoListState())

    fun toggleOlder() {
        showOlderFlow.value = !showOlderFlow.value
    }

    fun expandDone() {
        doneLimit.value = Int.MAX_VALUE
    }

    fun add(title: String, icon: String, colorIndex: Int, timing: TimingType, durationMin: Int) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            dao.upsert(
                Todo(
                    id = UUID.randomUUID().toString(),
                    title = clean,
                    icon = icon,
                    colorIndex = colorIndex,
                    timingType = timing.raw,
                    durationMinutes = durationMin.coerceIn(1, 480),
                    sortOrder = (System.currentTimeMillis() / 1000).toInt(),
                    createdDate = Dates.todayKey(),
                ),
            )
        }
    }

    fun toggle(todo: Todo) {
        viewModelScope.launch { dao.upsert(stamped(todo, !todo.isDone)) }
    }

    fun update(
        todo: Todo,
        title: String,
        icon: String,
        colorIndex: Int,
        timing: TimingType,
        durationMin: Int,
    ) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            dao.upsert(
                todo.copy(
                    title = clean,
                    icon = icon,
                    colorIndex = colorIndex,
                    timingType = timing.raw,
                    durationMinutes = durationMin.coerceIn(1, 480),
                ),
            )
        }
    }

    fun delete(todo: Todo) {
        // only unfinished todos are removable; finished ones stay as history
        if (todo.isDone) return
        viewModelScope.launch { dao.deleteById(todo.id) }
    }

    fun setDone(todo: Todo, done: Boolean) {
        viewModelScope.launch { dao.upsert(stamped(todo, done)) }
    }

    /**
     * Persists a drag-reordered list of OPEN todos (the only sortable section
     * — finished work stays in completion order). [ids] comes from the local,
     * on-screen order; a stale id set is ignored and left to the next DB emit.
     */
    fun reorder(ids: List<String>) {
        val ordered = Ordering.byId(state.value.undone, ids) { it.id } ?: return
        val ranks = Ordering.rankOrders(ordered.size)
        viewModelScope.launch {
            dao.updateAll(ordered.mapIndexed { index, todo -> todo.copy(sortOrder = ranks[index]) })
        }
    }

    /** Completion always stamps the logical day, reopening clears it. */
    private suspend fun stamped(todo: Todo, done: Boolean): Todo = todo.copy(
        isDone = done,
        doneDate = if (done) Dates.todayKey() else "",
    )
}
