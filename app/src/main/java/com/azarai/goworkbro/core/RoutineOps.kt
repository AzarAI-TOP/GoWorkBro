package com.azarai.goworkbro.core

import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.db.RoutineEvent
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Kind of a routine check-in. */
object RoutineKind {
    const val SLEEP = "sleep"
    const val WAKE = "wake"
}

/**
 * One night's sleep, derived by pairing a sleep check-in with the wake
 * check-in that follows it. Either end may be missing (slept but not up yet,
 * or an all-nighter with no sleep at all).
 */
data class SleepSession(
    /** Date of the morning this night belongs to. */
    val nightKey: String,
    val sleepAt: LocalDateTime?,
    val wakeAt: LocalDateTime?,
    val allNighter: Boolean = false,
) {
    /** Hours slept, or null when the pair is incomplete / it was an all-nighter. */
    val hours: Float?
        get() = if (sleepAt != null && wakeAt != null) {
            java.time.Duration.between(sleepAt, wakeAt).toMinutes() / 60f
        } else {
            null
        }
}

/**
 * Routine check-ins as an event stream plus the pairing/statistics rules.
 *
 * A night is keyed by the date of the morning it ends on. A bedtime is filed
 * by the night it starts (an 01:30 bedtime belongs to the same night as a
 * 23:30 one), so a sleep and the wake that follows it always land on the same
 * [SleepSession.nightKey]. A wake with no bedtime at all (all-nighter) falls
 * back to the logical day it happened on.
 */
object RoutineOps {

    /**
     * The night an instant belongs to: before noon it is the night in progress
     * (still tonight / this morning), from noon the one starting tonight.
     */
    fun nightKeyOf(at: LocalDateTime): String {
        val date: LocalDate = if (at.hour < Dates.SLEEP_ROW_CUTOFF_HOUR) {
            at.toLocalDate()
        } else {
            at.toLocalDate().plusDays(1)
        }
        return Dates.dateKeyOf(date)
    }

    /** The night currently in progress (or just finished). */
    fun currentNightKey(now: LocalDateTime = LocalDateTime.now()): String = nightKeyOf(now)

    /** Pairs the event stream into nights, oldest first. */
    fun pair(events: List<RoutineEvent>): List<SleepSession> {
        val sorted = events.sortedBy { it.at }
        val sessions = mutableListOf<SleepSession>()
        var pending: RoutineEvent? = null
        sorted.forEach { event ->
            when (event.kind) {
                // a repeated sleep check-in corrects the pending one
                RoutineKind.SLEEP -> pending = event
                else -> {
                    val sleep = pending
                    val sleepAt = sleep?.let { Dates.parseDateTime(it.at) }
                    val wakeAt = Dates.parseDateTime(event.at)
                    // the bedtime decides the night; a wake-only event uses its own instant
                    sessions += SleepSession(
                        nightKey = nightKeyOf(sleepAt ?: wakeAt),
                        sleepAt = sleepAt,
                        wakeAt = wakeAt,
                        allNighter = sleep == null && event.noSleep,
                    )
                    pending = null
                }
            }
        }
        pending?.let {
            val sleepAt = Dates.parseDateTime(it.at)
            sessions += SleepSession(
                nightKey = nightKeyOf(sleepAt),
                sleepAt = sleepAt,
                wakeAt = null,
            )
        }
        return sessions
    }

    /** True while a sleep check-in is waiting for its wake-up. */
    fun hasPendingSleep(events: List<RoutineEvent>): Boolean = events.maxByOrNull { it.at }?.kind == RoutineKind.SLEEP

    /** Latest event of [kind] inside the current logical day, if any. */
    fun eventOfCurrentDay(
        events: List<RoutineEvent>,
        kind: String,
        dayStart: LocalDateTime,
    ): RoutineEvent? = events
        .filter { it.kind == kind }
        .map { it to Dates.parseDateTime(it.at) }
        .filter { (_, at) -> !at.isBefore(dayStart) }
        .maxByOrNull { (_, at) -> at }
        ?.first

