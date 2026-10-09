package com.azarai.goworkbro.core

import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import java.util.UUID

/**
 * Statistics over the one-row-per-night sleep records.
 *
 * A record belongs to the slot of the morning it ends on: a wake before 04:00
 * still counts for the previous evening's night, everything else is filed by
 * its wake date. A daytime recovery sleep (07:00 -> 15:00) therefore shares
 * the slot with the all-nighter it recovers from — the night it belongs to.
 */
object SleepOps {

    /** Nights meeting this count as 达标. */
    const val TARGET_MINUTES = 7 * 60

    /** The night slot a record resolves to (see class doc). */
    fun slotKeyOf(record: SleepRecord): String {
        val wake = record.wake
        val day = if (wake.hour < Dates.LATE_NIGHT_BOUNDARY_HOUR) {
            wake.toLocalDate().minusDays(1)
        } else {
            wake.toLocalDate()
        }
        return Dates.dateKeyOf(day)
    }

    /**
     * One representative record per slot: the earliest sleep wins, so a nap
     * recorded after the main night does not shadow it.
     */
    fun bySlot(records: List<SleepRecord>): Map<String, SleepRecord> =
        records.groupBy { slotKeyOf(it) }.mapValues { (_, rs) -> rs.minBy { it.sleep } }

    /** The record filed under [slot], if any. */
    fun forSlot(records: List<SleepRecord>, slot: String): SleepRecord? =
        bySlot(records)[slot]

    /**
     * Consecutive slots (walking back from [todaySlot]) whose record met the
     * 7h target. A today without a record yet is skipped, not counted — an
     * open night must not break yesterday's chain.
     */
    fun streak(records: List<SleepRecord>, todaySlot: String): Int {
        val met = bySlot(records).filterValues { it.minutes >= TARGET_MINUTES }.keys
        var cursor = Dates.parseKey(todaySlot)
        if (Dates.dateKeyOf(cursor) !in met) cursor = cursor.minusDays(1)
        var count = 0
        while (Dates.dateKeyOf(cursor) in met) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** Average sleep duration of the newest [limit] records, in minutes. */
    fun averageMinutes(records: List<SleepRecord>, limit: Int = 30): Int? {
        val mins = records.sortedByDescending { it.wake }.take(limit).map { it.minutes }
        return if (mins.isEmpty()) null else mins.average().toInt()
    }

    /**
     * Average time-of-day of falling asleep, wrapped past midnight (a 23:30
     * bedtime and a 00:30 one average to midnight), as minutes since 00:00
     * plus 24h — feed it through [Dates.formatHours] for display.
     */
    fun averageSleepMinutes(records: List<SleepRecord>, limit: Int = 30): Int? {
        val wrapped = records.sortedByDescending { it.wake }.take(limit).map { r ->
            val m = r.sleep.hour * 60 + r.sleep.minute
            if (m < Dates.SLEEP_ROW_CUTOFF_HOUR * 60) m + 24 * 60 else m
        }
        return if (wrapped.isEmpty()) null else wrapped.average().toInt()
    }

    /** Nights for the 7-slot chart, oldest first: (label, record). */
    fun lastNights(
        records: List<SleepRecord>,
        todaySlot: String,
        count: Int = 7,
    ): List<Pair<String, SleepRecord?>> {
        val slots = bySlot(records)
        val start = Dates.parseKey(todaySlot)
        return (count - 1 downTo 0).map { back ->
            val key = Dates.dateKeyOf(start.minusDays(back.toLong()))
            val label = if (back == 0) {
                "今晚"
            } else {
                val evening = Dates.parseKey(key).minusDays(1) // the night starts the evening before
                "${evening.monthValue}/${evening.dayOfMonth}"
            }
            label to slots[key]
        }
    }

    /** "7h 35m" — minute-precise duration label used across the sleep UI. */
    fun formatDuration(minutes: Int): String = "${minutes / 60}h ${minutes % 60}m"

    /** "M月d日" for the light date labels under the record wheels. */
    fun dateLabel(at: LocalDateTime): String = "${at.monthValue}月${at.dayOfMonth}日"

    /** Nearest 15-minute step (the wheel granularity). */
    fun roundToStep(at: LocalDateTime, stepMinutes: Int = 15): LocalDateTime {
        val clean = at.withSecond(0).withNano(0)
        val remainder = clean.minute % stepMinutes
        return if (remainder * 2 >= stepMinutes) {
            clean.plusMinutes((stepMinutes - remainder).toLong())
        } else {
            clean.minusMinutes(remainder.toLong())
        }
    }
}

/** Database side of the sleep records. */
object SleepStore {

    private val dao get() = Graph.db.sleepDao()

    /** Write one complete night. Nothing is stored before both ends exist. */
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
