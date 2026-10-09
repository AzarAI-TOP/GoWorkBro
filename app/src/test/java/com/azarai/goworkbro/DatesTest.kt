package com.azarai.goworkbro

import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DatesTest {

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
    fun `decimal hours wrap past midnight when formatting`() {
        assertEquals("01:30", Dates.formatHours(25.5))
        assertEquals("23:29", Dates.formatHours(23.4833))
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
