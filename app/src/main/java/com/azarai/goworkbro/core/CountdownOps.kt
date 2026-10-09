package com.azarai.goworkbro.core

import com.azarai.goworkbro.core.db.Countdown
import java.time.Duration
import java.time.LocalDateTime

/** Remaining-time labels over countdown items. */
object CountdownOps {

    /**
     * Detail label: "3 天 05:47" past a day, a live "05:47:33" inside one,
     * "已到时" once past.
     */
    fun remainingLabel(target: LocalDateTime, now: LocalDateTime): String {
        val secs = Duration.between(now, target).seconds
        if (secs <= 0) return "已到时"
        val days = secs / 86_400
        val rem = secs % 86_400
        return if (days > 0) {
            "%d 天 %02d:%02d".format(days, rem / 3_600, rem % 3_600 / 60)
        } else {
            "%02d:%02d:%02d".format(rem / 3_600, rem % 3_600 / 60, rem % 60)
        }
    }

    /** Home-card label: "3 天" or "15:47" (minute precision is enough there). */
    fun shortLabel(target: LocalDateTime, now: LocalDateTime): String {
        val secs = Duration.between(now, target).seconds
        if (secs <= 0) return "已到时"
        val days = secs / 86_400
        return if (days > 0) {
            "${days} 天"
        } else {
            "%02d:%02d".format(secs / 3_600, secs % 3_600 / 60)
        }
    }

    /** The soonest unexpired item, or null. */
    fun nextOf(items: List<Countdown>, now: LocalDateTime): Countdown? =
        items.filter { it.target.isAfter(now) }.minByOrNull { it.target }
}
