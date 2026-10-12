package com.azarai.goworkbro

import com.azarai.goworkbro.core.CountdownOps
import com.azarai.goworkbro.core.db.Countdown
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Boundary cases for the countdown labels (day rollovers, last second). */
class CountdownOpsTest {

    private val now = LocalDateTime.of(2026, 10, 9, 12, 0, 0)

    @Test
    fun `labels at and past the target`() {
        assertEquals("已到时", CountdownOps.remainingLabel(now, now))
        assertEquals("已到时", CountdownOps.remainingLabel(now.minusSeconds(1), now))
        assertEquals("已到时", CountdownOps.shortLabel(now, now))
    }

    @Test
    fun `inside a day the detail label ticks seconds`() {
        assertEquals("00:00:01", CountdownOps.remainingLabel(now.plusSeconds(1), now))
        assertEquals("00:59:59", CountdownOps.remainingLabel(now.plusMinutes(59).plusSeconds(59), now))
        assertEquals(
            "23:59:59",
            CountdownOps.remainingLabel(now.plusHours(23).plusMinutes(59).plusSeconds(59), now),
        )
    }

    @Test
    fun `the home label is minute precise`() {
        assertEquals("00:01", CountdownOps.shortLabel(now.plusSeconds(90), now))
        assertEquals("23:59", CountdownOps.shortLabel(now.plusHours(23).plusMinutes(59).plusSeconds(59), now))
        assertEquals("00:00", CountdownOps.shortLabel(now.plusSeconds(59), now))
    }

    @Test
    fun `past a day shows days and clock`() {
        assertEquals("1 天 00:00", CountdownOps.remainingLabel(now.plusDays(1), now))
        assertEquals(
            "3 天 05:47",
            CountdownOps.remainingLabel(now.plusDays(3).plusHours(5).plusMinutes(47), now),
        )
        assertEquals("3 天", CountdownOps.shortLabel(now.plusDays(3).plusHours(5), now))
    }

    @Test
    fun `nextOf picks the soonest unexpired item`() {
        fun item(id: String, at: LocalDateTime) = Countdown(id, "t", at.toString(), 0, "")
        assertNull(CountdownOps.nextOf(emptyList(), now))
        assertNull(CountdownOps.nextOf(listOf(item("a", now.minusMinutes(1))), now))
        val items = listOf(
            item("late", now.plusDays(2)),
            item("soon", now.plusHours(1)),
            item("past", now.minusHours(1)),
        )
        assertEquals("soon", CountdownOps.nextOf(items, now)!!.id)
    }
}
