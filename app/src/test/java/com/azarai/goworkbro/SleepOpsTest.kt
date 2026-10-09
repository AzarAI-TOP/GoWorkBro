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
    fun `records are filed under the day the sleep started`() {
        // an evening sleep (23:30) belongs to its own evening
        assertEquals("2026-09-01", SleepOps.dailyKeyOf(rec("a", at(2026, 9, 1, 23, 30), at(2026, 9, 2, 7, 30))))
        // a 01:30 bedtime still belongs to the previous evening
        assertEquals("2026-09-01", SleepOps.dailyKeyOf(rec("b", at(2026, 9, 2, 1, 30), at(2026, 9, 2, 9))))
        // a nap on the 2nd belongs to the 2nd
        assertEquals("2026-09-02", SleepOps.dailyKeyOf(rec("c", at(2026, 9, 2, 13), at(2026, 9, 2, 14, 30))))
        // a 04:00 sharp bedtime starts a new day
        assertEquals("2026-09-02", SleepOps.dailyKeyOf(rec("d", at(2026, 9, 2, 4), at(2026, 9, 2, 12))))
    }

    @Test
    fun `daily totals sum every sleep of the day`() {
        val night = rec("night", at(2026, 9, 1, 23), at(2026, 9, 2, 7)) // 8h on 09-01
        val nap = rec("nap", at(2026, 9, 2, 13), at(2026, 9, 2, 14, 30)) // 1.5h on 09-02
        val records = listOf(night, nap)
        assertEquals(480, SleepOps.dailyMinutes(records, "2026-09-01"))
        assertEquals(90, SleepOps.dailyMinutes(records, "2026-09-02"))
        assertEquals(0, SleepOps.dailyMinutes(records, "2026-09-03"))
        assertEquals(listOf(nap), SleepOps.recordsOfDay(records, "2026-09-02"))
    }

    @Test
    fun `streak counts consecutive days whose total met the target`() {
        fun sleep8h(id: String, d: Int) = rec(id, at(2026, 9, d, 23), at(2026, 9, d + 1, 7))
        fun short(id: String, d: Int) = rec(id, at(2026, 9, d, 23), at(2026, 9, d + 1, 4)) // 5h
        val records = listOf(sleep8h("a", 1), sleep8h("b", 2), short("c", 3), sleep8h("d", 4))
        // met days: 09-01, 09-02, 09-04 — 09-03 totals 5h
        assertEquals(1, SleepOps.streak(records, "2026-09-05"))
        assertEquals(0, SleepOps.streak(records, "2026-09-06")) // yesterday unrecorded breaks the chain
        assertEquals(1, SleepOps.streak(records, "2026-09-04")) // 09-03 (5h) breaks it
        assertEquals(2, SleepOps.streak(records, "2026-09-03")) // 09-02 + 09-01
        assertEquals(0, SleepOps.streak(emptyList(), "2026-09-05"))
    }

    @Test
    fun `two naps can carry a day over the target together`() {
        val r1 = rec("a", at(2026, 9, 1, 13), at(2026, 9, 1, 16)) // 3h
        val r2 = rec("b", at(2026, 9, 1, 23), at(2026, 9, 2, 3, 30)) // 4.5h, filed 09-01
        val records = listOf(r1, r2)
        assertEquals(450, SleepOps.dailyMinutes(records, "2026-09-01"))
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
        val r = rec("x", at(2026, 9, 2, 23), at(2026, 9, 3, 7)) // filed 09-02
        assertEquals(480, SleepOps.lastDays(listOf(r), "2026-09-03")[5].second)
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
