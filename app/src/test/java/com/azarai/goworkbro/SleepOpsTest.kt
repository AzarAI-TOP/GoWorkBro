package com.azarai.goworkbro

import com.azarai.goworkbro.core.SleepOps
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Slot attribution + statistics over the one-row-per-night sleep records. */
class SleepOpsTest {

    private fun rec(id: String, sleep: LocalDateTime, wake: LocalDateTime) =
        SleepRecord(id = id, sleepAt = Dates.isoDateTime(sleep), wakeAt = Dates.isoDateTime(wake))

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi)

    @Test
    fun `records are filed by the morning they end on`() {
        // 23:30 -> 07:30 ends on the morning of the 2nd
        assertEquals("2026-09-02", SleepOps.slotKeyOf(rec("a", at(2026, 9, 1, 23, 30), at(2026, 9, 2, 7, 30))))
        // a daytime recovery sleep 07:00 -> 15:00 belongs to the same slot
        assertEquals("2026-09-02", SleepOps.slotKeyOf(rec("b", at(2026, 9, 2, 7), at(2026, 9, 2, 15))))
        // waking before 04:00 still counts for the previous evening's night
        assertEquals("2026-09-01", SleepOps.slotKeyOf(rec("c", at(2026, 9, 1, 21), at(2026, 9, 2, 2))))
        // waking at 04:00 sharp starts a new slot
        assertEquals("2026-09-02", SleepOps.slotKeyOf(rec("d", at(2026, 9, 1, 22), at(2026, 9, 2, 4))))
    }

    @Test
    fun `the earliest sleep represents a slot`() {
        val night = rec("night", at(2026, 9, 1, 23, 30), at(2026, 9, 2, 7, 30))
        val nap = rec("nap", at(2026, 9, 2, 13), at(2026, 9, 2, 14, 30))
        val bySlot = SleepOps.bySlot(listOf(nap, night))
        assertEquals(night, bySlot["2026-09-02"])
    }

    @Test
    fun `streak counts consecutive nights meeting the target and tolerates today`() {
        fun long8h(id: String, d: Int) = rec(id, at(2026, 9, d, 23), at(2026, 9, d + 1, 7)) // slot d+1
        fun short(id: String, d: Int) = rec(id, at(2026, 9, d, 23), at(2026, 9, d + 1, 4)) // 5h, slot d+1
        val records = listOf(long8h("a", 1), long8h("b", 2), short("c", 3), long8h("d", 4))
        // met slots: 09-02, 09-03, 09-05 — 09-04 is 5h
        assertEquals(1, SleepOps.streak(records, "2026-09-05"))
        assertEquals(1, SleepOps.streak(records, "2026-09-06")) // today unrecorded doesn't break the chain
        assertEquals(2, SleepOps.streak(records, "2026-09-04"))
        assertEquals(0, SleepOps.streak(emptyList(), "2026-09-05"))
    }

    @Test
    fun `averages wrap bedtimes past midnight`() {
        val r1 = rec("a", at(2026, 9, 1, 23, 30), at(2026, 9, 2, 7)) // 450 min
        val r2 = rec("b", at(2026, 9, 2, 0, 30), at(2026, 9, 2, 8)) // 450 min
        // 23:30 -> 1410, 00:30 -> 1470 (before noon wraps past 24h), avg 1440 = 00:00
        assertEquals(1440, SleepOps.averageSleepMinutes(listOf(r1, r2)))
        assertEquals(450, SleepOps.averageMinutes(listOf(r1, r2)))
        assertEquals(null, SleepOps.averageMinutes(emptyList()))
    }

    @Test
    fun `last nights are labelled by the evening the night started`() {
        val points = SleepOps.lastNights(emptyList(), "2026-09-03")
        assertEquals(7, points.size)
        assertEquals("今晚", points.last().first)
        // the slot keyed 09-02 started on the evening of 09-01
        assertEquals("9/1", points[5].first)
        val r = rec("x", at(2026, 9, 2, 23), at(2026, 9, 3, 7)) // slot 09-03
        assertEquals(r, SleepOps.lastNights(listOf(r), "2026-09-03").last().second)
    }

    @Test
    fun `duration formatting and wheel rounding`() {
        assertEquals("7h 35m", SleepOps.formatDuration(455))
        assertEquals("0h 45m", SleepOps.formatDuration(45))
        assertEquals(at(2026, 9, 1, 23, 45), SleepOps.roundToStep(at(2026, 9, 1, 23, 38)))
        assertEquals(at(2026, 9, 1, 23, 30), SleepOps.roundToStep(at(2026, 9, 1, 23, 37)))
        assertEquals(at(2026, 9, 1, 23, 30), SleepOps.roundToStep(at(2026, 9, 1, 23, 30)))
        assertEquals(at(2026, 9, 2, 0, 0), SleepOps.roundToStep(at(2026, 9, 1, 23, 55)))
    }
}
