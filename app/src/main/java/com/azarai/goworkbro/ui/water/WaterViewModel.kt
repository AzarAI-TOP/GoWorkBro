package com.azarai.goworkbro.ui.water

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.Rollover
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.WaterLog
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

data class WaterState(
    val todayTotal: Int = 0,
    val goal: Int = Store.DEFAULT_WATER_GOAL,
    val cups: List<Int> = Store.DEFAULT_WATER_CUPS,
    val todayLogs: List<WaterLog> = emptyList(),
    val week: List<Pair<String, Int>> = emptyList(),
)

class WaterViewModel(app: Application) : AndroidViewModel(app) {

    private val db = Graph.db


    private val logicalKey = LogicalDay.flow.map { it.dateKey }.distinctUntilChanged()

    private val logsToday = logicalKey.flatMapLatest { db.waterDao().observeByDate(it) }

    private val weekFlow = combine(logicalKey, logsToday) { key, _ -> key }.flatMapLatest { key ->
        flow {
            val today = Dates.parseKey(key)
            val from = Dates.dateKeyOf(today.minusDays(6))
            val to = Dates.dateKeyOf(today)
            val totals = db.waterDao().dailyTotals(from, to).associate { it.logDate to it.total }
            emit(Dates.weekSeries(today, totals))
        }
    }

    val state: StateFlow<WaterState> = combine(
        logsToday,
        Graph.store.observe(Store.Keys.WATER_GOAL_ML),
        Graph.store.observe(Store.Keys.WATER_CUPS),
        weekFlow,
    ) { logs, goalRaw, cupsRaw, week ->
        WaterState(
            todayTotal = logs.sumOf { it.ml },
            goal = goalRaw?.toIntOrNull() ?: Store.DEFAULT_WATER_GOAL,
            cups = Store.parseWaterCups(cupsRaw),
            todayLogs = logs,
            week = week,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaterState())

    fun drink(ml: Int) {
        if (ml <= 0) return
        viewModelScope.launch {
            val key = Rollover.logicalToday(Graph.store)
            db.waterDao().insert(
                WaterLog(
                    id = UUID.randomUUID().toString(),
                    logDate = key,
                    ml = ml,
                    loggedAt = Dates.hhmm(LocalDateTime.now()),
                ),
            )
        }
    }

    fun delete(log: WaterLog) {
        viewModelScope.launch { db.waterDao().deleteById(log.id) }
    }

    fun setGoal(ml: Int) {
        viewModelScope.launch { Graph.store.set(Store.Keys.WATER_GOAL_ML, ml.coerceIn(200, 10000).toString()) }
    }

    fun updateCup(index: Int, ml: Int) {
        viewModelScope.launch {
            val cups = Graph.store.waterCups().toMutableList()
            if (index in cups.indices && ml in 50..3000) {
                cups[index] = ml
                Graph.store.setWaterCups(cups)
            }
        }
    }

    fun removeCup(index: Int) {
        viewModelScope.launch {
            val cups = Graph.store.waterCups().toMutableList()
            if (cups.size > 1 && index in cups.indices) {
                cups.removeAt(index)
                Graph.store.setWaterCups(cups)
            }
        }
    }
}
