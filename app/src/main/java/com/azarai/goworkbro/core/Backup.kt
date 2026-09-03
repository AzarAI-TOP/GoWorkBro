package com.azarai.goworkbro.core

import android.content.Context
import android.net.Uri
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.db.Countdown
import com.azarai.goworkbro.core.db.FocusSession
import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.db.NewsCache
import com.azarai.goworkbro.core.db.SettingRow
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.util.Dates
import java.util.Base64
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full-data export/import as versioned JSON (v1-compatible read, v2 write).
 * `format_version` 1 = Flutter-era exports; 2 = this app. Replace-all import.
 */
object Backup {
    const val FORMAT = "goworkbro-data-export"
    const val VERSION = 2

    fun suggestedFileName(): String {
        val stamp = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"),
        )
        return "GoWorkBro-$stamp.json"
    }

    suspend fun export(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val json = buildJson()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toString().toByteArray(Charsets.UTF_8))
            } ?: error("cannot open output stream")
            json.length()
        }
    }

    suspend fun buildJson(): JSONObject {
        val db = Graph.db
        val store = Graph.store
        val todos = db.todoDao().getAll()
        val habits = db.habitDao().getAll()
        val sessions = db.focusDao().getAll()
        val countdowns = db.countdownDao().getAll()
        val sleeps = db.sleepDao().getAll()
        val settings = db.settingsDao().getAll()
        val news = db.newsCacheDao().getAll()

        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("format_version", VERSION)
        root.put("database_schema_version", 1)
        root.put("exported_at", Dates.nowIso())

        val tables = JSONObject()
        tables.put("todos", JSONArray(todos.map { todoJson(it) }))
        tables.put("habits", JSONArray(habits.map { habitJson(it) }))
        tables.put("focus_sessions", JSONArray(sessions.map { sessionJson(it) }))
        tables.put("countdowns", JSONArray(countdowns.map { countdownJson(it) }))
        tables.put("sleep_records", JSONArray(sleeps.map { sleepJson(it) }))
        tables.put("user_settings", JSONArray(settings.map { rowJson(it) }))
        tables.put("ustc_news_cache", JSONArray(news.map { newsJson(it) }))
        root.put("tables", tables)

        val prefs = JSONObject()
        prefs.put("custom_habit_units", JSONArray(store.customHabitUnits()))
        root.put("preferences", prefs)

        val assets = JSONObject()
        Graph.avatars.file().takeIf { it.exists() }?.let { file ->
            assets.put("avatar", Base64.getEncoder().encodeToString(file.readBytes()))
        }
        root.put("assets", assets)
        return root
    }

    /** Applies a backup file: wipes and replaces all local data. */
    suspend fun import(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("cannot open input stream")
            applyJson(JSONObject(text))
        }
    }

    suspend fun applyJson(root: JSONObject): String {
        require(root.optString("format") == FORMAT) { "unrecognized_format" }
        val version = root.optInt("format_version", 0)
        require(version in 1..VERSION) { "unsupported_version" }

        val db = Graph.db
        db.withTransaction {
            db.todoDao().deleteAll()
            db.habitDao().deleteAll()
            db.focusDao().deleteAll()
            db.countdownDao().deleteAll()
            db.sleepDao().deleteAll()
            db.settingsDao().deleteAll()
            db.newsCacheDao().deleteAll()

            val tables = root.optJSONObject("tables") ?: JSONObject()
            tables.optJSONArray("todos")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.todoDao().upsert(
                        Todo(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            timingType = o.optString("timing_type", "forward"),
                            durationMinutes = o.optInt("duration_minutes", 25),
                            isCompleted = o.optInt("is_completed", 0) != 0,
                            sortOrder = o.optInt("sort_order", 0),
                            keepTomorrow = o.optInt("keep_tomorrow", 1) != 0,
                            createdDate = o.optString("created_date"),
                            completedDate = if (o.isNull("completed_date")) null else o.optString("completed_date"),
                            actualDurationSeconds = o.optInt("actual_duration_seconds", 0),
                        ),
                    )
                }
            }
            tables.optJSONArray("habits")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.habitDao().upsert(
                        Habit(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            targetCount = o.optInt("target_count", 1),
                            unit = o.optString("unit", "次"),
                            sortOrder = o.optInt("sort_order", 0),
                            createdDate = o.optString("created_date"),
                            currentCount = o.optInt("current_count", 0),
                            lastResetDate = if (o.isNull("last_reset_date")) null else o.optString("last_reset_date"),
                        ),
                    )
                }
            }
            tables.optJSONArray("focus_sessions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.focusDao().upsert(
                        FocusSession(
                            id = o.getString("id"),
                            todoId = if (o.isNull("todo_id")) null else o.optString("todo_id"),
                            sourceType = o.optString("source_type", "todo"),
                            sourceTitle = o.optString("source_title"),
                            startTime = o.optString("start_time"),
                            endTime = o.optString("end_time"),
                            durationSeconds = o.optInt("duration_seconds", 0),
                            sessionDate = o.optString("session_date"),
                        ),
                    )
                }
            }
            tables.optJSONArray("countdowns")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.countdownDao().upsert(
                        Countdown(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            targetDatetime = o.optString("target_datetime"),
                            createdDate = o.optString("created_date"),
                            colorIndex = o.optInt("color_index", 0),
                        ),
                    )
                }
            }
            tables.optJSONArray("sleep_records")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.sleepDao().upsert(
                        SleepRecord(
                            id = o.getString("id"),
                            recordDate = o.getString("record_date"),
                            wakeTime = o.optString("wake_time").ifEmpty { null },
                            sleepTime = o.optString("sleep_time").ifEmpty { null },
                            workoutDurationMinutes = if (o.isNull("workout_duration_minutes")) {
                                null
                            } else {
                                o.optInt("workout_duration_minutes")
                            },
                            note = o.optString("note").ifEmpty { null },
                        ),
                    )
                }
            }
            tables.optJSONArray("user_settings")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.settingsDao().set(
                        SettingRow(o.getString("key"), o.getString("value")),
                    )
                }
            }
            tables.optJSONArray("ustc_news_cache")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.newsCacheDao().upsertAll(
                        listOf(
                            NewsCache(
                                date = o.getString("date"),
                                title = o.optString("title"),
                                markdown = o.optString("markdown"),
                                cachedAt = o.optString("cached_at"),
                            ),
                        ),
                    )
                }
            }
        }

        // Restore the avatar file.
        Graph.avatars.delete()
        val avatarB64 = root.optJSONObject("assets")?.optString("avatar")
        var avatarRestored = false
        if (!avatarB64.isNullOrEmpty()) {
            runCatching {
                Graph.avatars.file().writeBytes(Base64.getDecoder().decode(avatarB64))
                avatarRestored = true
            }
        }

        return "ok:${version}:${avatarRestored}"
    }

    // ---- JSON writers ----

    private fun todoJson(t: Todo) = JSONObject()
        .put("id", t.id)
        .put("title", t.title)
        .put("timing_type", t.timingType)
        .put("duration_minutes", t.durationMinutes)
        .put("is_completed", if (t.isCompleted) 1 else 0)
        .put("sort_order", t.sortOrder)
        .put("keep_tomorrow", if (t.keepTomorrow) 1 else 0)
        .put("created_date", t.createdDate)
        .put(
            "completed_date",
            if (t.completedDate == null) JSONObject.NULL else t.completedDate,
        )
        .put("actual_duration_seconds", t.actualDurationSeconds)

    private fun habitJson(h: Habit) = JSONObject()
        .put("id", h.id)
        .put("title", h.title)
        .put("target_count", h.targetCount)
        .put("unit", h.unit)
        .put("sort_order", h.sortOrder)
        .put("created_date", h.createdDate)
        .put("current_count", h.currentCount)
        .put(
            "last_reset_date",
            if (h.lastResetDate == null) JSONObject.NULL else h.lastResetDate,
        )

    private fun sessionJson(s: FocusSession) = JSONObject()
        .put("id", s.id)
        .put("todo_id", s.todoId ?: JSONObject.NULL)
        .put("source_type", s.sourceType)
        .put("source_title", s.sourceTitle)
        .put("start_time", s.startTime)
        .put("end_time", s.endTime)
        .put("duration_seconds", s.durationSeconds)
        .put("session_date", s.sessionDate)

    private fun countdownJson(c: Countdown) = JSONObject()
        .put("id", c.id)
        .put("title", c.title)
        .put("target_datetime", c.targetDatetime)
        .put("created_date", c.createdDate)
        .put("color_index", c.colorIndex)

    private fun sleepJson(r: SleepRecord) = JSONObject()
        .put("id", r.id)
        .put("record_date", r.recordDate)
        .put("wake_time", r.wakeTime ?: JSONObject.NULL)
        .put("sleep_time", r.sleepTime ?: JSONObject.NULL)
        .put("workout_duration_minutes", r.workoutDurationMinutes ?: JSONObject.NULL)
        .put("note", r.note ?: JSONObject.NULL)

    private fun rowJson(r: SettingRow) = JSONObject()
        .put("key", r.key)
        .put("value", r.value)

    private fun newsJson(n: NewsCache) = JSONObject()
        .put("date", n.date)
        .put("title", n.title)
        .put("markdown", n.markdown)
        .put("cached_at", n.cachedAt)
}
