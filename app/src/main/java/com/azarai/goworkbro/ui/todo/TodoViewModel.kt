package com.azarai.goworkbro.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.db.TimingType
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.util.Dates
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodoViewModel : ViewModel() {
    private val dao = Graph.db.todoDao()

    val todos = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
