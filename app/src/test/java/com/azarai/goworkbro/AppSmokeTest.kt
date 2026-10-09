package com.azarai.goworkbro

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/**
 * Full-app smoke test on Robolectric: boots the real activity, renders the
 * home grid and walks the todo / habit / water flows.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [35], application = GoWorkBroApp::class)
class AppSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seed() {
        Graph.rebindForTesting(androidx.test.core.app.ApplicationProvider.getApplicationContext())
    }

    private fun exists(text: String) =
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun hasDesc(desc: String) =
        rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty()

    private fun hasSub(text: String) =
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun homeReady() {
        rule.waitForIdle()
        rule.waitUntil(20_000) { exists("待办") && exists("睡眠") }
    }

    @Test
    fun startupRevealPlaysThenDismisses() {
        homeReady()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithContentDescription("启动动画").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun homeShowsAllModules() {
        homeReady()
        listOf("待办", "习惯", "喝水", "健身", "睡眠").forEach {
            assertTrue("missing module card: $it", exists(it))
        }
    }

    @Test
    fun todoAddToggleFlow() {
        homeReady()
        rule.onAllNodesWithText("待办").onFirst().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("添加").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("添加").performClick()
        rule.waitUntil(10_000) { exists("新待办") }
        rule.onNode(hasSetTextAction()).performTextInput("写作业")
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil(10_000) { exists("写作业") }
        // tap toggles done
        rule.onAllNodesWithText("写作业").onFirst().performClick()
        rule.waitUntil(10_000) { exists("已完成") }
        // and back
        rule.onAllNodesWithText("写作业").onFirst().performClick()
        rule.waitUntil(10_000) { exists("待完成") }
    }

    @Test
    fun timerForwardFlow() {
        homeReady()
        rule.onAllNodesWithText("待办").onFirst().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("添加").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("添加").performClick()
        rule.waitUntil(10_000) { exists("新待办") }
        rule.onNode(hasSetTextAction()).performTextInput("Focus")
        rule.onNodeWithText("正向计时").performClick()
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil(10_000) { exists("Focus") }
        // tap the timed card -> focus screen opens
        rule.onAllNodesWithText("Focus").onFirst().performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("暂停").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("暂停").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("继续").fetchSemanticsNodes().isNotEmpty()
        }
        // stop -> back to the list, card still there
        rule.onNodeWithContentDescription("提前结束").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("暂停").fetchSemanticsNodes().isEmpty() }
        assertTrue(exists("Focus"))
    }

    @Test
    fun countdownTimerAndHomeMiniCardFlow() {
        homeReady()
        rule.onAllNodesWithText("待办").onFirst().performClick()
        rule.waitUntil(10_000) { hasDesc("添加") }
        rule.onNodeWithContentDescription("添加").performClick()
        rule.waitUntil(10_000) { exists("新待办") }
        rule.onNode(hasSetTextAction()).performTextInput("Read")
        rule.onNodeWithText("倒计时").performClick()
        rule.onNodeWithText("10").performClick()
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil(10_000) { hasSub("倒计时 10min") }

        // countdown card opens the focus screen
        rule.onAllNodesWithText("Read").onFirst().performClick()
        rule.waitUntil(10_000) { exists("倒计时") && exists("本任务已进行 0 次") }
        assertTrue(hasDesc("暂停") && hasDesc("提前结束"))

        // back arrow only closes the focus page -> list -> home mini-card
        rule.onAllNodesWithContentDescription("返回").onFirst().performClick()
        rule.waitUntil(10_000) { hasDesc("添加") }
        rule.onAllNodesWithContentDescription("返回").onFirst().performClick()
        rule.waitUntil(10_000) { exists("已进行 0 次") }
        assertTrue(exists("倒"))
        assertTrue(hasSub("%"))

        // mini-card -> focus page; pause then finish early
        rule.onAllNodesWithText("Read").onFirst().performClick()
        rule.waitUntil(10_000) { hasDesc("暂停") }
        rule.onNodeWithContentDescription("暂停").performClick()
        rule.waitUntil(10_000) { hasSub("已暂停") }

        // finishing from the focus page lands on the todo list
        rule.onNodeWithContentDescription("提前结束").performClick()
        rule.waitUntil(10_000) { hasDesc("添加") }
        assertTrue(hasDesc("暂停").not())

        // ...and a round shorter than a minute is not counted
        rule.onAllNodesWithText("Read").onFirst().performClick()
        rule.waitUntil(10_000) { exists("本任务已进行 0 次") }
        rule.onAllNodesWithContentDescription("返回").onFirst().performClick()
        rule.waitUntil(10_000) { hasDesc("添加") }

        // long-press -> edit dialog -> 标记完成 (the only way to close a timed todo)
        rule.onAllNodesWithText("Read").onFirst().performTouchInput { longClick() }
        rule.waitUntil(10_000) { exists("标记完成") }
        rule.onNodeWithText("标记完成").performClick()
        rule.waitUntil(10_000) { exists("已完成") }

        // a finished todo is history: no delete affordance, only 标记未完成
        rule.onAllNodesWithText("Read").onFirst().performTouchInput { longClick() }
        rule.waitUntil(10_000) { exists("标记未完成") }
        assertTrue(rule.onAllNodesWithText("删除").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun recordSleepTwoStepSheetWritesOneRow() {
        homeReady()
        rule.onAllNodesWithText("睡眠").onFirst().performClick()
        rule.waitUntil(10_000) { exists("记录睡眠") }
        rule.onNodeWithText("记录睡眠").performScrollTo().performClick()
        rule.waitUntil(10_000) { exists("第 1 步 · 睡眠时间") }

        // step 1 keeps its default (now - 7h): confirm as-is
        rule.onNodeWithText("确认睡眠时间").performClick()
        rule.waitUntil(10_000) { exists("第 2 步 · 起床时间") }

        // step 2 keeps its default (now): confirming writes ONE complete row.
        // The default wake/sleep clamp to the wheel window, so only the
        // structural invariant is asserted: one row, positive duration.
        // (The hero/home-card refresh after the write is verified on the
        // emulator — Robolectric's Room-flow replay across the sheet is not
        // a reliable witness for it.)
        rule.onNodeWithText("确认起床时间").performClick()
        rule.waitUntil(10_000) { !exists("第 2 步 · 起床时间") }
        val rows = runBlocking { Graph.db.sleepDao().observeAll().first() }
        org.junit.Assert.assertEquals(1, rows.size)
        org.junit.Assert.assertTrue(rows.single().minutes >= 30)
    }

    @Test
    fun everyModuleScreenRenders() {
        homeReady()
        fun open(name: String) {
            rule.onAllNodesWithText(name).onFirst().performClick()
            rule.waitForIdle()
        }
        fun back(scrollable: Boolean = false) {
            val node = rule.onAllNodesWithContentDescription("返回").onFirst()
            if (scrollable) node.performScrollTo()
            node.performClick()
            rule.waitForIdle()
        }

        open("待办"); assertTrue(hasDesc("添加")); back()
        open("习惯"); assertTrue(hasDesc("添加")); back()

        open("喝水")
        rule.waitUntil(5_000) { exists("快捷杯子（长按修改）") && exists("今日记录") && exists("近 7 天") }
        assertTrue(exists("今日目标达成啦 🎉") || hasSub("还差"))
        back()

        open("健身")
        rule.waitUntil(5_000) { exists("运动计时") && exists("今日记录") && exists("近 7 天") }
        assertTrue(exists("开始运动"))
        // start and finish inside a minute: the workout is discarded, not logged
        rule.onNodeWithText("开始运动").performClick()
        rule.waitUntil(5_000) { exists("结束并记录") }
        rule.onNodeWithText("结束并记录").performClick()
        rule.waitUntil(5_000) { exists("开始运动") }
        back()

        open("睡眠")
        rule.waitUntil(5_000) { exists("睡眠记录") } // appbar title is always on screen
        back()

        // overview -> settings -> back out of both
        rule.onNodeWithContentDescription("数据纵览").performClick()
        rule.waitUntil(5_000) { exists("今日专注时间占比") && exists("近七天睡眠时长") }
        rule.onNodeWithContentDescription("设置").performScrollTo().performClick()
        rule.waitUntil(5_000) { exists("熬夜模式") && exists("删除应用数据") }
        back()
        rule.waitUntil(5_000) { exists("今日专注时间占比") }
        back(scrollable = true)
        rule.waitUntil(5_000) { exists("待办") && exists("睡眠") }
    }

    @Test
    fun waterDrinkFlow() {
        homeReady()
        rule.onAllNodesWithText("喝水").onFirst().performClick()
        rule.waitUntil(10_000) { exists("快捷杯子（长按修改）") }
        rule.onNodeWithText("250ml").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("喝了 250 ml", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
