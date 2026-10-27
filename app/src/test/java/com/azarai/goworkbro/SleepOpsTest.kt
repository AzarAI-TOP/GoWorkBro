package com.azarai.goworkbro

import com.azarai.goworkbro.core.SleepOps
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Per-day totals over the one-row-per-sleep records (naps included). */
class SleepOpsTest {

    private fun rec(id: String, sleep: LocalDateTime, wake: LocalDateTime) =
        SleepRecord(id = id, sleepAt = Dates.isoDateTime(sleep), wakeAt = Dates.isoDateTime(wake))

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi)

    @Test
    fun `records are filed under the calendar day the sleeper woke up`() {
        // last night's sleep counts for the morning it ended on
        assertEquals("2026-09-02", SleepOps.dailyKeyOf(rec("a", at(2026, 9, 1, 23, 30), at(2026, 9, 2, 7, 30))))
        // a 01:30–09:00 night is the same morning
        assertEquals("2026-09-02", SleepOps.dailyKeyOf(rec("b", at(2026, 9, 2, 1, 30), at(2026, 9, 2, 9))))
        // a nap belongs to its own day
        assertEquals("2026-09-02", SleepOps.dailyKeyOf(rec("c", at(2026, 9, 2, 13), at(2026, 9, 2, 14, 30))))
        // midnight is the boundary
        assertEquals("2026-09-02", SleepOps.dailyKeyOf(rec("d", at(2026, 9, 1, 20), at(2026, 9, 2, 0, 0))))
        assertEquals("2026-09-01", SleepOps.dailyKeyOf(rec("e", at(2026, 9, 1, 20), at(2026, 9, 1, 23, 45))))
    }

    @Test
    fun `day window is the calendar day`() {
        val (from, to) = SleepOps.dayWindow("2026-09-02")
        assertEquals("2026-09-02T00:00:00", from)
        assertEquals("2026-09-03T00:00:00", to)
    }

    @Test
    fun `daily totals sum every sleep that ended that day`() {
        val night = rec("night", at(2026, 9, 1, 23), at(2026, 9, 2, 7)) // 8h, woken 09-02
        val nap = rec("nap", at(2026, 9, 2, 13), at(2026, 9, 2, 14, 30)) // 1.5h, 09-02
        val records = listOf(night, nap)
        assertEquals(0, SleepOps.dailyMinutes(records, "2026-09-01"))
        assertEquals(570, SleepOps.dailyMinutes(records, "2026-09-02"))
        assertEquals(0, SleepOps.dailyMinutes(records, "2026-09-03"))
        // newest wake first
        assertEquals(listOf(nap, night), SleepOps.recordsOfDay(records, "2026-09-02"))
    }

    @Test
    fun `streak counts consecutive days whose total met the target`() {
        // each night is filed under its wake day: 09-02, 09-03, 09-04 (5h), 09-05
        fun sleep8h(id: String, d: Int) = rec(id, at(2026, 9, d, 23), at(2026, 9, d + 1, 7))
        fun short(id: String, d: Int) = rec(id, at(2026, 9, d, 23), at(2026, 9, d + 1, 4)) // 5h
        val records = listOf(sleep8h("a", 1), sleep8h("b", 2), short("c", 3), sleep8h("d", 4))
        // met days: 09-02, 09-03, 09-05 — 09-04 totals 5h
        assertEquals(1, SleepOps.streak(records, "2026-09-06"))
        assertEquals(0, SleepOps.streak(records, "2026-09-07")) // yesterday unrecorded breaks the chain
        assertEquals(1, SleepOps.streak(records, "2026-09-05"))
        // 09-04 (5h) breaks the chain, and an unmet today is skipped rather
        // than counted: standing on 09-04 the run is still 09-03 + 09-02
        assertEquals(2, SleepOps.streak(records, "2026-09-04"))
        assertEquals(2, SleepOps.streak(records, "2026-09-03"))
        assertEquals(0, SleepOps.streak(emptyList(), "2026-09-05"))
    }

    @Test
    fun `two naps can carry a day over the target together`() {
        val r1 = rec("a", at(2026, 9, 1, 13), at(2026, 9, 1, 17)) // 4h
        val r2 = rec("b", at(2026, 9, 1, 19), at(2026, 9, 1, 23)) // 4h, same wake day
        val records = listOf(r1, r2)
        assertEquals(480, SleepOps.dailyMinutes(records, "2026-09-01"))
        assertEquals(1, SleepOps.streak(records, "2026-09-02"))
    }

    @Test
    fun `average minutes per sleep`() {
        val r1 = rec("a", at(2026, 9, 1, 23, 30), at(2026, 9, 2, 7)) // 450
        val r2 = rec("b", at(2026, 9, 2, 0, 30), at(2026, 9, 2, 8)) // 450
        assertEquals(450, SleepOps.averageMinutes(listOf(r1, r2)))
        assertEquals(null, SleepOps.averageMinutes(emptyList()))
    }

    @Test
    fun `last days are labelled by their own date`() {
        val points = SleepOps.lastDays(emptyList(), "2026-09-03")
        assertEquals(7, points.size)
        assertEquals("今天", points.last().first)
        assertEquals("9/2", points[5].first)
        val r = rec("x", at(2026, 9, 2, 23), at(2026, 9, 3, 7)) // woken 09-03 = today
        assertEquals(480, SleepOps.lastDays(listOf(r), "2026-09-03").last().second)
        assertEquals(0, SleepOps.lastDays(listOf(r), "2026-09-03")[5].second)
    }

    @Test
    fun `relative date label names today and yesterday`() {
        val now = at(2026, 9, 3, 8)
        assertEquals("今天 · 9月3日", SleepOps.relativeDateLabel(at(2026, 9, 3, 1), now))
        assertEquals("昨天 · 9月2日", SleepOps.relativeDateLabel(at(2026, 9, 2, 23, 30), now))
        assertEquals("前天 · 9月1日", SleepOps.relativeDateLabel(at(2026, 9, 1, 12), now))
        // older than "前天": plain date, no misleading hint
        assertEquals("8月28日", SleepOps.relativeDateLabel(at(2026, 8, 28, 12), now))
    }

    @Test
    fun `duration formatting and wheel rounding`() {
        assertEquals("7h 35m", SleepOps.formatDuration(455))
        assertEquals("0h 45m", SleepOps.formatDuration(45))
        // floor: a computed wheel bound must never land in the future
        assertEquals(at(2026, 9, 1, 23, 30), SleepOps.floorStep(at(2026, 9, 1, 23, 38)))
        assertEquals(at(2026, 9, 1, 23, 30), SleepOps.floorStep(at(2026, 9, 1, 23, 30)))
        assertEquals(at(2026, 9, 1, 23, 45), SleepOps.floorStep(at(2026, 9, 1, 23, 55)))
        assertEquals(at(2026, 9, 2, 0, 0), SleepOps.floorStep(at(2026, 9, 2, 0, 7)))
    }
}
