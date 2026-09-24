package com.azarai.goworkbro.core.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Date helpers. All date keys are local-time `yyyy-MM-dd` strings. */
object Dates {
    const val LATE_NIGHT_BOUNDARY_HOUR = 4
    const val SLEEP_ROW_CUTOFF_HOUR = 12

    private val KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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

    /** Formats seconds as `HH:MM:SS` or `MM:SS`. */
    fun formatSeconds(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** Value/unit pair for durations: 45 -> ("45", "分钟"), 90 -> ("1.5", "小时"). */
    fun minutesParts(minutes: Int): Pair<String, String> {
        if (minutes < 60) return minutes.toString() to "分钟"
        val hours = Math.round(minutes / 6.0) / 10.0
        val value = if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()
        return value to "小时"
    }

    /** "45 分钟" under an hour, otherwise hours with one decimal ("1.5 小时"). */
    fun formatMinutesHuman(minutes: Int): String {
        val (value, unit) = minutesParts(minutes)
        return "$value $unit"
    }

    fun nowIso(): String = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())

    fun isoDateTime(value: LocalDateTime): String =
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value)

    fun parseDateTime(value: String): LocalDateTime = LocalDateTime.parse(value)

    /**
     * Last 7 days, oldest first, as (label, total) pairs for the week charts.
     * [totals] is keyed by date; missing days count as zero.
     */
    fun weekSeries(today: LocalDate, totals: Map<String, Int>): List<Pair<String, Int>> =
        (0..6).map { back ->
            val day = today.minusDays((6 - back).toLong())
            val label = if (back == 6) "今天" else "${day.monthValue}/${day.dayOfMonth}"
            label to (totals[dateKeyOf(day)] ?: 0)
        }

    /** The `HH:mm` format used by every stored time column. */
    fun hhmm(dateTime: LocalDateTime): String = dateTime.format(HHMM)

    fun hhmm(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    /** Hours between a sleep time and a wake time (wrap-aware), or null. */
    fun sleepHours(sleepTime: String?, wakeTime: String?): Float? {
        val sleep = hoursFromTime(sleepTime) ?: return null
        val wake = hoursFromTime(wakeTime) ?: return null
        val adjustedWake = if (wake <= sleep) wake + 24 else wake
        return (adjustedWake - sleep).toFloat()
    }
}
