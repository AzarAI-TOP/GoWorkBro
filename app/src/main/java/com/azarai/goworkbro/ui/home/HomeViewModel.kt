package com.azarai.goworkbro.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.RoutineKind
import com.azarai.goworkbro.core.RoutineOps
import com.azarai.goworkbro.core.RoutineStore
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
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
    val wakeTime: String? = null,
    val sleepTime: String? = null,
    val focusSessions: Int = 0,
    val focusMinutes: Int = 0,
    /** A wake check-in would need the missed bedtime first. */
    val wakeNeedsSleepPrompt: Boolean = false,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = Graph.db


    /** Logical day key (late-night aware), re-evaluated on every tick. */
    private val logicalKey = LogicalDay.flow.map { it.dateKey }.distinctUntilChanged()

    private val waterToday = logicalKey.flatMapLatest { db.waterDao().observeByDate(it) }
    private val fitnessToday = logicalKey.flatMapLatest { db.fitnessDao().observeByDate(it) }
    private val focusToday = logicalKey.flatMapLatest { db.focusDao().observeDayStats(it) }

    val state: StateFlow<HomeState> = combine(
        combine(
            db.todoDao().observeAll(),
            db.habitDao().observeAll(),
        ) { todos, habits -> todos to habits },
        combine(
            db.routineDao().observeAll(),
            LogicalDay.flow.map { it.start }.distinctUntilChanged(),
        ) { events, start -> events to start },
        combine(waterToday, fitnessToday, focusToday) { w, f, fo -> Triple(w, f, fo) },
        combine(
            Graph.store.observe(Store.Keys.WATER_GOAL_ML),
            Graph.store.observe(Store.Keys.FITNESS_GOAL_MIN),
        ) { w, f -> w to f },
        logicalKey,
    ) { (todos, habits), (routineEvents, dayStart), (waterToday, fitnessToday, focusToday), (waterGoalRaw, fitnessGoalRaw), _ ->
        val now = LocalDateTime.now()
        val wakeToday = RoutineOps.eventOfCurrentDay(routineEvents, RoutineKind.WAKE, dayStart)
        val sleepToday = RoutineOps.eventOfCurrentDay(routineEvents, RoutineKind.SLEEP, dayStart)
        HomeState(
            todoUndone = todos.count { !it.isDone },
            todoTotal = todos.size,
            habitDone = habits.count { it.isCompleted },
            habitTotal = habits.size,
            waterMl = waterToday.sumOf { it.ml },
            waterGoal = waterGoalRaw?.toIntOrNull() ?: Store.DEFAULT_WATER_GOAL,
            fitnessMin = fitnessToday.sumOf { it.minutes },
            fitnessGoal = fitnessGoalRaw?.toIntOrNull() ?: Store.DEFAULT_FITNESS_GOAL,
            wakeTime = wakeToday?.let { Dates.hhmm(Dates.parseDateTime(it.at)) },
            sleepTime = sleepToday?.let { Dates.hhmm(Dates.parseDateTime(it.at)) },
            wakeNeedsSleepPrompt = !RoutineOps.hasPendingSleep(routineEvents),
            focusSessions = focusToday.sessions,
            focusMinutes = focusToday.minutes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun checkInWake(onNeedsSleep: () -> Unit) {
        viewModelScope.launch {
            if (db.routineDao().latest()?.kind == RoutineKind.SLEEP) {
                RoutineStore.checkInWake()
            } else {
                onNeedsSleep()
            }
        }
    }

    fun checkInWakeWithSleep(sleepAt: LocalDateTime) {
        viewModelScope.launch { RoutineStore.checkInWakeWithSleep(sleepAt) }
    }

    fun checkInWakeAllNighter() {
        viewModelScope.launch { RoutineStore.checkInWakeAllNighter() }
    }

    fun checkInSleep() {
        viewModelScope.launch { RoutineStore.checkInSleep() }
    }
}
