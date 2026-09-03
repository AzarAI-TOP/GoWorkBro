package com.azarai.goworkbro

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.NewsCache
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.AppRoot
import com.azarai.goworkbro.ui.EnvViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/**
 * Full-app smoke test on Robolectric: boots the real application, renders
 * the whole Compose UI and walks the primary flows (todos, habits, completed
 * section, timer, countdown, Today + news, Me + language switch).
 *
 * Screen titles duplicate the bottom-nav labels ("待办" etc.), so tab clicks
 * use onFirst() and assertions target unique strings.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [35], application = GoWorkBroApp::class)
class AppSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before
    fun seed() {
        // Robolectric reuses the classloader across methods — rebind Graph to
        // this method's fresh application before touching any singleton.
        Graph.rebindForTesting(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        // Seed a cached news edition and make the hourly fetch check a no-op,
        // keeping the smoke test offline-deterministic.
        runBlocking {
            Graph.db.newsCacheDao().upsertAll(
                listOf(
                    NewsCache(
                        date = Dates.todayKey(),
                        title = "USTC 每日要闻 — 测试",
                        markdown = "# USTC 每日要闻 — 测试\n\n- 测试条目",
                    ),
                ),
            )
            Graph.store.set(Store.Keys.NEWS_LAST_FETCH, System.currentTimeMillis().toString())
        }
    }

    private fun launch() {
        rule.setContent {
            val env: EnvViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            AppRoot(env)
        }
        rule.waitForIdle()
        // Robolectric: first composition (incl. bundled font loading) can be
        // slow and racy — poll until the tab shell is materialized.
        rule.waitUntil(20_000) { exists("待办") }
        rule.waitForIdle()
    }

    private fun exists(text: String) =
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun tabsRenderAndEmptyStateShows() {
        launch()
        org.junit.Assert.assertTrue(exists("待办"))
        org.junit.Assert.assertTrue(exists("Today"))
        org.junit.Assert.assertTrue(exists("我"))
        rule.onNodeWithText("还没有待办事项").assertExists()
        rule.onNodeWithText("点击右下角 + 创建第一个待办").assertExists()
    }

    @Test
    fun addTodoOpenTimerAndCompleteHabitFlow() {
        launch()

        // -- add a forward-timer todo (Room write is async -> poll) --
        rule.onNodeWithContentDescription("添加").performClick()
        rule.onNodeWithText("TODO").performClick()
        rule.onNodeWithText("想做什么？").performTextInput("复习线代")
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil(5_000) { exists("复习线代") }
        rule.onNodeWithText("正向计时").assertExists()

        // -- tapping a timed todo opens the timer overlay --
        rule.onNodeWithText("复习线代").performClick()
        rule.onNodeWithText("开始").assertExists()
        // back out without recording (elapsed == 0 -> close directly)
        androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().runOnMainSync {
                rule.activity.onBackPressedDispatcher.onBackPressed()
            }
        rule.waitForIdle()
        org.junit.Assert.assertFalse(exists("开始"))

        // -- add a habit and complete it --
        rule.onNodeWithContentDescription("添加").performClick()
        rule.onNodeWithText("HABIT").performClick()
        rule.onNodeWithText("习惯名称").performTextInput("喝水")
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil(5_000) { exists("喝水") }
        rule.onNodeWithText("每日 0/1 次").assertExists()
        rule.onNodeWithContentDescription("打卡").performClick()
        // Completed habit moves into the collapsed completed section.
        rule.waitUntil(5_000) { exists("已完成 · 1") }
    }

    @Test
    fun countdownTabAddFlow() {
        launch()
        rule.onAllNodesWithText("倒计时")[0].performClick()
        rule.onNodeWithText("还没有倒计时").assertExists()
        rule.onNodeWithContentDescription("添加").performClick()
        rule.onNodeWithText("标题").performTextInput("期末考试")
        rule.onNodeWithText("创建").performClick()
        rule.waitUntil(5_000) { exists("期末考试") }
        org.junit.Assert.assertTrue(
            rule.onAllNodesWithText("目标 ", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun todayTabShowsStatsAndNews() {
        launch()
        rule.onAllNodesWithText("Today")[0].performClick()
        rule.onNodeWithText("今日专注").assertExists()
        // The news banner takes the top slot, pushing the chart cards below
        // Robolectric's 320x470 fold — scroll to each before asserting.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("来源分布"))
        rule.onNodeWithText("来源分布").assertExists()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("近 7 天"))
        rule.onNodeWithText("近 7 天").assertExists()
        // Back to the top: the news banner opens the reader overlay.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("USTC 每日要闻"))
        rule.onNodeWithText("USTC 每日要闻").performClick()
        // The Today banner behind the overlay repeats the title prefix, so use
        // existence checks (>=1) rather than unique-match assertions.
        org.junit.Assert.assertTrue(exists("USTC 每日要闻 — 测试"))
        org.junit.Assert.assertTrue(exists("测试条目"))
    }

    @Test
    fun meTabRendersAndLanguageSwitches() {
        launch()
        rule.onAllNodesWithText("我")[0].performClick()
        rule.onNodeWithText("今日打卡").assertExists()
        rule.onNodeWithText("起床").assertExists()
        rule.onAllNodesWithText("统计")[0].performClick()
        rule.onNodeWithText("累计统计").assertExists()
        rule.onAllNodesWithText("设置")[0].performClick()
        rule.onNodeWithText("数据管理").assertExists()
        // switch language to English and verify tab labels flip in-place
        // (the locale write goes through Room, so wait for recomposition)
        rule.onNodeWithText("English").performClick()
        rule.waitUntil(10_000) { exists("Todo") }
        org.junit.Assert.assertTrue(exists("Countdown"))
    }
}
