package com.azarai.goworkbro.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.db.TimingType
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.util.Dates
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the todo page renders: active work + a capped slice of history. */
data class TodoListState(
    val undone: List<Todo> = emptyList(),
    val done: List<Todo> = emptyList(),
    /** Total finished todos ever — drives the 展开更多 row. */
    val doneTotal: Int = 0,
)

class TodoViewModel : ViewModel() {
    private val dao = Graph.db.todoDao()

    /** Done history renders capped — the page stays O(active work), not
     *  O(all time); expandDone() lifts the cap for this session. */
    private val doneLimit = MutableStateFlow(50)

    val state: StateFlow<TodoListState> = combine(
        dao.observeUndone(),
        doneLimit.flatMapLatest { dao.observeDone(it) },
        dao.observeDoneCount(),
    ) { undone, done, doneTotal ->
        TodoListState(undone, done, doneTotal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoListState())

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
        viewModelScope.launch { dao.upsert(todo.copy(isDone = !todo.isDone)) }
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
        viewModelScope.launch { dao.upsert(todo.copy(isDone = done)) }
    }
}
