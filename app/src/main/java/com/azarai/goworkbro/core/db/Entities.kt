package com.azarai.goworkbro.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Timing modes for a todo. */
enum class TimingType(val raw: String, val label: String) {
    FORWARD("forward", "正"),
    COUNTDOWN("countdown", "倒"),
    NONE("none", "");

    companion object {
        fun fromRaw(value: String?): TimingType = entries.firstOrNull { it.raw == value } ?: NONE
    }
}

/** A todo item. Checked state persists until the user removes it. */
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey val id: String,
    val title: String,
    /** Key into CartoonIcons (see ui/components/CartoonIcon.kt). */
    val icon: String = "scroll",
    /** Index into the pastel palette used for the icon circle background. */
    @ColumnInfo(name = "color_index") val colorIndex: Int = 0,
    /** none | forward | countdown */
    @ColumnInfo(name = "timing_type") val timingType: String = "none",
    /** Countdown duration in minutes (ignored otherwise). */
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int = 25,
    @ColumnInfo(name = "is_done") val isDone: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_date") val createdDate: String = "",
) {
    val timing: TimingType get() = TimingType.fromRaw(timingType)
}

/** A habit with a daily counter that the rollover engine resets. */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey val id: String,
    val title: String,
    val icon: String = "sprout",
    @ColumnInfo(name = "color_index") val colorIndex: Int = 1,
    @ColumnInfo(name = "target_count") val targetCount: Int = 1,
    val unit: String = "次",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_date") val createdDate: String = "",
    @ColumnInfo(name = "current_count") val currentCount: Int = 0,
    @ColumnInfo(name = "last_reset_date") val lastResetDate: String? = null,
) {
    val isCompleted: Boolean get() = currentCount >= targetCount
}

/**
 * One wake or sleep check-in, stamped with its absolute local time. Sleep
 * sessions are derived by pairing a sleep with the next wake
 * (see [com.azarai.goworkbro.core.RoutineOps.pair]).
 */
@Entity(tableName = "routine_events", indices = [Index("at"), Index("kind")])
data class RoutineEvent(
    @PrimaryKey val id: String,
    /** "sleep" or "wake". */
    val kind: String,
    /** ISO local datetime of the check-in. */
    @ColumnInfo(name = "at") val at: String,
    /** True when this wake followed an all-nighter (nothing to pair with). */
    @ColumnInfo(name = "no_sleep") val noSleep: Boolean = false,
)

/** One drink of water. */
@Entity(tableName = "water_logs", indices = [Index("log_date")])
data class WaterLog(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "log_date") val logDate: String = "",
    val ml: Int,
    /** `HH:mm` for display only. */
    @ColumnInfo(name = "logged_at") val loggedAt: String = "",
)

/** One bout of exercise, in minutes. */
@Entity(tableName = "fitness_logs", indices = [Index("log_date")])
data class FitnessLog(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "log_date") val logDate: String = "",
    val minutes: Int,
    @ColumnInfo(name = "logged_at") val loggedAt: String = "",
)

/** One finished focus round for a todo (>= 1 minute). */
@Entity(tableName = "focus_logs", indices = [Index("todo_id")])
data class FocusLog(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "todo_id") val todoId: String,
    @ColumnInfo(name = "log_date") val logDate: String,
    val minutes: Int,
)

@Entity(tableName = "user_settings")
data class SettingRow(
    @PrimaryKey val key: String,
    val value: String,
)
