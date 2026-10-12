package com.azarai.goworkbro.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.CountdownOps
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.SleepOps
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.Updater
import com.azarai.goworkbro.core.db.Countdown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the home cards need. */
data class HomeState(
    val todoUndone: Int = 0,
    val todoTotal: Int = 0,
    val habitDone: Int = 0,
    val habitTotal: Int = 0,
    val waterMl: Int = 0,
    val waterGoal: Int = Store.DEFAULT_WATER_GOAL,
    val fitnessMin: Int = 0,
    val fitnessGoal: Int = Store.DEFAULT_FITNESS_GOAL,
    /** Duration of today's total sleep, e.g. "7h 5m". */
    val todaySleepText: String? = null,
    /** Countdown items, for the home tile's live label. */
    val countdowns: List<Countdown> = emptyList(),
    val focusSessions: Int = 0,
    val focusMinutes: Int = 0,
    /** A downloaded update APK waiting for its install tap. */
    val pendingUpdate: String? = null,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = Graph.db

    /** Logical day key (late-night aware), re-evaluated on every tick. */
    private val logicalKey = LogicalDay.flow.map { it.dateKey }.distinctUntilChanged()

    private val waterToday = logicalKey.flatMapLatest { db.waterDao().observeByDate(it) }
    private val fitnessToday = logicalKey.flatMapLatest { db.fitnessDao().observeByDate(it) }
    private val focusToday = logicalKey.flatMapLatest { db.focusDao().observeDayStats(it) }

    /** Today's sleeps via the fixed-04:00 filing window — NOT the full table
     *  (home must stay O(today), history grows forever). */
    private val sleepToday = logicalKey.flatMapLatest { day ->
        val (from, to) = SleepOps.dayWindow(day)
        db.sleepDao().observeBetween(from, to)
    }

    /** Version dismissed from the banner this session (install reminder). */
    private val dismissedUpdate = MutableStateFlow<String?>(null)

    private val pendingUpdate = combine(
        Updater.pendingInstallVersion,
        dismissedUpdate,
    ) { pending, dismissed -> if (pending != null && pending != dismissed) pending else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<HomeState> = combine(
        combine(
            db.todoDao().observeCounts(),
            db.habitDao().observeCounts(),
        ) { todos, habits -> todos to habits },
        combine(
            sleepToday,
            db.countdownDao().observeAll(),
        ) { sleep, countdowns -> sleep to countdowns },
        combine(waterToday, fitnessToday, focusToday) { w, f, fo -> Triple(w, f, fo) },
        combine(
            Graph.store.observe(Store.Keys.WATER_GOAL_ML),
            Graph.store.observe(Store.Keys.FITNESS_GOAL_MIN),
        ) { w, f -> w to f },
        combine(logicalKey, pendingUpdate) { key, pending -> key to pending },
    ) { (todos, habits), (sleepToday, countdowns), (waterToday, fitnessToday, focusToday), (waterGoalRaw, fitnessGoalRaw), (logicalKey, pendingUpdate) ->
        HomeState(
            todoUndone = todos.undone,
            todoTotal = todos.total,
            habitDone = habits.done,
            habitTotal = habits.total,
            waterMl = waterToday.sumOf { it.ml },
            waterGoal = waterGoalRaw?.toIntOrNull() ?: Store.DEFAULT_WATER_GOAL,
            fitnessMin = fitnessToday.sumOf { it.minutes },
            fitnessGoal = fitnessGoalRaw?.toIntOrNull() ?: Store.DEFAULT_FITNESS_GOAL,
            todaySleepText = sleepToday.sumOf { it.minutes }
                .takeIf { it > 0 }?.let { SleepOps.formatDuration(it) },
            countdowns = countdowns,
            focusSessions = focusToday.sessions,
            focusMinutes = focusToday.minutes,
            pendingUpdate = pendingUpdate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun dismissUpdate() {
        dismissedUpdate.value = Updater.pendingInstallVersion.value
    }

    fun installUpdate() {
        viewModelScope.launch { Updater.startInstall(Graph.appContext) }
    }
}
