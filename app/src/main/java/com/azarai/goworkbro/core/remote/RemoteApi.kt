package com.azarai.goworkbro.core.remote

import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.db.Countdown
import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.db.TimingType
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.components.CartoonIcons
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * The PC-facing command surface: one JSON request in, one JSON response out.
 * Transport-agnostic on purpose — [AdbBridge] feeds it today, the Bluetooth
 * GATT pipe will feed it next, and a hermetic test feeds it in CI.
 *
 *   {"v":1, "token":"…", "op":"todo.add", "data":{"title":"写毕设"}}
 *   {"ok":true, "op":"todo.add", "data":{"id":"…", "created":true}}
 *   {"ok":false, "op":"todo.add", "error":"bad_request", "message":"…"}
 *
 * The phone stays the only source of truth: every op is a single mutation
 * through the same DAOs the UI uses, so Room's flows refresh the screens by
 * themselves and the logical-day rules (done_date, habit reset) are applied
 * here exactly as they are for a tap.
 *
 * Field names mirror the backup JSON (`Backup.kt`) so one mental model covers
 * both channels. Nothing here ever clears a table — `Backup.import` remains
 * the only destructive path.
 */
object RemoteApi {

    /** Bumped when an op's shape changes; a newer PC gets "bad_version". */
    const val PROTOCOL = 1

    private const val MAX_TITLE = 80
    private const val MAX_UNIT = 8
    private val ID_PATTERN = Regex("[A-Za-z0-9_-]{4,64}")

    /** A request we refuse; [code] becomes the response's `error` field. */
    private class ApiError(val code: String, override val message: String) : Exception(message)

    suspend fun handle(
        db: AppDatabase,
        request: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val req = runCatching { JSONObject(request) }.getOrNull()
            ?: return fail("", "bad_request", "请求不是合法 JSON")
        val op = req.optString("op")

        // Feature gate, then the shared secret. BLE pairing is "Just Works"
        // and an exported receiver is reachable by any app, so the token —
        // never the link — is what actually authorizes a write.
        val settings = db.settingsDao()
        if (settings.get(Store.Keys.REMOTE_ENABLED) != "true") {
            return fail(op, "disabled", "手机端还没打开「电脑协同」")
        }
        val token = settings.get(Store.Keys.REMOTE_TOKEN)
        if (token.isNullOrBlank() || req.optString("token") != token) {
            return fail(op, "unauthorized", "token 不对：设置 → 电脑协同 → 复制 token")
        }
        if (req.optInt("v", PROTOCOL) > PROTOCOL) {
            return fail(op, "bad_version", "手机端协议是 v$PROTOCOL，请升级 app")
        }

        val data = req.optJSONObject("data") ?: JSONObject()
        val today = Dates.todayKey(now)
        return try {
            val payload = when (op) {
                "ping" -> ping(db, today)

                "todos.list" -> JSONObject()
                    .put("items", JSONArray(db.todoDao().listAll().map(::todoOut)))
                "todo.add" -> writeTodo(db, data, today, now, create = true)
                "todo.update" -> writeTodo(db, data, today, now, create = false)
                "todo.done" -> markTodo(db, data, today)
                "todo.delete" -> deleteTodo(db, data)

                "habits.list" -> JSONObject()
                    .put("items", JSONArray(db.habitDao().listAll().map(::habitOut)))
                "habit.add" -> writeHabit(db, data, today, now, create = true)
                "habit.update" -> writeHabit(db, data, today, now, create = false)
                "habit.check" -> checkHabit(db, data, today)
                "habit.delete" -> deleteHabit(db, data)

                "countdowns.list" -> JSONObject()
                    .put("items", JSONArray(db.countdownDao().listAll().map(::countdownOut)))
                "countdown.add" -> writeCountdown(db, data, now, create = true)
                "countdown.update" -> writeCountdown(db, data, now, create = false)
                "countdown.delete" -> deleteCountdown(db, data)

                else -> throw ApiError("bad_op", "不认识的 op「$op」")
            }
            ok(op, payload)
        } catch (e: ApiError) {
            fail(op, e.code, e.message)
        }
    }

    // ---- todos ----

    private suspend fun writeTodo(
        db: AppDatabase,
        data: JSONObject,
        today: String,
        now: LocalDateTime,
        create: Boolean,
    ): JSONObject {
        val dao = db.todoDao()
        val id = data.id() ?: if (create) UUID.randomUUID().toString() else throw bad("缺 id")
        val existing = dao.byId(id)
        if (existing == null && !create) throw notFound("没有这条待办：$id")
        // Re-adding a known id updates it: a PC retrying after a lost response
        // must not create a second copy.
        val todo = if (existing == null) {
            Todo(
                id = id,
                title = data.title(),
                icon = data.icon("scroll"),
                colorIndex = data.color(0),
                timingType = data.timing("none"),
                durationMinutes = data.duration(25),
                sortOrder = epochSeconds(now),
                createdDate = today,
            )
        } else {
            existing.copy(
                title = data.optTitle(existing.title),
                icon = data.icon(existing.icon),
                colorIndex = data.color(existing.colorIndex),
                timingType = data.timing(existing.timingType),
                durationMinutes = data.duration(existing.durationMinutes),
            )
        }
        dao.upsert(todo)
        return JSONObject().put("id", id).put("created", existing == null)
    }

