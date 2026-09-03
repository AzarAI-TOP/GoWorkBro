package com.azarai.goworkbro.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Timing modes for a todo. Stored as the raw name for v1-export compatibility. */
enum class TimingType(val raw: String) {
    FORWARD("forward"),
    BACKWARD("backward"),
    NONE("none");

    companion object {
        fun fromRaw(value: String?): TimingType =
            entries.firstOrNull { it.raw == value } ?: FORWARD
    }
}

@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "timing_type") val timingType: String = "forward",
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int = 25,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "keep_tomorrow") val keepTomorrow: Boolean = true,
    @ColumnInfo(name = "created_date") val createdDate: String = "",
    @ColumnInfo(name = "completed_date") val completedDate: String? = null,
    @ColumnInfo(name = "actual_duration_seconds") val actualDurationSeconds: Int = 0,
) {
    val timing: TimingType get() = TimingType.fromRaw(timingType)
}

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "target_count") val targetCount: Int = 1,
    val unit: String = "次",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_date") val createdDate: String = "",
    @ColumnInfo(name = "current_count") val currentCount: Int = 0,
    @ColumnInfo(name = "last_reset_date") val lastResetDate: String? = null,
) {
    val isCompleted: Boolean get() = currentCount >= targetCount
}

@Entity(
    tableName = "focus_sessions",
    indices = [Index("session_date")],
)
data class FocusSession(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "todo_id") val todoId: String? = null,
    @ColumnInfo(name = "source_type") val sourceType: String = "todo",
    @ColumnInfo(name = "source_title") val sourceTitle: String = "",
    @ColumnInfo(name = "start_time") val startTime: String = "",
    @ColumnInfo(name = "end_time") val endTime: String = "",
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int = 0,
    @ColumnInfo(name = "session_date") val sessionDate: String = "",
)

@Entity(tableName = "countdowns")
data class Countdown(
    @PrimaryKey val id: String,
    val title: String,
    /** UTC ISO-8601 with Z suffix (v1 semantics). */
    @ColumnInfo(name = "target_datetime") val targetDatetime: String = "",
    @ColumnInfo(name = "created_date") val createdDate: String = "",
    @ColumnInfo(name = "color_index") val colorIndex: Int = 0,
)

@Entity(
    tableName = "sleep_records",
    indices = [Index(value = ["record_date"], unique = true)],
)
data class SleepRecord(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "record_date") val recordDate: String = "",
    @ColumnInfo(name = "wake_time") val wakeTime: String? = null,
    @ColumnInfo(name = "sleep_time") val sleepTime: String? = null,
    @ColumnInfo(name = "workout_duration_minutes") val workoutDurationMinutes: Int? = null,
    val note: String? = null,
)

@Entity(tableName = "user_settings")
data class SettingRow(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "ustc_news_cache")
data class NewsCache(
    @PrimaryKey val date: String,
    val title: String,
    val markdown: String,
    @ColumnInfo(name = "cached_at") val cachedAt: String = "",
)
