package com.azarai.goworkbro

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.ui.timer.ActiveTimer
import com.azarai.goworkbro.ui.timer.TimerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Focus-round counting rules: a round only counts after a full minute. */
@RunWith(AndroidJUnit4::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [35], application = GoWorkBroApp::class)
class TimerRulesTest {

    private val todo = Todo(id = "t1", title = "数学", timingType = "forward")

    @Before
    fun setup() {
        Graph.rebindForTesting(ApplicationProvider.getApplicationContext())
        runBlocking { Graph.db.todoDao().upsert(todo) }
    }

    /** Let Room's executor finish the insert launched from viewModelScope. */
    private fun awaitSessions(expected: Int): Int {
        var count = 0
        repeat(60) {
            shadowOf(Looper.getMainLooper()).idle()
            count = runBlocking { Graph.db.focusDao().countForTodo(todo.id) }
            if (count >= expected) return count
            Thread.sleep(40)
        }
        return count
    }

    @Test
    fun homeQuickCheckInsUseTheRoutineStream() {
        val vm = com.azarai.goworkbro.ui.home.HomeViewModel(ApplicationProvider.getApplicationContext())
        var needsSleep = false

        vm.checkInSleep()
        waitUntilEvents(1)
        assertEquals(listOf("sleep"), kinds())

        // a wake now pairs with that bedtime, so no prompt is requested
        vm.checkInWake { needsSleep = true }
        waitUntilEvents(2)
        assertEquals(false, needsSleep)
        assertEquals(listOf("sleep", "wake"), kinds())

        // a second wake has nothing to pair with -> the UI must ask
        vm.checkInWake { needsSleep = true }
        waitUntilEvents(3)
        assertEquals(true, needsSleep)

        vm.checkInWakeAllNighter()
        waitUntilEvents(4)
        assertEquals(1, runBlocking { Graph.db.routineDao().getAll() }.count { it.noSleep })
    }

    private fun kinds() = runBlocking { Graph.db.routineDao().getAll() }.sortedBy { it.at }.map { it.kind }

    private fun waitUntilEvents(expected: Int) {
        repeat(60) {
            shadowOf(Looper.getMainLooper()).idle()
            if (runBlocking { Graph.db.routineDao().getAll().size } >= expected) return
            Thread.sleep(40)
        }
    }

    @Test
    fun shortRoundIsNotCounted() {
        val vm = TimerViewModel()
        vm.start(todo)
        vm.stop() // immediately: well under a minute
        assertEquals(0, awaitSessions(1))
        assertNull(vm.active.value)
    }

    @Test
    fun fullMinuteCountsAsOneSession() {
        val vm = TimerViewModel()
        // a paused round that already accumulated 65 seconds
        vm.active.value = ActiveTimer(
            todo = todo,
            forward = false,
            durationMs = 10 * 60_000L,
            accMs = 65_000L,
            startedAt = null,
        )
        vm.stop()
        assertEquals(1, awaitSessions(1))
        assertNull(vm.active.value)
    }

    @Test
    fun completingTodoMarksItDoneAndCounts() {
        val vm = TimerViewModel()
        vm.active.value = ActiveTimer(
            todo = todo,
            forward = true,
            durationMs = null,
            accMs = 120_000L,
            startedAt = null,
        )
        vm.completeTodo()
        assertEquals(1, awaitSessions(1))
        val stored = runBlocking { Graph.db.todoDao().observeAll().first() }.single()
        assertEquals(true, stored.isDone)
    }
}