    private suspend fun markTodo(db: AppDatabase, data: JSONObject, today: String): JSONObject {
        val dao = db.todoDao()
        val id = data.id() ?: throw bad("缺 id")
        val todo = dao.byId(id) ?: throw notFound("没有这条待办：$id")
        val done = data.optBoolean("done", true)
        dao.upsert(todo.copy(isDone = done, doneDate = if (done) today else ""))
        return JSONObject().put("id", id).put("is_done", done).put("done_date", if (done) today else "")
    }

    private suspend fun deleteTodo(db: AppDatabase, data: JSONObject): JSONObject {
        val dao = db.todoDao()
        val id = data.id() ?: throw bad("缺 id")
        if (dao.byId(id) == null) throw notFound("没有这条待办：$id")
        dao.deleteById(id)
        return JSONObject().put("id", id).put("deleted", true)
    }

    // ---- habits ----

    private suspend fun writeHabit(
        db: AppDatabase,
        data: JSONObject,
        today: String,
        now: LocalDateTime,
        create: Boolean,
    ): JSONObject {
        val dao = db.habitDao()
        val id = data.id() ?: if (create) UUID.randomUUID().toString() else throw bad("缺 id")
        val existing = dao.byId(id)
        if (existing == null && !create) throw notFound("没有这个习惯：$id")
        val habit = if (existing == null) {
            Habit(
                id = id,
                title = data.title(),
                icon = data.icon("sprout"),
                colorIndex = data.color(1),
                targetCount = data.target(1),
                unit = data.unit("次"),
                // Same stamp the habits page uses, so a PC-added habit lands
                // at the end of the list like a hand-added one.
                sortOrder = epochSeconds(now),
                createdDate = today,
                currentCount = 0,
                // Today's stamp, or tonight's Rollover would wipe the count.
                lastResetDate = today,
            )
        } else {
            existing.copy(
                title = data.optTitle(existing.title),
                icon = data.icon(existing.icon),
                colorIndex = data.color(existing.colorIndex),
                targetCount = data.target(existing.targetCount),
                unit = if (data.has("unit")) data.unit(existing.unit) else existing.unit,
            )
        }
        dao.upsert(habit)
        return JSONObject().put("id", id).put("created", existing == null)
    }

    private suspend fun checkHabit(db: AppDatabase, data: JSONObject, today: String): JSONObject {
        val dao = db.habitDao()
        val id = data.id() ?: throw bad("缺 id")
        val habit = dao.byId(id) ?: throw notFound("没有这个习惯：$id")
        // No delta given = the phone's own tap: +1, or -1 to undo once complete.
        val delta = if (data.has("delta")) data.optInt("delta", 1)
        else if (habit.isCompleted) -1 else 1
        val next = (habit.currentCount + delta).coerceIn(0, habit.targetCount)
        // last_reset_date must move to today with the count (see Rollover.ensure).
        dao.upsert(habit.copy(currentCount = next, lastResetDate = today))
        return JSONObject()
            .put("id", id)
            .put("current_count", next)
            .put("target_count", habit.targetCount)
            .put("is_completed", next >= habit.targetCount)
    }

    private suspend fun deleteHabit(db: AppDatabase, data: JSONObject): JSONObject {
        val dao = db.habitDao()
        val id = data.id() ?: throw bad("缺 id")
        if (dao.byId(id) == null) throw notFound("没有这个习惯：$id")
        dao.deleteById(id)
        return JSONObject().put("id", id).put("deleted", true)
    }

    // ---- countdowns ----

    private suspend fun writeCountdown(
        db: AppDatabase,
        data: JSONObject,
        now: LocalDateTime,
        create: Boolean,
    ): JSONObject {
        val dao = db.countdownDao()
        val id = data.id() ?: if (create) UUID.randomUUID().toString() else throw bad("缺 id")
        val existing = dao.byId(id)
        if (existing == null && !create) throw notFound("没有这个倒计时：$id")
        val item = if (existing == null) {
            Countdown(
                id = id,
                title = data.title(),
                targetAt = data.targetAt(),
                colorIndex = data.color(0),
                createdAt = Dates.isoDateTime(now),
            )
        } else {
            existing.copy(
                title = data.optTitle(existing.title),
                targetAt = if (data.has("target_at")) data.targetAt() else existing.targetAt,
                colorIndex = data.color(existing.colorIndex),
            )
        }
        dao.upsert(item)
        return JSONObject().put("id", id).put("created", existing == null)
    }

