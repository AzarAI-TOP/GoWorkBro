package com.azarai.goworkbro

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azarai.goworkbro.core.Rollover
import com.azarai.goworkbro.core.db.FocusLog
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.util.Dates
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/**
 * Overview page flow. This class seeds the database BEFORE the activity
 * launches on purpose: Room's invalidation only tracks writes made through
 * the same database instance, so data inserted after launch through a second
 * instance would never show up in the UI.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [35], application = GoWorkBroApp::class)
class OverviewFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun exists(text: String) =
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun hasSub(text: String) =
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun hasDesc(desc: String) =
        rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun showsStatsThenDeletesSelectedData() {
        Graph.rebindForTesting(ApplicationProvider.getApplicationContext())
        val todo = Todo(id = "seed-1", title = "数学", icon = "book", colorIndex = 2, timingType = "forward")
        runBlocking {
            // the app keys focus stats by the late-night logical day
            val today = Rollover.logicalToday(Graph.store)
            Graph.db.todoDao().upsert(todo)
            Graph.db.focusDao().insert(FocusLog("f1", todo.id, today, 25))
            Graph.db.focusDao().insert(FocusLog("f2", todo.id, today, 20))
            // a night of sleep so the duration chart has data (23:30 -> 07:30 = 8h)
            val night = java.time.LocalDate.now().minusDays(1)
            Graph.db.routineDao().upsert(
                com.azarai.goworkbro.core.db.RoutineEvent(
                    id = "e1",
                    kind = com.azarai.goworkbro.core.RoutineKind.SLEEP,
                    at = Dates.isoDateTime(night.atTime(23, 30)),
                ),
            )
            Graph.db.routineDao().upsert(
                com.azarai.goworkbro.core.db.RoutineEvent(
                    id = "e2",
                    kind = com.azarai.goworkbro.core.RoutineKind.WAKE,
                    at = Dates.isoDateTime(java.time.LocalDate.now().atTime(7, 30)),
                ),
            )
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario?.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()

        // home header summary
        rule.waitUntil(20_000) { exists("2 次专注 - 共专注 45 分钟") }

        // sprout -> overview
        rule.onNodeWithContentDescription("数据纵览").performClick()
        // wait for the flows, not just the static titles
        rule.waitUntil(15_000) { exists("今日专注时间占比") && hasSub("今日 2 次 · 45 分钟") }
        rule.waitUntil(15_000) { hasSub("100%") } // the seeded todo owns all of today's focus
        assertTrue(exists("近七天睡眠时长"))

        // gear -> settings page -> 删除应用数据 -> pick types -> confirm
        rule.onNodeWithContentDescription("设置").performScrollTo().performClick()
        rule.waitUntil(10_000) { exists("设置") && exists("删除应用数据") }
        rule.onNodeWithText("删除应用数据").performClick()
        rule.waitUntil(10_000) { exists("待办（含专注记录）") }
        rule.onNodeWithText("待办（含专注记录）").performClick()
        rule.onNodeWithText("删除").performClick()
        rule.waitUntil(10_000) { exists("确认删除？") }
        rule.onNodeWithText("删除").performClick()

        // back to the overview (settings page is not scrollable): pie is empty now
        rule.onAllNodesWithContentDescription("返回").onFirst().performClick()
        rule.waitUntil(10_000) { exists("今天还没有专注记录") }

        // back home: the todo is gone as well
        rule.onAllNodesWithContentDescription("返回").onFirst().performScrollTo().performClick()
        rule.waitUntil(10_000) { exists("去添加一条吧") }
    }
}