    /** Consecutive nights (walking back from [currentNight]) holding [kind]. */
    fun streak(sessions: List<SleepSession>, kind: String, currentNight: String): Int {
        val nights = sessions
            .filter { if (kind == RoutineKind.WAKE) it.wakeAt != null else it.sleepAt != null }
            .map { it.nightKey }
            .toSet()
        var cursor = Dates.parseKey(currentNight)
        if (Dates.dateKeyOf(cursor) !in nights) cursor = cursor.minusDays(1) // tonight still open
        var count = 0
        while (Dates.dateKeyOf(cursor) in nights) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /**
     * Average time-of-day of the newest [limit] sessions for [kind], as
     * wrapped minutes since midnight (early-morning times move past 24h so a
     * 23:30 bedtime and a 00:30 bedtime average to midnight).
     */
    fun averageMinutes(sessions: List<SleepSession>, kind: String, limit: Int = 30): Int? {
        val wrapped = sessions
            .sortedByDescending { it.nightKey }
            .take(limit)
            .mapNotNull { session ->
                val at = if (kind == RoutineKind.WAKE) session.wakeAt else session.sleepAt
                at?.let { minutesWrapped(it, if (kind == RoutineKind.WAKE) 4 else 12) }
            }
        return if (wrapped.isEmpty()) null else wrapped.average().toInt()
    }

    private fun minutesWrapped(at: LocalDateTime, wrapBelowHour: Int): Int {
        val hours = at.hour + at.minute / 60.0
        val adjusted = if (hours < wrapBelowHour) hours + 24 else hours
        return Math.round(adjusted * 60).toInt()
    }

    /** Nights for the 7-night charts, oldest first: (label, session). */
    fun lastNights(
        sessions: List<SleepSession>,
        currentNight: String,
        count: Int = 7,
    ): List<Pair<String, SleepSession?>> {
        val byNight = sessions.associateBy { it.nightKey }
        val start = Dates.parseKey(currentNight)
        return (count - 1 downTo 0).map { back ->
            val key = Dates.dateKeyOf(start.minusDays(back.toLong()))
            val label = if (back == 0) "今晚" else {
                val evening = Dates.parseKey(key).minusDays(1) // the night starts the evening before
                "${evening.monthValue}/${evening.dayOfMonth}"
            }
            label to byNight[key]
        }
    }
}

/** Database side of the routine check-ins. */
object RoutineStore {

    private val dao get() = Graph.db.routineDao()

    suspend fun checkInSleep(now: LocalDateTime = LocalDateTime.now()) {
        val pending = dao.latest()?.takeIf { it.kind == RoutineKind.SLEEP }
        if (pending != null) {
            dao.upsert(pending.copy(at = Dates.isoDateTime(now)))
        } else {
            dao.upsert(RoutineEvent(UUID.randomUUID().toString(), RoutineKind.SLEEP, Dates.isoDateTime(now)))
        }
    }

    /** Wake check-in when the sleep that preceded it is already recorded. */
    suspend fun checkInWake(now: LocalDateTime = LocalDateTime.now()) {
        dao.upsert(RoutineEvent(UUID.randomUUID().toString(), RoutineKind.WAKE, Dates.isoDateTime(now)))
    }

    /** Wake check-in plus the missed bedtime the user just told us about. */
    suspend fun checkInWakeWithSleep(sleepAt: LocalDateTime, now: LocalDateTime = LocalDateTime.now()) {
        dao.upsert(RoutineEvent(UUID.randomUUID().toString(), RoutineKind.SLEEP, Dates.isoDateTime(sleepAt)))
        checkInWake(now)
    }

    /** Wake check-in after an all-nighter: nothing to pair with. */
    suspend fun checkInWakeAllNighter(now: LocalDateTime = LocalDateTime.now()) {
        dao.upsert(
            RoutineEvent(
                id = UUID.randomUUID().toString(),
                kind = RoutineKind.WAKE,
                at = Dates.isoDateTime(now),
                noSleep = true,
            ),
        )
    }

    /** Set the check-in time of the current day's event, creating it if needed. */
    suspend fun setTime(kind: String, existing: RoutineEvent?, at: LocalDateTime) {
        if (existing != null) {
            dao.upsert(existing.copy(at = Dates.isoDateTime(at)))
        } else {
            dao.upsert(RoutineEvent(UUID.randomUUID().toString(), kind, Dates.isoDateTime(at)))
        }
    }

    suspend fun clear(id: String) {
        dao.deleteById(id)
    }
}
