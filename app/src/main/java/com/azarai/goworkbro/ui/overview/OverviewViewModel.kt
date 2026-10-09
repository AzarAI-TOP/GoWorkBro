package com.azarai.goworkbro.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.SleepOps
import com.azarai.goworkbro.ui.components.PieSlice
import com.azarai.goworkbro.ui.components.SleepPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class OverviewState(
    val totalSessions: Int = 0,
    val totalMinutes: Int = 0,
    val todaySessions: Int = 0,
    val todayMinutes: Int = 0,
    val pie: List<PieSlice> = emptyList(),
    val sleep: List<SleepPoint> = emptyList(),
)

class OverviewViewModel : ViewModel() {

    private val db = Graph.db


    private val logicalKey = LogicalDay.flow.map { it.dateKey }.distinctUntilChanged()

    private val todayStats = logicalKey.flatMapLatest { db.focusDao().observeDayStats(it) }
    private val todayByTodo = logicalKey.flatMapLatest { db.focusDao().observeDayByTodo(it) }

    val state: StateFlow<OverviewState> = combine(
        combine(
            db.focusDao().observeTotalStats(),
            todayStats,
            todayByTodo,
            db.todoDao().observeAll(),
        ) { total, day, byTodo, todos ->
            val byId = todos.associateBy { it.id }
            val pie = byTodo
                .map { row ->
                    val todo = byId[row.todoId]
                    PieSlice(
                        label = todo?.title ?: "已删除的任务",
                        minutes = row.minutes,
                        colorIndex = todo?.colorIndex ?: 0,
                    )
                }
                .sortedByDescending { it.minutes }
            Triple(total, day, pie)
        },
        combine(
            db.sleepDao().observeAll(),
            logicalKey,
        ) { records, todaySlot -> SleepOps.lastNights(records, todaySlot) },
    ) { (total, day, pie), nights ->
        OverviewState(
            totalSessions = total.sessions,
            totalMinutes = total.minutes,
            todaySessions = day.sessions,
            todayMinutes = day.minutes,
            pie = pie,
            sleep = nights.map { (label, r) ->
                SleepPoint(label = label, hours = r?.let { it.minutes / 60f })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewState())
}
