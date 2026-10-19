package com.azarai.goworkbro.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.room.withTransaction
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.db.Countdown
import com.azarai.goworkbro.core.db.FocusLog
import com.azarai.goworkbro.core.db.FitnessLog
import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.db.SettingRow
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.db.WaterLog
import com.azarai.goworkbro.core.util.Dates
import org.json.JSONArray
import org.json.JSONObject

/**
 * One-file JSON backup of the whole app-private database. The user's history
 * is irreplaceable and `allowBackup=false` (by design, zero cloud), so this
 * is the only lifeline against uninstall/ROM-flash/phone-loss — the v3.3.0
 * signature rebuild already forced one full data wipe.
 *
 * Import semantics: REPLACE ALL — every table is cleared, then the file's
 * contents are inserted in one transaction. Missing tables import as empty.
 */
object Backup {

    /** Bump on breaking format changes; imports of other formats are rejected. */
    const val FORMAT = 1

    /** Rows restored per table, for the post-import toast. */
    data class Summary(
        val todos: Int,
        val habits: Int,
        val waterLogs: Int,
        val fitnessLogs: Int,
        val sleepRecords: Int,
        val countdowns: Int,
        val focusLogs: Int,
        val settings: Int,
    ) {
        val total: Int get() = todos + habits + waterLogs + fitnessLogs +
            sleepRecords + countdowns + focusLogs + settings
    }

    suspend fun export(db: AppDatabase): String = JSONObject().apply {
        put("format", FORMAT)
        put("exported_at", Dates.nowIso())
        put("todos", JSONArray(db.todoDao().getAll().map(::todoJson)))
        put("habits", JSONArray(db.habitDao().getAll().map(::habitJson)))
        put("water_logs", JSONArray(db.waterDao().getAll().map(::waterJson)))
        put("fitness_logs", JSONArray(db.fitnessDao().getAll().map(::fitnessJson)))
        put("sleep_records", JSONArray(db.sleepDao().getAll().map(::sleepJson)))
        put("countdowns", JSONArray(db.countdownDao().getAll().map(::countdownJson)))
        put("focus_logs", JSONArray(db.focusDao().getAll().map(::focusJson)))
        put("settings", JSONArray(db.settingsDao().getAll().map(::settingJson)))
    }.toString(2)

