package com.azarai.goworkbro.ui.fitness

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.Rollover
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.FitnessLog
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import java.util.UUID
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

data class FitnessState(
    val todayTotal: Int = 0,
    val goal: Int = Store.DEFAULT_FITNESS_GOAL,
    val todayLogs: List<FitnessLog> = emptyList(),
    val week: List<Pair<String, Int>> = emptyList(),
)

class FitnessViewModel(app: Application) : AndroidViewModel(app) {

    private val db = Graph.db


    private val logicalKey = LogicalDay.flow.map { it.dateKey }.distinctUntilChanged()

    private val logsToday = logicalKey.flatMapLatest { db.fitnessDao().observeByDate(it) }

    private val weekFlow = combine(logicalKey, logsToday) { key, _ -> key }.flatMapLatest { key ->
        flow {
            val today = Dates.parseKey(key)
            val from = Dates.dateKeyOf(today.minusDays(6))
            val to = Dates.dateKeyOf(today)
            val totals = db.fitnessDao().dailyTotals(from, to).associate { it.logDate to it.total }
            emit(Dates.weekSeries(today, totals))
        }
    }

    val state: StateFlow<FitnessState> = combine(
        logsToday,
        Graph.store.observe(Store.Keys.FITNESS_GOAL_MIN),
        weekFlow,
    ) { logs, goalRaw, week ->
        FitnessState(
            todayTotal = logs.sumOf { it.minutes },
            goal = goalRaw?.toIntOrNull() ?: Store.DEFAULT_FITNESS_GOAL,
            todayLogs = logs,
            week = week,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FitnessState())

    fun log(minutes: Int) {
        if (minutes <= 0) return
        viewModelScope.launch {
            db.fitnessDao().insert(
                FitnessLog(
                    id = UUID.randomUUID().toString(),
                    logDate = Rollover.logicalToday(Graph.store),
                    minutes = minutes,
                    loggedAt = Dates.hhmm(LocalDateTime.now()),
                ),
            )
        }
    }

    fun delete(log: FitnessLog) {
        viewModelScope.launch { db.fitnessDao().deleteById(log.id) }
    }

    fun setGoal(minutes: Int) {
        viewModelScope.launch {
            Graph.store.set(Store.Keys.FITNESS_GOAL_MIN, minutes.coerceIn(5, 600).toString())
        }
    }
}
