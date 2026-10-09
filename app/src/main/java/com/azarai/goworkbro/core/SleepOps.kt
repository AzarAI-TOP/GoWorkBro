package com.azarai.goworkbro.core

import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import java.util.UUID

/**
 * Statistics over the one-row-per-sleep records.
 *
 * v3.3 semantics: a record is just A SLEEP — a nap and a night are the same
 * kind of thing, and a day can hold any number of them. A record is filed
 * under the logical day it STARTED on (a bedtime between 00:00 and 04:00
 * still belongs to the previous evening); all stats are per-day TOTALS.
 */
object SleepOps {

    /** Days whose total meets this count as 达标. */
    const val TARGET_MINUTES = 7 * 60

    /** The logical day a record is filed under (see class doc). */
    fun dailyKeyOf(record: SleepRecord): String {
        val sleep = record.sleep
        val day = if (sleep.hour < Dates.LATE_NIGHT_BOUNDARY_HOUR) {
            sleep.toLocalDate().minusDays(1)
        } else {
            sleep.toLocalDate()
        }
        return Dates.dateKeyOf(day)
    }

    /** Total minutes slept on [day] (all records that started on it). */
    fun dailyMinutes(records: List<SleepRecord>, day: String): Int =
        records.filter { dailyKeyOf(it) == day }.sumOf { it.minutes }

    /** The records of [day], newest first. */
    fun recordsOfDay(records: List<SleepRecord>, day: String): List<SleepRecord> =
        records.filter { dailyKeyOf(it) == day }.sortedByDescending { it.sleep }

    /**
     * Consecutive days (walking back from [today]) whose TOTAL met the 7h
     * target. A today without any record yet is skipped, not counted — an
     * open day must not break yesterday's chain.
     */
    fun streak(records: List<SleepRecord>, today: String): Int {
        val met = records.groupBy { dailyKeyOf(it) }
            .mapValues { (_, rs) -> rs.sumOf { it.minutes } }
            .filterValues { it >= TARGET_MINUTES }
            .keys
        var cursor = Dates.parseKey(today)
        if (Dates.dateKeyOf(cursor) !in met) cursor = cursor.minusDays(1)
        var count = 0
        while (Dates.dateKeyOf(cursor) in met) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** Average duration per sleep of the newest [limit] records, in minutes. */
    fun averageMinutes(records: List<SleepRecord>, limit: Int = 30): Int? {
        val mins = records.sortedByDescending { it.wake }.take(limit).map { it.minutes }
        return if (mins.isEmpty()) null else mins.average().toInt()
    }

    /** Totals for the 7-day chart, oldest first: (label, minutes). */
    fun lastDays(
        records: List<SleepRecord>,
        today: String,
        count: Int = 7,
    ): List<Pair<String, Int>> {
        val start = Dates.parseKey(today)
        return (count - 1 downTo 0).map { back ->
            val day = start.minusDays(back.toLong())
            val label = if (back == 0) "今天" else "${day.monthValue}/${day.dayOfMonth}"
            label to dailyMinutes(records, Dates.dateKeyOf(day))
        }
    }

    /** "7h 35m" — minute-precise duration label used across the sleep UI. */
    fun formatDuration(minutes: Int): String = "${minutes / 60}h ${minutes % 60}m"

    /** "M月d日" for the light date labels under the record wheels. */
    fun dateLabel(at: LocalDateTime): String = "${at.monthValue}月${at.dayOfMonth}日"

    /**
     * Round DOWN to a 15-minute step. Down on purpose: computed wheel bounds
     * and defaults must never land in the future.
     */
    fun floorStep(at: LocalDateTime, stepMinutes: Int = 15): LocalDateTime {
        val clean = at.withSecond(0).withNano(0)
        return clean.minusMinutes((clean.minute % stepMinutes).toLong())
    }
}

/** Database side of the sleep records. */
object SleepStore {

    private val dao get() = Graph.db.sleepDao()

    /** Write one complete sleep. Nothing is stored before both ends exist. */
    suspend fun record(sleepAt: LocalDateTime, wakeAt: LocalDateTime) {
        dao.insert(
            SleepRecord(
                id = UUID.randomUUID().toString(),
                sleepAt = Dates.isoDateTime(sleepAt),
                wakeAt = Dates.isoDateTime(wakeAt),
            ),
        )
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