    /** null = not a GoWorkBro backup (or a future format) — nothing written. */
    suspend fun import(db: AppDatabase, json: String): Summary? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optInt("format", -1) != FORMAT) return null

        val todos = root.optJSONArray("todos").orEmpty().mapNotNull(::todoOf)
        val habits = root.optJSONArray("habits").orEmpty().mapNotNull(::habitOf)
        val water = root.optJSONArray("water_logs").orEmpty().mapNotNull(::waterOf)
        val fitness = root.optJSONArray("fitness_logs").orEmpty().mapNotNull(::fitnessOf)
        val sleep = root.optJSONArray("sleep_records").orEmpty().mapNotNull(::sleepOf)
        val countdowns = root.optJSONArray("countdowns").orEmpty().mapNotNull(::countdownOf)
        val focus = root.optJSONArray("focus_logs").orEmpty().mapNotNull(::focusOf)
        val settings = root.optJSONArray("settings").orEmpty().mapNotNull(::settingOf)

        db.withTransaction {
            val m = db.maintenanceDao()
            m.clearTodos()
            m.clearHabits()
            m.clearWaterLogs()
            m.clearFitnessLogs()
            m.clearSleepRecords()
            m.clearCountdowns()
            m.clearFocusLogs()
            m.clearSettings()
            db.todoDao().insertAll(todos)
            db.habitDao().insertAll(habits)
            db.waterDao().insertAll(water)
            db.fitnessDao().insertAll(fitness)
            db.sleepDao().insertAll(sleep)
            db.countdownDao().insertAll(countdowns)
            db.focusDao().insertAll(focus)
            db.settingsDao().insertAll(settings)
        }
        return Summary(
            todos = todos.size,
            habits = habits.size,
            waterLogs = water.size,
            fitnessLogs = fitness.size,
            sleepRecords = sleep.size,
            countdowns = countdowns.size,
            focusLogs = focus.size,
            settings = settings.size,
        )
    }

    /**
     * Silent snapshot into PUBLIC Downloads (Download/GoWorkBro/…) — survives
     * even an uninstall-reinstall. Called before every update install; no-op
     * below API 29 (no MediaStore Downloads there; use 设置 → 导出数据 instead).
     * Best-effort: a failed snapshot never blocks the install.
     */
    suspend fun snapshotToDownloads(context: Context): Boolean {
        if (Graph.testing) return false
        if (Build.VERSION.SDK_INT < 29) return false
        val ok = runCatching {
            val json = export(Graph.db)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "GoWorkBro-backup-${Dates.todayKey()}.json")
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/GoWorkBro")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } != null
        }.getOrDefault(false)
        return ok
    }

    /** Suggested file name for the SAF export picker. */
    fun fileName(): String = "GoWorkBro-backup-${Dates.todayKey()}.json"

    // ---- per-table JSON mapping (keys mirror the DB column names) ----

    private fun todoJson(t: Todo) = JSONObject()
        .put("id", t.id).put("title", t.title).put("icon", t.icon)
        .put("color_index", t.colorIndex).put("timing_type", t.timingType)
        .put("duration_minutes", t.durationMinutes).put("is_done", t.isDone)
        .put("done_date", t.doneDate)
        .put("sort_order", t.sortOrder).put("created_date", t.createdDate)

    private fun todoOf(o: JSONObject): Todo? {
        if (o.optString("id").isBlank()) return null
        return Todo(
            id = o.optString("id"),
            title = o.optString("title"),
            icon = o.optString("icon", "scroll"),
            colorIndex = o.optInt("color_index", 0),
            timingType = o.optString("timing_type", "none"),
            durationMinutes = o.optInt("duration_minutes", 25),
            isDone = o.optBoolean("is_done", false),
            // older backups have no done_date: "" = un-dated history
            doneDate = o.optString("done_date"),
            sortOrder = o.optInt("sort_order", 0),
            createdDate = o.optString("created_date"),
        )
    }

    private fun habitJson(h: Habit) = JSONObject()
        .put("id", h.id).put("title", h.title).put("icon", h.icon)
        .put("color_index", h.colorIndex).put("target_count", h.targetCount)
        .put("unit", h.unit).put("sort_order", h.sortOrder)
        .put("created_date", h.createdDate).put("current_count", h.currentCount)
        .put("last_reset_date", h.lastResetDate ?: JSONObject.NULL)

    private fun habitOf(o: JSONObject): Habit? {
        if (o.optString("id").isBlank()) return null
        return Habit(
            id = o.optString("id"),
            title = o.optString("title"),
            icon = o.optString("icon", "sprout"),
            colorIndex = o.optInt("color_index", 1),
            targetCount = o.optInt("target_count", 1),
            unit = o.optString("unit", "次"),
            sortOrder = o.optInt("sort_order", 0),
            createdDate = o.optString("created_date"),
            currentCount = o.optInt("current_count", 0),
            // optString would stringify a JSON null into "null" — check isNull first
            lastResetDate = if (o.isNull("last_reset_date")) null
            else o.optString("last_reset_date").ifBlank { null },
        )
    }

    private fun waterJson(w: WaterLog) = JSONObject()
        .put("id", w.id).put("log_date", w.logDate).put("ml", w.ml).put("logged_at", w.loggedAt)

    private fun waterOf(o: JSONObject): WaterLog? {
        if (o.optString("id").isBlank()) return null
        return WaterLog(
            id = o.optString("id"),
            logDate = o.optString("log_date"),
            ml = o.optInt("ml", 0),
            loggedAt = o.optString("logged_at"),
        )
    }

    private fun fitnessJson(f: FitnessLog) = JSONObject()
        .put("id", f.id).put("log_date", f.logDate).put("minutes", f.minutes).put("logged_at", f.loggedAt)

    private fun fitnessOf(o: JSONObject): FitnessLog? {
        if (o.optString("id").isBlank()) return null
        return FitnessLog(
            id = o.optString("id"),
            logDate = o.optString("log_date"),
            minutes = o.optInt("minutes", 0),
            loggedAt = o.optString("logged_at"),
        )
    }

    private fun sleepJson(s: SleepRecord) = JSONObject()
        .put("id", s.id).put("sleep_at", s.sleepAt).put("wake_at", s.wakeAt)

    private fun sleepOf(o: JSONObject): SleepRecord? {
        if (o.optString("id").isBlank()) return null
        return SleepRecord(
            id = o.optString("id"),
            sleepAt = o.optString("sleep_at"),
            wakeAt = o.optString("wake_at"),
        )
    }

    private fun countdownJson(c: Countdown) = JSONObject()
        .put("id", c.id).put("title", c.title).put("target_at", c.targetAt)
        .put("color_index", c.colorIndex).put("created_at", c.createdAt)

    private fun countdownOf(o: JSONObject): Countdown? {
        if (o.optString("id").isBlank()) return null
        return Countdown(
            id = o.optString("id"),
            title = o.optString("title"),
            targetAt = o.optString("target_at"),
            colorIndex = o.optInt("color_index", 0),
            createdAt = o.optString("created_at"),
        )
    }

    private fun focusJson(f: FocusLog) = JSONObject()
        .put("id", f.id).put("todo_id", f.todoId).put("log_date", f.logDate).put("minutes", f.minutes)

    private fun focusOf(o: JSONObject): FocusLog? {
        if (o.optString("id").isBlank()) return null
        return FocusLog(
            id = o.optString("id"),
            todoId = o.optString("todo_id"),
            logDate = o.optString("log_date"),
            minutes = o.optInt("minutes", 0),
        )
    }

    private fun settingJson(s: SettingRow) = JSONObject().put("key", s.key).put("value", s.value)

    private fun settingOf(o: JSONObject): SettingRow? {
        if (o.optString("key").isBlank()) return null
        return SettingRow(key = o.optString("key"), value = o.optString("value"))
    }

    /** JSONArray? that behaves like an empty one when the key is absent. */
    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private inline fun <T> JSONArray.mapNotNull(f: (JSONObject) -> T?): List<T> =
        (0 until length()).mapNotNull { i -> f(getJSONObject(i)) }
}
