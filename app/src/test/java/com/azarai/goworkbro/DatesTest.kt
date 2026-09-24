package com.azarai.goworkbro

import com.azarai.goworkbro.core.RoutineKind
import com.azarai.goworkbro.core.RoutineOps
import com.azarai.goworkbro.core.db.RoutineEvent
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DatesTest {

    private fun event(kind: String, at: LocalDateTime, noSleep: Boolean = false) =
        RoutineEvent(id = "$kind-$at", kind = kind, at = Dates.isoDateTime(at), noSleep = noSleep)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi)

    @Test
    fun `logical date carries over before 4am in late-night mode`() {
        val key = Dates.logicalDateKey(at(2026, 9, 1, 1), lateNightModeEnabled = true, lastRolloverDate = "2026-08-31")
        assertEquals("2026-08-31", key)
    }

    @Test
    fun `logical date does not carry over when already rolled`() {
        val key = Dates.logicalDateKey(at(2026, 9, 1, 1), lateNightModeEnabled = true, lastRolloverDate = "2026-09-01")
        assertEquals("2026-09-01", key)
    }

    @Test
    fun `late-night mode disabled keeps calendar date`() {
        val key = Dates.logicalDateKey(at(2026, 9, 1, 2), lateNightModeEnabled = false, lastRolloverDate = "2026-08-31")
        assertEquals("2026-09-01", key)
    }

    @Test
    fun `boundary at 4am sharp stops carrying over`() {
        val key = Dates.logicalDateKey(at(2026, 9, 1, 4), lateNightModeEnabled = true, lastRolloverDate = "2026-08-31")
        assertEquals("2026-09-01", key)
    }

    @Test
    fun `instants are filed by the night they belong to`() {
        // a 23:00 bedtime, a 01:30 bedtime and a 07:30 wake all end the same morning
        assertEquals("2026-09-02", RoutineOps.nightKeyOf(at(2026, 9, 1, 23)))
        assertEquals("2026-09-02", RoutineOps.nightKeyOf(at(2026, 9, 2, 1, 30)))
        assertEquals("2026-09-02", RoutineOps.nightKeyOf(at(2026, 9, 2, 7, 30)))
        // from noon onward it is the night starting tonight
        assertEquals("2026-09-03", RoutineOps.nightKeyOf(at(2026, 9, 2, 13, 0)))
        assertEquals("2026-09-03", RoutineOps.nightKeyOf(at(2026, 9, 2, 23, 30)))
    }

    @Test
    fun `sleep sessions pair a sleep with the following wake`() {
        val events = listOf(
            event(RoutineKind.SLEEP, at(2026, 9, 1, 23, 30)),
            event(RoutineKind.WAKE, at(2026, 9, 2, 7, 30)),
            event(RoutineKind.SLEEP, at(2026, 9, 2, 23, 0)),
        )
        val sessions = RoutineOps.pair(events)
        assertEquals(2, sessions.size)
        assertEquals(8f, sessions[0].hours!!, 1e-4f)
        assertEquals("2026-09-02", sessions[0].nightKey)
        assertEquals("2026-09-03", sessions[1].nightKey)
        assertEquals(null, sessions[1].hours) // still sleeping
        assertEquals(at(2026, 9, 2, 23, 0), sessions[1].sleepAt)
    }

    @Test
    fun `an all-nighter has no sleep to pair with`() {
        val events = listOf(event(RoutineKind.WAKE, at(2026, 9, 2, 7, 0), noSleep = true))
        val sessions = RoutineOps.pair(events)
        assertEquals(1, sessions.size)
        assertEquals(true, sessions[0].allNighter)
        assertEquals(null, sessions[0].hours)
    }

    @Test
    fun `a wake without a recorded sleep is not flagged as an all-nighter`() {
        val events = listOf(event(RoutineKind.WAKE, at(2026, 9, 2, 7, 0)))
        assertEquals(false, RoutineOps.pair(events).single().allNighter)
    }

    @Test
    fun `streak counts consecutive nights and tolerates an open night`() {
        val events = listOf(
            event(RoutineKind.SLEEP, at(2026, 9, 1, 23, 0)),
            event(RoutineKind.WAKE, at(2026, 9, 2, 7, 0)),
            event(RoutineKind.SLEEP, at(2026, 9, 2, 23, 0)),
            event(RoutineKind.WAKE, at(2026, 9, 3, 7, 0)),
        )
        val sessions = RoutineOps.pair(events)
        // 09-03 has a wake, 09-02 has a wake, 09-01 none -> 2
        assertEquals(2, RoutineOps.streak(sessions, RoutineKind.WAKE, "2026-09-03"))
        // the current night is still open, so the chain is not broken
        assertEquals(2, RoutineOps.streak(sessions, RoutineKind.WAKE, "2026-09-04"))
    }

    @Test
    fun `average bedtime wraps past midnight`() {
        val events = listOf(
            event(RoutineKind.SLEEP, at(2026, 9, 1, 23, 30)),
            event(RoutineKind.WAKE, at(2026, 9, 2, 7, 0)),
            event(RoutineKind.SLEEP, at(2026, 9, 3, 0, 30)),
            event(RoutineKind.WAKE, at(2026, 9, 3, 7, 0)),
        )
        // 23:30 -> 1410, 00:30 -> 1470, average 1440 = 00:00
        assertEquals(1440, RoutineOps.averageMinutes(RoutineOps.pair(events), RoutineKind.SLEEP))
        assertEquals("00:00", Dates.formatHours(24.0))
    }

    @Test
    fun `last nights are labelled by the evening the night started`() {
        val points = RoutineOps.lastNights(emptyList(), "2026-09-03")
        assertEquals(7, points.size)
        assertEquals("今晚", points.last().first)
        // the night keyed 09-02 started on the evening of 09-01
        assertEquals("9/1", points[points.size - 2].first)
    }

    @Test
    fun `decimal hours wrap past midnight when formatting`() {
        assertEquals("01:30", Dates.formatHours(25.5))
        assertEquals("23:29", Dates.formatHours(23.4833))
    }

    @Test
    fun `sleep hours handles a normal and a wrapping night`() {
        assertEquals(8f, Dates.sleepHours("23:30", "07:30")!!, 1e-4f)
        assertEquals(7.5f, Dates.sleepHours("01:00", "08:30")!!, 1e-4f)
        assertEquals(null, Dates.sleepHours(null, "07:30"))
    }

    @Test
    fun `duration wording keeps one decimal above an hour`() {
        assertEquals("45 分钟", Dates.formatMinutesHuman(45))
        assertEquals("1 小时", Dates.formatMinutesHuman(60))
        assertEquals("1.5 小时", Dates.formatMinutesHuman(90))
        assertEquals("45" to "分钟", Dates.minutesParts(45))
    }

    @Test
    fun `hours from time parses hh mm`() {
        assertEquals(23.5, Dates.hoursFromTime("23:30")!!, 1e-6)
        assertEquals(null, Dates.hoursFromTime("broken"))
    }
}
