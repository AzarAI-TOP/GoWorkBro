package com.azarai.goworkbro.ui.sleep

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.LogicalDay
import com.azarai.goworkbro.core.SleepOps
import com.azarai.goworkbro.core.SleepStore
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.ui.components.SleepPoint
import java.time.LocalDateTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SleepState(
    /** Total minutes slept on today's logical day (naps included). */
    val todayMinutes: Int = 0,
    /** How many sleeps today's total is made of. */
    val todayCount: Int = 0,
    /** The newest record overall (for the "最近" line). */
    val last: SleepRecord? = null,
    /** Total records kept. */
    val totalRecords: Int = 0,
    val avgMinutes: Int? = null,
    val streakDays: Int = 0,
    /** Last 7 days of totals, oldest first, for the duration chart. */
    val points: List<SleepPoint> = emptyList(),
)

/** 睡眠记录页的状态：一条记录一觉（夜睡午睡同理），统计全部按天合计。 */
class SleepViewModel(app: Application) : AndroidViewModel(app) {

    private val db = Graph.db

    /** Stats read a bounded 370-day window, not the whole table — history
     *  grows forever, the charts/streak/average never need more than that. */
    private val logicalKey = LogicalDay.flow.map { it.dateKey }.distinctUntilChanged()

    private val history = logicalKey.flatMapLatest { day ->
        db.sleepDao().observeSince(SleepOps.historySince(day))
    }

    val state: StateFlow<SleepState> = combine(
        history,
        logicalKey,
    ) { records, today ->
        SleepState(
            todayMinutes = SleepOps.dailyMinutes(records, today),
            todayCount = SleepOps.recordsOfDay(records, today).size,
            last = records.firstOrNull(),
            totalRecords = records.size,
            avgMinutes = SleepOps.averageMinutes(records),
            streakDays = SleepOps.streak(records, today),
            points = SleepOps.lastDays(records, today).map { (label, minutes) ->
                SleepPoint(label = label, hours = if (minutes > 0) minutes / 60f else null)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepState())

    /** A record only ever lands complete — both wheels were confirmed. */
    fun record(sleepAt: LocalDateTime, wakeAt: LocalDateTime) {
        viewModelScope.launch { SleepStore.record(sleepAt, wakeAt) }
    }

    fun deleteLast() {
        viewModelScope.launch { state.value.last?.let { SleepStore.delete(it.id) } }
    }
}
