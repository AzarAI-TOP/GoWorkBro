package com.azarai.goworkbro.ui.sleep

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.SleepOps
import com.azarai.goworkbro.core.SleepStore
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.components.SleepPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SleepState(
    /** The record filed under today's slot, if the night is already in. */
    val today: SleepRecord? = null,
    /** The newest record overall (for the "上次" line). */
    val last: SleepRecord? = null,
    val avgMinutes: Int? = null,
    val avgSleepLabel: String = "--:--",
    val streakDays: Int = 0,
    /** Last 7 night slots, oldest first, for the duration chart. */
    val points: List<SleepPoint> = emptyList(),
)

/** 睡眠记录页的状态：一条记录一行，全部统计直接派生。 */
class SleepViewModel(app: Application) : AndroidViewModel(app) {

    val state: StateFlow<SleepState> = combine(
        Graph.db.sleepDao().observeAll(),
        LogicalDay.flow.map { it.dateKey }.distinctUntilChanged(),
    ) { records, todaySlot ->
        SleepState(
            today = SleepOps.forSlot(records, todaySlot),
            last = records.firstOrNull(),
            avgMinutes = SleepOps.averageMinutes(records),
            avgSleepLabel = SleepOps.averageSleepMinutes(records)
                ?.let { Dates.formatHours(it / 60.0) } ?: "--:--",
            streakDays = SleepOps.streak(records, todaySlot),
            points = SleepOps.lastNights(records, todaySlot).map { (label, r) ->
                SleepPoint(label = label, hours = r?.let { it.minutes / 60f })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepState())

    /** A record only ever lands complete — both wheels were confirmed. */
    fun record(sleepAt: java.time.LocalDateTime, wakeAt: java.time.LocalDateTime) {
        viewModelScope.launch { SleepStore.record(sleepAt, wakeAt) }
    }

    fun deleteLast() {
        viewModelScope.launch { state.value.last?.let { SleepStore.delete(it.id) } }
    }
}
