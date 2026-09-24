package com.azarai.goworkbro.ui.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.RoutineKind
import com.azarai.goworkbro.core.RoutineOps
import com.azarai.goworkbro.core.RoutineStore
import com.azarai.goworkbro.core.SleepSession
import com.azarai.goworkbro.core.db.RoutineEvent
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.components.ChartPoint
import java.time.LocalDateTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineState(
    val isWake: Boolean = true,
    val currentTime: String? = null,
    val currentEvent: RoutineEvent? = null,
    val streak: Int = 0,
    val avgLabel: String = "--:--",
    val avgMinutes: Int? = null,
    val totalDays: Int = 0,
    /** Last 7 nights, oldest first, for the line chart. */
    val points: List<ChartPoint> = emptyList(),
)

/** 起床 / 睡觉打卡页的状态：事件流 → 配对会话 → 统计与折线图。 */
class RoutineViewModel(app: Application, private val isWake: Boolean) : AndroidViewModel(app) {

    private val kind = if (isWake) RoutineKind.WAKE else RoutineKind.SLEEP

    private val sessions: StateFlow<List<SleepSession>> = combine(
        Graph.db.routineDao().observeAll(),
        LogicalDay.flow.map { it.dateKey }.distinctUntilChanged(),
    ) { events, _ -> RoutineOps.pair(events) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<RoutineState> = combine(
        Graph.db.routineDao().observeAll(),
        sessions,
        LogicalDay.flow.map { it.start }.distinctUntilChanged(),
    ) { events, paired, dayStart ->
        val currentNight = RoutineOps.currentNightKey()
        val current = RoutineOps.eventOfCurrentDay(events, kind, dayStart)
        val currentSession = paired.firstOrNull { it.nightKey == currentNight }
        val timeText = if (isWake) currentSession?.wakeAt else currentSession?.sleepAt

        RoutineState(
            isWake = isWake,
            currentTime = timeText?.let { Dates.hhmm(it) },
            currentEvent = current,
            streak = RoutineOps.streak(paired, kind, currentNight),
            avgLabel = RoutineOps.averageMinutes(paired, kind)
                ?.let { Dates.formatHours(it / 60.0) } ?: "--:--",
            avgMinutes = RoutineOps.averageMinutes(paired, kind),
            totalDays = paired.count { if (isWake) it.wakeAt != null else it.sleepAt != null },
            points = RoutineOps.lastNights(paired, currentNight).map { (label, session) ->
                val at = if (isWake) session?.wakeAt else session?.sleepAt
                ChartPoint(
                    label = label,
                    minutes = at?.let { it.hour * 60 + it.minute + if (it.hour < (if (isWake) 4 else 12)) 24 * 60 else 0 },
                    isCurrent = label == "今晚",
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineState(isWake = isWake))

    /** Check in now. Wake check-ins may need the missed bedtime first. */
    fun checkIn(onNeedsSleep: () -> Unit) {
        viewModelScope.launch {
            if (isWake) {
                val events = Graph.db.routineDao().latest()
                if (events?.kind == RoutineKind.SLEEP) RoutineStore.checkInWake() else onNeedsSleep()
            } else {
                RoutineStore.checkInSleep()
            }
        }
    }

    /** 起床打卡 completed with the bedtime the user supplied. */
    fun checkInWakeWithSleep(sleepAt: LocalDateTime) {
        viewModelScope.launch { RoutineStore.checkInWakeWithSleep(sleepAt) }
    }

    fun checkInWakeAllNighter() {
        viewModelScope.launch { RoutineStore.checkInWakeAllNighter() }
    }

    /**
     * Set (or backfill) the current day's check-in time. A picked clock time
     * earlier than the day window belongs to the following morning.
     */
    fun setTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val info = LogicalDay.current()
            val at = info.start.toLocalDate().atTime(hour, minute).let {
                if (it.isBefore(info.start)) it.plusDays(1) else it
            }
            val existing = RoutineOps.eventOfCurrentDay(
                Graph.db.routineDao().getAll(),
                kind,
                info.start,
            )
            RoutineStore.setTime(kind, existing, at)
        }
    }

    fun clearCurrentDay() {
        viewModelScope.launch {
            val info = LogicalDay.current()
            RoutineOps.eventOfCurrentDay(Graph.db.routineDao().getAll(), kind, info.start)
                ?.let { RoutineStore.clear(it.id) }
        }
    }
}
