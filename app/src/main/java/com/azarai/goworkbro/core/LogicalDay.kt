package com.azarai.goworkbro.core

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
 * re-emits on [notifyResumed] (app foreground) and again by itself the moment
 * midnight passes — so screens no longer thread a refresh counter around and
 * rollovers happen even if the app stays open across 00:00.
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
    fun current(): LogicalDayInfo = compute()

    private fun compute(): LogicalDayInfo {
        val dateKey = Dates.todayKey()
        val day = Dates.parseKey(dateKey)
        return LogicalDayInfo(
            dateKey = dateKey,
            start = day.atStartOfDay(),
            nextBoundary = day.plusDays(1).atStartOfDay(),
        )
    }
}
