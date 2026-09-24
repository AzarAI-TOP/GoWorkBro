package com.azarai.goworkbro.core

import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.util.Dates
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn

/** The logical day everything is bucketed by, plus its window. */
data class LogicalDayInfo(
    val dateKey: String,
    /** First instant belonging to this logical day. */
    val start: LocalDateTime,
    /** When the next logical day begins. */
    val nextBoundary: LocalDateTime,
)

/**
 * Single source of truth for "which day is it". Emits the current logical day,
 * re-emits on [notifyResumed] (app foreground, late-night setting changed) and
 * again by itself the moment the day boundary passes — so screens no longer
 * thread a refresh counter around and rollovers happen even if the app stays
 * open across 04:00.
 */
object LogicalDay {

    private val requests = MutableStateFlow(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val flow: Flow<LogicalDayInfo> = requests
        .flatMapLatest {
            flow {
                while (true) {
                    val info = compute()
                    emit(info)
                    delay(
                        Duration.between(LocalDateTime.now(), info.nextBoundary)
                            .toMillis()
                            .coerceAtLeast(1_000L),
                    )
                }
            }
        }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    fun notifyResumed() {
        requests.value++
    }

    /** One-shot read for non-reactive callers. */
    suspend fun current(): LogicalDayInfo = compute()

    private suspend fun compute(): LogicalDayInfo {
        val store = Graph.store
        val lateNight = store.get(Store.Keys.LATE_NIGHT_MODE) != "false"
        val dateKey = Rollover.logicalToday(store)
        val day = Dates.parseKey(dateKey)
        val start = if (lateNight) {
            day.atTime(Dates.LATE_NIGHT_BOUNDARY_HOUR, 0)
        } else {
            day.atStartOfDay()
        }
        val next = if (lateNight) {
            day.plusDays(1).atTime(Dates.LATE_NIGHT_BOUNDARY_HOUR, 0)
        } else {
            day.plusDays(1).atStartOfDay()
        }
        return LogicalDayInfo(dateKey, start, next)
    }
}
