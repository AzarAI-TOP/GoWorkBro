package com.azarai.goworkbro.core

import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Statistics over the one-row-per-sleep records.
 *
 * A record is just A SLEEP — a nap and a night are the same kind of thing,
 * and a day can hold any number of them. A record is filed under the calendar
 * day it ENDED on, so the morning after a 23:00–07:00 night the day's total
 * already reads 8h instead of nothing; all stats are per-day TOTALS.
 */
object SleepOps {

    /** Days whose total meets this count as "达标" (target met). */
    const val TARGET_MINUTES = 7 * 60

    /** The day a record is filed under: the calendar day the sleeper woke up. */
    fun dailyKeyOf(record: SleepRecord): String = Dates.dateKeyOf(record.wake)

    /** Total minutes slept on [day] (all records that started on it). */
    fun dailyMinutes(records: List<SleepRecord>, day: String): Int =
        records.filter { dailyKeyOf(it) == day }.sumOf { it.minutes }

    /** ISO `[start, end)` WAKE-time window capturing exactly the records
     *  [dailyKeyOf] files under [day] — the SQL twin of [dailyMinutes]
     *  (queries must filter `wake_at`, not `sleep_at`). */
    fun dayWindow(day: String): Pair<String, String> {
        val d = Dates.parseKey(day)
        return Dates.isoDateTime(d.atStartOfDay()) to
            Dates.isoDateTime(d.plusDays(1).atStartOfDay())
    }

    /** ISO lower WAKE-time bound covering [HISTORY_DAYS] days of history — caps
     *  the stats page at a bounded, evergreen record set. */
    const val HISTORY_DAYS = 370

    fun historySince(day: String): String =
        dayWindow(Dates.dateKeyOf(Dates.parseKey(day).minusDays(HISTORY_DAYS.toLong()))).first

    /** The records of [day], newest first. */
    fun recordsOfDay(records: List<SleepRecord>, day: String): List<SleepRecord> =
        records.filter { dailyKeyOf(it) == day }.sortedByDescending { it.wake }

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
     * Same label with a "今天 / 昨天 / 前天" prefix, so the sleep wheel says
     * *which* day the user just scrolled to instead of forcing date math.
     */
    fun relativeDateLabel(at: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
        val days = ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate())
        val head = when (days) {
            0L -> "今天"
            1L -> "昨天"
            2L -> "前天"
            else -> null
        }
        return if (head == null) dateLabel(at) else "$head · ${dateLabel(at)}"
    }

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
