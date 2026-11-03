package com.azarai.goworkbro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.db.SettingRow
import com.azarai.goworkbro.core.remote.RemoteApi
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The PC command surface, exercised without a radio: op in, op out. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = GoWorkBroApp::class)
class RemoteApiTest {

    private lateinit var db: AppDatabase
    private val token = "0123456789abcdef"
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 5, 14, 30)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun enable(enabled: Boolean = true, token: String = this.token) = runBlocking {
        db.settingsDao().set(SettingRow(Store.Keys.REMOTE_ENABLED, enabled.toString()))
        db.settingsDao().set(SettingRow(Store.Keys.REMOTE_TOKEN, token))
    }

    private fun call(
        op: String,
        data: JSONObject = JSONObject(),
        tok: String = token,
        at: LocalDateTime = now,
    ): JSONObject = JSONObject(
        runBlocking {
            RemoteApi.handle(
                db,
                JSONObject().put("v", 1).put("op", op).put("token", tok).put("data", data).toString(),
                at,
            )
        },
    )

    // ---- the gate ----

    @Test
    fun disabledUntilTheToggleIsOn() {
        val response = call("ping")
        assertFalse(response.getBoolean("ok"))
        assertEquals("disabled", response.getString("error"))
    }

    @Test
    fun wrongTokenIsRejected() {
        enable()
        val response = call("ping", tok = "not-the-token")
        assertFalse(response.getBoolean("ok"))
        assertEquals("unauthorized", response.getString("error"))
    }

    @Test
    fun pingCountsTheTables() {
        enable()
        call("todo.add", JSONObject().put("title", "写毕设"))
        val data = call("ping").getJSONObject("data")
        assertEquals(RemoteApi.PROTOCOL, data.getInt("protocol"))
        assertEquals("2026-11-05", data.getString("today"))
        assertEquals(1, data.getInt("todos"))
        assertEquals(0, data.getInt("habits"))
    }

    // ---- todos ----

    @Test
    fun addTodoStampsTheLogicalDay() {
        enable()
        val response = call("todo.add", JSONObject().put("title", "  写毕设  "))
        val id = response.getJSONObject("data").getString("id")
        assertTrue(response.getJSONObject("data").getBoolean("created"))

        val todo = runBlocking { db.todoDao().byId(id) }!!
        assertEquals("写毕设", todo.title)
        assertEquals("", todo.doneDate)
        assertEquals("2026-11-05", todo.createdDate)
        assertEquals("none", todo.timingType)
        assertEquals("scroll", todo.icon)
        // open todos sort DESC by this stamp: epoch seconds, like the UI does
        assertTrue(todo.sortOrder > 0)
    }

    @Test
    fun addTodoTakesAnExplicitIdSoRetriesAreIdempotent() {
        enable()
        val data = JSONObject().put("id", "pc-abc-1").put("title", "写毕设")
        val first = call("todo.add", data).getJSONObject("data")
        val second = call("todo.add", data).getJSONObject("data")
        assertEquals("pc-abc-1", first.getString("id"))
        assertFalse(second.getBoolean("created"))
        assertEquals(1, runBlocking { db.todoDao().countAll() })
    }

    @Test
    fun addTodoRejectsAnUnknownIcon() {
        enable()
        val response = call("todo.add", JSONObject().put("title", "x").put("icon", "rocket"))
        assertFalse(response.getBoolean("ok"))
        assertEquals("bad_request", response.getString("error"))
        assertTrue(response.getString("message").contains("rocket"))
        assertEquals(0, runBlocking { db.todoDao().countAll() })
    }

    @Test
    fun addTodoRejectsAnEmptyTitle() {
        enable()
        assertEquals("bad_request", call("todo.add", JSONObject().put("title", "   ")).getString("error"))
    }

    @Test
    fun doneStampsTodayAndUndoneClearsIt() {
        enable()
        val id = call("todo.add", JSONObject().put("title", "写毕设")).getJSONObject("data").getString("id")

        call("todo.done", JSONObject().put("id", id))
        val done = runBlocking { db.todoDao().byId(id) }!!
        assertTrue(done.isDone)
        assertEquals("2026-11-05", done.doneDate)

        call("todo.done", JSONObject().put("id", id).put("done", false))
        val undone = runBlocking { db.todoDao().byId(id) }!!
        assertFalse(undone.isDone)
        assertEquals("", undone.doneDate)
    }

    @Test
    fun updateKeepsTheFieldsThatWereNotSent() {
        enable()
        val id = call(
            "todo.add",
            JSONObject().put("title", "写毕设").put("icon", "book").put("color_index", 3),
        ).getJSONObject("data").getString("id")

        call("todo.update", JSONObject().put("id", id).put("title", "改标题"))
        val todo = runBlocking { db.todoDao().byId(id) }!!
        assertEquals("改标题", todo.title)
        assertEquals("book", todo.icon)
        assertEquals(3, todo.colorIndex)
    }

    @Test
    fun updateOfAMissingTodoIsNotFound() {
        enable()
        assertEquals("not_found", call("todo.update", JSONObject().put("id", "nope-1")).getString("error"))
    }

    @Test
    fun deleteRemovesTheRow() {
        enable()
        val id = call("todo.add", JSONObject().put("title", "写毕设")).getJSONObject("data").getString("id")
        assertTrue(call("todo.delete", JSONObject().put("id", id)).getBoolean("ok"))
        assertNull(runBlocking { db.todoDao().byId(id) })
    }

    @Test
    fun listReturnsTheTodoPageOrder() {
        enable()
        call("todo.add", JSONObject().put("title", "旧"), at = now.minusMinutes(1))
        call("todo.add", JSONObject().put("title", "新"))
        val items = call("todos.list").getJSONObject("data").getJSONArray("items")
        assertEquals(2, items.length())
        assertEquals("新", items.getJSONObject(0).getString("title"))
    }

    // ---- habits ----

    @Test
    fun habitAddStampsTodaySoRolloverLeavesItAlone() {
        enable()
        val id = call(
            "habit.add",
            JSONObject().put("title", "读书").put("target_count", 2).put("unit", "章"),
        ).getJSONObject("data").getString("id")
        val habit = runBlocking { db.habitDao().byId(id) }!!
        assertEquals("2026-11-05", habit.lastResetDate)
        assertEquals(2, habit.targetCount)
        assertEquals("章", habit.unit)
        assertEquals(0, habit.currentCount)
    }

    @Test
    fun habitCheckMovesTheCountAndTheResetDate() {
        enable()
        val id = call("habit.add", JSONObject().put("title", "读书").put("target_count", 2))
            .getJSONObject("data").getString("id")

        val first = call("habit.check", JSONObject().put("id", id)).getJSONObject("data")
        assertEquals(1, first.getInt("current_count"))
        assertFalse(first.getBoolean("is_completed"))

        val second = call("habit.check", JSONObject().put("id", id).put("delta", 2)).getJSONObject("data")
        assertEquals(2, second.getInt("current_count")) // clamped at the target
        assertTrue(second.getBoolean("is_completed"))
        assertEquals("2026-11-05", runBlocking { db.habitDao().byId(id) }!!.lastResetDate)
    }

    // ---- countdowns ----

    @Test
    fun countdownAddNeedsALocalIsoTarget() {
        enable()
        val bad = call("countdown.add", JSONObject().put("title", "二课").put("target_at", "2026/11/05 14:00"))
        assertFalse(bad.getBoolean("ok"))
        assertEquals("bad_request", bad.getString("error"))

        val good = call(
            "countdown.add",
            JSONObject().put("title", "二课").put("target_at", "2026-11-07T15:00"),
        )
        assertTrue(good.getBoolean("ok"))
        val id = good.getJSONObject("data").getString("id")
        assertNotNull(runBlocking { db.countdownDao().byId(id) })
        assertEquals("2026-11-07T15:00:00", runBlocking { db.countdownDao().byId(id) }!!.targetAt)
    }

    @Test
    fun countdownDeleteWorks() {
        enable()
        val id = call(
            "countdown.add",
            JSONObject().put("title", "二课").put("target_at", "2026-11-07T15:00"),
        ).getJSONObject("data").getString("id")
        assertTrue(call("countdown.delete", JSONObject().put("id", id)).getBoolean("ok"))
        assertNull(runBlocking { db.countdownDao().byId(id) })
    }

    @Test
    fun unknownOpIsReported() {
        enable()
        assertEquals("bad_op", call("todo.explode").getString("error"))
    }

    // ---- v2: today.summary / water / sleep / focus ----

    @Test
    fun pingReportsProtocolTwo() {
        enable()
        assertEquals(2, call("ping").getJSONObject("data").getInt("protocol"))
        assertEquals(2, RemoteApi.PROTOCOL)
    }

    @Test
    fun todosListOnlyFiltersLikeTheTodoPage() {
        enable()
        val openId = call("todo.add", JSONObject().put("title", "写毕设"))
            .getJSONObject("data").getString("id")
        val doneId = call("todo.add", JSONObject().put("title", "洗床单"))
            .getJSONObject("data").getString("id")
        call("todo.done", JSONObject().put("id", doneId))

        fun titles(only: String): List<String> = call("todos.list", JSONObject().put("only", only))
            .getJSONObject("data").getJSONArray("items")
            .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).getString("title") } }

        assertEquals(listOf("写毕设"), titles("open"))
        assertEquals(listOf("写毕设", "洗床单"), titles("today"))
        assertEquals(2, titles("all").size)
        assertNotNull(openId)
        assertEquals(
            "bad_request",
            call("todos.list", JSONObject().put("only", "everything")).getString("error"),
        )
    }

    @Test
    fun waterLogDefaultsToTheFirstCupAndStaysOnItsDay() {
        enable()
        val first = call("water.log").getJSONObject("data")
        assertEquals(150, first.getInt("ml")) // first quick cup
        assertEquals(150, first.getInt("today_ml"))
        assertEquals(2000, first.getInt("goal_ml"))

        val second = call("water.log", JSONObject().put("ml", 500)).getJSONObject("data")
        assertEquals(650, second.getInt("today_ml"))

        // Yesterday's cups do not count towards today.
        val yesterday = call("water.log", JSONObject().put("ml", 300), at = now.minusDays(1))
            .getJSONObject("data")
        assertEquals(300, yesterday.getInt("today_ml"))

        val today = call("water.today").getJSONObject("data")
        assertEquals(650, today.getInt("ml"))
        assertEquals(2, today.getJSONArray("logs").length())
        assertEquals(3, call("water.today").getJSONObject("data").getJSONArray("cups").length())
    }

    @Test
    fun waterLogRejectsNonsenseAmountsAndIsReplaySafe() {
        enable()
        assertEquals("bad_request", call("water.log", JSONObject().put("ml", 0)).getString("error"))
        assertEquals("bad_request", call("water.log", JSONObject().put("ml", 9999)).getString("error"))

        val data = JSONObject().put("id", "cup-0001").put("ml", 250)
        assertEquals(250, call("water.log", data).getJSONObject("data").getInt("today_ml"))
        // Same id twice: one cup, not two.
        assertEquals(250, call("water.log", data).getJSONObject("data").getInt("today_ml"))
    }

    @Test
    fun sleepLogChecksBothEndsAndNeverTheFuture() {
        enable()
        val inverted = call(
            "sleep.log",
            JSONObject().put("sleep_at", "2026-11-05T07:00").put("wake_at", "2026-11-05T06:00"),
        )
        assertEquals("bad_request", inverted.getString("error"))

        val future = call(
            "sleep.log",
            JSONObject().put("sleep_at", "2026-11-05T13:00").put("wake_at", "2026-11-05T15:00"),
        )
        assertEquals("bad_request", future.getString("error")) // now is 14:30

        val ok = call(
            "sleep.log",
            JSONObject().put("sleep_at", "2026-11-04T23:00").put("wake_at", "2026-11-05T07:10"),
        )
        assertTrue(ok.getBoolean("ok"))
        val data = ok.getJSONObject("data")
        assertEquals(490, data.getInt("minutes"))
        assertEquals("2026-11-05", data.getString("day")) // filed by the wake day
    }

    @Test
    fun sleepListIsBoundedAndFilesByWakeDay() {
        enable()
        call(
            "sleep.log",
            JSONObject().put("sleep_at", "2026-11-04T23:00").put("wake_at", "2026-11-05T07:00"),
        )
        call(
            "sleep.log",
            JSONObject().put("sleep_at", "2026-10-20T13:00").put("wake_at", "2026-10-20T14:00"),
        ) // a nap two weeks back

        val recent = call("sleep.list", JSONObject().put("days", 7)).getJSONObject("data")
        assertEquals(1, recent.getJSONArray("items").length())
        assertEquals(480, recent.getInt("today_minutes"))
        val record = recent.getJSONArray("items").getJSONObject(0)
        assertEquals("2026-11-05", record.getString("day"))

        assertEquals(2, call("sleep.list", JSONObject().put("days", 30))
            .getJSONObject("data").getJSONArray("items").length())
    }

    @Test
    fun todaySummaryCountsEverythingInOneFrame() {
        enable()
        val todoId = call("todo.add", JSONObject().put("title", "写毕设"))
            .getJSONObject("data").getString("id")
        call("todo.add", JSONObject().put("title", "洗床单"))
        call("todo.done", JSONObject().put("id", todoId))
        val habitId = call("habit.add", JSONObject().put("title", "冥想"))
            .getJSONObject("data").getString("id")
        call("habit.check", JSONObject().put("id", habitId))
        call("water.log", JSONObject().put("ml", 250))
        call(
            "sleep.log",
            JSONObject().put("sleep_at", "2026-11-04T23:00").put("wake_at", "2026-11-05T07:00"),
        )
        call("countdown.add", JSONObject().put("title", "考研预试").put("target_at", "2026-12-19T00:00"))

        val data = call("today.summary").getJSONObject("data")
        assertEquals("2026-11-05", data.getString("today"))
        val todos = data.getJSONObject("todos")
        assertEquals(1, todos.getInt("undone"))
        assertEquals(1, todos.getInt("done_today"))
        assertEquals(2, todos.getInt("total"))
        val habits = data.getJSONObject("habits")
        assertEquals(1, habits.getInt("total"))
        assertEquals(1, habits.getInt("done"))
        assertEquals(250, data.getJSONObject("water").getInt("ml"))
        assertEquals(2000, data.getJSONObject("water").getInt("goal_ml"))
        assertEquals(480, data.getJSONObject("sleep").getInt("today_minutes"))
        assertEquals(1, data.getJSONObject("sleep").getInt("streak"))
        assertEquals("考研预试", data.getJSONObject("next_countdown").getString("title"))
        assertEquals(0, data.getJSONObject("focus").getInt("minutes"))
    }

    @Test
    fun focusTodayGroupsMinutesByTodo() {
        enable()
        val id = call("todo.add", JSONObject().put("title", "写毕设"))
            .getJSONObject("data").getString("id")
        runBlocking {
            db.focusDao().insert(com.azarai.goworkbro.core.db.FocusLog("f1", id, "2026-11-05", 25))
            db.focusDao().insert(com.azarai.goworkbro.core.db.FocusLog("f2", id, "2026-11-05", 5))
            db.focusDao().insert(com.azarai.goworkbro.core.db.FocusLog("f3", id, "2026-11-04", 50))
        }

        val data = call("focus.today").getJSONObject("data")
        assertEquals(2, data.getInt("sessions"))
        assertEquals(30, data.getInt("minutes"))
        val byTodo = data.getJSONArray("by_todo")
        assertEquals(1, byTodo.length())
        assertEquals("写毕设", byTodo.getJSONObject(0).getString("title"))
        assertEquals(30, byTodo.getJSONObject(0).getInt("minutes"))
    }
}