    private suspend fun deleteCountdown(db: AppDatabase, data: JSONObject): JSONObject {
        val dao = db.countdownDao()
        val id = data.id() ?: throw bad("缺 id")
        if (dao.byId(id) == null) throw notFound("没有这个倒计时：$id")
        dao.deleteById(id)
        return JSONObject().put("id", id).put("deleted", true)
    }

    // ---- responses ----

    private suspend fun ping(db: AppDatabase, today: String): JSONObject = JSONObject()
        .put("protocol", PROTOCOL)
        .put("today", today)
        .put("todos", db.todoDao().countAll())
        .put("habits", db.habitDao().countAll())
        .put("countdowns", db.countdownDao().countAll())

    private fun ok(op: String, data: JSONObject): String =
        JSONObject().put("ok", true).put("op", op).put("data", data).toString()

    private fun fail(op: String, error: String, message: String): String =
        JSONObject().put("ok", false).put("op", op).put("error", error).put("message", message).toString()

    // ---- row -> JSON (keys mirror Backup.kt) ----

    private fun todoOut(t: Todo) = JSONObject()
        .put("id", t.id).put("title", t.title).put("icon", t.icon)
        .put("color_index", t.colorIndex).put("timing_type", t.timingType)
        .put("duration_minutes", t.durationMinutes).put("is_done", t.isDone)
        .put("done_date", t.doneDate).put("sort_order", t.sortOrder)
        .put("created_date", t.createdDate)

    private fun habitOut(h: Habit) = JSONObject()
        .put("id", h.id).put("title", h.title).put("icon", h.icon)
        .put("color_index", h.colorIndex).put("target_count", h.targetCount)
        .put("unit", h.unit).put("sort_order", h.sortOrder)
        .put("created_date", h.createdDate).put("current_count", h.currentCount)
        .put("last_reset_date", h.lastResetDate ?: JSONObject.NULL)
        .put("is_completed", h.isCompleted)

    private fun countdownOut(c: Countdown) = JSONObject()
        .put("id", c.id).put("title", c.title).put("target_at", c.targetAt)
        .put("color_index", c.colorIndex).put("created_at", c.createdAt)

    // ---- field validation: reject typos loudly, clamp only what is cosmetic ----

    private fun bad(message: String) = ApiError("bad_request", message)

    private fun notFound(message: String) = ApiError("not_found", message)

    private fun epochSeconds(now: LocalDateTime): Int =
        (now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 1000).toInt()

    private fun JSONObject.id(): String? {
        val id = optString("id").trim()
        if (id.isEmpty()) return null
        if (!ID_PATTERN.matches(id)) throw bad("id 只允许字母、数字、- 和 _（4..64 位）")
        return id
    }

    private fun JSONObject.title(): String {
        val title = optString("title").trim()
        if (title.isEmpty()) throw bad("title 不能为空")
        if (title.length > MAX_TITLE) throw bad("title 太长（上限 $MAX_TITLE 字）")
        return title
    }

    private fun JSONObject.optTitle(fallback: String): String = if (has("title")) title() else fallback

    private fun JSONObject.icon(fallback: String): String {
        if (!has("icon")) return fallback
        val key = optString("icon").trim()
        // CartoonIcons is a UI file, but its `all` list is the only place the
        // 27 icon keys are written down — a second copy here would drift.
        if (CartoonIcons.all.none { it.key == key }) {
            throw bad("未知 icon「$key」，可用：" + CartoonIcons.all.joinToString(" ") { it.key })
        }
        return key
    }

    private fun JSONObject.color(fallback: Int): Int =
        if (has("color_index")) optInt("color_index", fallback).coerceIn(0, 7) else fallback

    private fun JSONObject.timing(fallback: String): String {
        if (!has("timing_type")) return fallback
        val raw = optString("timing_type").trim()
        if (TimingType.fromRaw(raw).raw != raw) throw bad("timing_type 只能是 none / forward / countdown")
        return raw
    }

    private fun JSONObject.duration(fallback: Int): Int =
        if (has("duration_minutes")) optInt("duration_minutes", fallback).coerceIn(1, 480) else fallback

    private fun JSONObject.target(fallback: Int): Int =
        if (has("target_count")) optInt("target_count", fallback).coerceIn(1, 99) else fallback

    private fun JSONObject.unit(fallback: String): String =
        optString("unit", fallback).trim().take(MAX_UNIT).ifBlank { fallback }

    private fun JSONObject.targetAt(): String {
        val raw = optString("target_at").trim()
        val parsed = runCatching { Dates.parseDateTime(raw) }.getOrNull()
            ?: throw bad("target_at 需要本地时间 yyyy-MM-ddTHH:mm(:ss)，收到「$raw」")
        return Dates.isoDateTime(parsed)
    }
}
