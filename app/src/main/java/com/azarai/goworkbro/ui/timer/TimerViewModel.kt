package com.azarai.goworkbro.ui.timer

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.Rollover
import com.azarai.goworkbro.core.db.FocusLog
import com.azarai.goworkbro.core.db.Todo
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A running (or paused) focus session on one todo. */
data class ActiveTimer(
    val todo: Todo,
    val forward: Boolean,
    /** Countdown total, null for forward timing. */
    val durationMs: Long?,
    /** Elapsed ms accumulated across pauses. */
    val accMs: Long = 0,
    /** Wall clock of the current running stretch; null while paused. */
    val startedAt: Long? = null,
) {
    val isRunning: Boolean get() = startedAt != null
}

/**
 * App-wide timer shared by the todo list, the full-screen focus page and
 * the home mini-card. Survives navigation; stops with the process (by
 * design — a focus round is not worth persisting across kills).
 */
class TimerViewModel : ViewModel() {

    val active = MutableStateFlow<ActiveTimer?>(null)

    /** Completed rounds for the active todo (all-time count). */
    val sessions = MutableStateFlow(0)

    /**
     * Ticks ~4x/s only while a round is running; an idle or paused timer emits
     * once per state change and then stays silent.
     */
    private val ticks = active
        .flatMapLatest { a ->
            if (a != null && a.isRunning) {
                flow { while (true) { emit(Unit); delay(250) } }
            } else {
                flow { emit(Unit) }
            }
        }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    /** Current elapsed ms (live while running, frozen while paused). */
    val elapsed: StateFlow<Long> = ticks
        .combine(active) { _, a -> elapsedOf(a) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        // auto-finish countdowns
        viewModelScope.launch {
            ticks.collect {
                val a = active.value ?: return@collect
                if (!a.forward && a.isRunning && (a.durationMs ?: 0) - elapsedOf(a) <= 0) {
                    stop(auto = true)
                }
            }
        }
    }

    private fun elapsedOf(a: ActiveTimer?): Long =
        a?.let { it.accMs + (it.startedAt?.let { s -> (System.currentTimeMillis() - s).coerceAtLeast(0) } ?: 0) } ?: 0

    fun start(todo: Todo) {
        val forward = todo.timing == com.azarai.goworkbro.core.db.TimingType.FORWARD
        active.value = ActiveTimer(
            todo = todo,
            forward = forward,
            durationMs = if (forward) null else todo.durationMinutes * 60_000L,
            startedAt = System.currentTimeMillis(),
        )
        viewModelScope.launch { sessions.value = Graph.db.focusDao().countForTodo(todo.id) }
    }

    fun togglePause() {
        val a = active.value ?: return
        active.value = if (a.isRunning) {
            a.copy(accMs = elapsedOf(a), startedAt = null)
        } else {
            a.copy(startedAt = System.currentTimeMillis())
        }
    }

    /** 提前结束： the round counts as one completed focus (needs >= 1 min). */
    fun stop() = stop(auto = false)

    private fun stop(auto: Boolean) {
        val a = active.value ?: return
        val elapsedMs = elapsedOf(a)
        val elapsedMin = (elapsedMs / 60_000).toInt()
        // a round only counts once a full minute has passed
        val counts = elapsedMs >= 60_000L
        if (counts) {
            viewModelScope.launch {
                Graph.db.focusDao().insert(
                    FocusLog(
                        id = UUID.randomUUID().toString(),
                        todoId = a.todo.id,
                        logDate = Rollover.logicalToday(Graph.store),
                        minutes = maxOf(1, elapsedMin),
                    ),
                )
                sessions.value = Graph.db.focusDao().countForTodo(a.todo.id)
            }
        }
        active.value = null
        Toast.makeText(
            Graph.appContext,
            when {
                !counts -> "不满 1 分钟，未记专注"
                auto -> "时间到！已记 1 次专注"
                else -> "已记 1 次专注"
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** Mark the todo done, then end the round (which counts as a session). */
    fun completeTodo() {
        val a = active.value ?: return
        stop()
        viewModelScope.launch {
            Graph.db.todoDao().upsert(a.todo.copy(isDone = true))
        }
    }
}
