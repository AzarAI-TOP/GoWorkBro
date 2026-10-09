package com.azarai.goworkbro

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.timer.ActiveTimer
import com.azarai.goworkbro.ui.timer.TimerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        // LogicalDay is a static singleton shared across test methods — its
        // running flow still points at the PREVIOUS method's db. Kick it so
        // the next emission re-reads today from the fresh store (the same
        // thing the activity's ON_RESUME does in the real app).
        com.azarai.goworkbro.core.LogicalDay.notifyResumed()
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
    fun homeCardShowsTodaysSleepTotal() {
        runBlocking {
            val today = java.time.LocalDate.now()
            Graph.db.sleepDao().insert(
                com.azarai.goworkbro.core.db.SleepRecord(
                    id = "s1",
                    sleepAt = Dates.isoDateTime(today.atTime(13, 0)),
                    wakeAt = Dates.isoDateTime(today.atTime(14, 30)),
                ),
            )
        }
        val vm = com.azarai.goworkbro.ui.home.HomeViewModel(ApplicationProvider.getApplicationContext())
        // viewModelScope runs on Main.immediate — in a bare VM test nothing
        // pumps the looper, so collect with Unconfined and idle the main
        // looper by hand (same pattern the old check-in tests used).
        var seen: com.azarai.goworkbro.ui.home.HomeState? = null
        val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch {
            vm.state.first { it.todaySleepText != null }.let { seen = it }
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (seen == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        collector.cancel()
        org.junit.Assert.assertNotNull("todaySleepText never arrived", seen)
        assertEquals("1h 30m", seen!!.todaySleepText)
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
