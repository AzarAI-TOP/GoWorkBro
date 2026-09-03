package com.azarai.goworkbro.core.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Date helpers. All date keys are local-time `yyyy-MM-dd` strings. */
object Dates {
    const val LATE_NIGHT_BOUNDARY_HOUR = 4
    const val SLEEP_ROW_CUTOFF_HOUR = 12

    private val KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun dateKeyOf(date: LocalDate): String = date.format(KEY)

    fun dateKeyOf(dateTime: LocalDateTime): String = dateTime.toLocalDate().format(KEY)

    fun todayKey(clock: LocalDateTime = LocalDateTime.now()): String = dateKeyOf(clock)

    fun parseKey(key: String): LocalDate = LocalDate.parse(key)

    /**
     * The date bucket used by daily app data. In late-night mode, activity
     * between midnight and 4 AM stays on the previous day. The boundary is
     * fixed in time — a day that has already rolled over never moves back.
     */
    fun logicalDateKey(
        now: LocalDateTime,
        lateNightModeEnabled: Boolean,
        lastRolloverDate: String,
    ): String {
        val calendarDate = dateKeyOf(now)
        val canCarryOver = lateNightModeEnabled &&
            now.hour < LATE_NIGHT_BOUNDARY_HOUR &&
            lastRolloverDate != calendarDate
        return if (canCarryOver) dateKeyOf(now.minusDays(1)) else calendarDate
    }

    /**
     * Date row used by a sleep check-in: before noon belongs to the current
     * calendar row, from noon onward to the following row. This keeps sleep
     * and wake times paired. Independent of the late-night boundary.
     */
    fun sleepRecordDateKey(
        now: LocalDateTime,
        cutoffHour: Int = SLEEP_ROW_CUTOFF_HOUR,
    ): String = dateKeyOf(if (now.hour < cutoffHour) now else now.plusDays(1))

    /** A wake-up check-in belongs to its wall-clock date. */
    fun wakeRecordDateKey(now: LocalDateTime): String = dateKeyOf(now)

    /**
     * Resolves a time-only check-in to its most recent local occurrence: a
     * selected clock time later than now refers to yesterday. Lets the user
     * backfill last night's sleep without pre-closing the next day.
     */
    fun resolveCheckInDateTime(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
        var resolved = now.toLocalDate().atTime(hour, minute)
        if (resolved.isAfter(now)) resolved = resolved.minusDays(1)
        return resolved
    }

    /** Parses `HH:mm` into decimal hours (e.g. "23:29" -> 23.4833…). */
    fun hoursFromTime(value: String?): Double? {
        if (value == null) return null
        val parts = value.split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h + m / 60.0
    }

    /** Decimal hours -> "HH:mm" (values >= 24 wrap, e.g. 25.5 -> "01:30"). */
    fun formatHours(value: Double): String {
        val normalized = if (value >= 24) value - 24 else value
        val totalMinutes = Math.round(normalized * 60).toInt()
        return "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
    }

    /** Formats decimal hours (e.g. 7.5) as "7h 30m". */
    fun formatDurationHours(value: Double): String {
        val totalMinutes = Math.round(value * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** Formats seconds as `HH:MM:SS` or `MM:SS`. */
    fun formatSeconds(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun nowIso(): String = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())

    /** Parses a stored ISO local datetime; tolerates legacy ISO-8601 with 'Z'. */
    fun parseIso(value: String): LocalDateTime = try {
        LocalDateTime.parse(value)
    } catch (_: Exception) {
        runCatching { LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault()) }
            .getOrDefault(LocalDateTime.now())
    }

    fun durationLabel(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun minutesBetween(start: LocalDateTime, end: LocalDateTime): Long =
        Duration.between(start, end).toMinutes()
}
