package com.azarai.goworkbro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.util.Ordering
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = GoWorkBroApp::class)
class TodoOrderTest {

    private lateinit var db: AppDatabase

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

    private fun todo(id: String, order: Int, done: Boolean = false) =
        Todo(id = id, title = id, sortOrder = order, isDone = done)

    @Test
    fun newestFirstAndDoneSinksToBottom() = runBlocking {
        val dao = db.todoDao()
        dao.upsert(todo("A", 1))
        dao.upsert(todo("B", 2))
        dao.upsert(todo("C", 3, done = true))
        assertEquals(listOf("B", "A", "C"), dao.observeAll().first().map { it.id })

        // completing A moves it below B, after the newer done item C
        dao.upsert(todo("A", 1, done = true))
        assertEquals(listOf("B", "C", "A"), dao.observeAll().first().map { it.id })

        // un-completing A puts it back on top
        dao.upsert(todo("A", 1, done = false))
        assertEquals(listOf("B", "A", "C"), dao.observeAll().first().map { it.id })
    }

    @Test
    fun completionsAreScopedToTheirDay() = runBlocking {
        val dao = db.todoDao()
        // newest completion first; "" (un-dated, e.g. migrated history) sinks last
        dao.upsert(Todo(id = "A", title = "A", sortOrder = 3, isDone = true, doneDate = "2026-10-19"))
        dao.upsert(Todo(id = "B", title = "B", sortOrder = 2, isDone = true, doneDate = "2026-10-18"))
        dao.upsert(Todo(id = "C", title = "C", sortOrder = 1, isDone = true, doneDate = ""))

        assertEquals(listOf("A"), dao.observeDoneOn("2026-10-19").first().map { it.id })
        assertEquals(
            listOf("B", "C"),
            dao.observeDoneBefore("2026-10-19", 50).first().map { it.id },
        )
        assertEquals(2, dao.observeDoneCountBefore("2026-10-19").first())
        // nothing is deleted: the open list stays empty, history keeps both rows
        assertEquals(emptyList<String>(), dao.observeUndone().first().map { it.id })
    }

    @Test
    fun reorderingOpenTodosPersistsTheDraggedOrder() = runBlocking {
        val dao = db.todoDao()
        listOf("A" to 30, "B" to 20, "C" to 10).forEach { (id, order) -> dao.upsert(todo(id, order)) }
        assertEquals(listOf("A", "B", "C"), dao.observeUndone().first().map { it.id })

        // drag C to the front — the same write the page performs on drop
        val byId = dao.observeUndone().first().associateBy { it.id }
        val dragged = listOf("C", "A", "B").map { byId.getValue(it) }
        val ranks = Ordering.rankOrders(dragged.size)
        dao.updateAll(dragged.mapIndexed { i, row -> row.copy(sortOrder = ranks[i]) })
        assertEquals(listOf("C", "A", "B"), dao.observeUndone().first().map { it.id })
    }

    @Test
    fun reorderingHabitsWritesAnAscendingOrder() = runBlocking {
        val dao = db.habitDao()
        listOf("A" to 30, "B" to 20, "C" to 10).forEach { (id, order) ->
            dao.upsert(Habit(id = id, title = id, sortOrder = order))
        }
        assertEquals(listOf("C", "B", "A"), dao.observeAll().first().map { it.id })

        val byId = dao.observeAll().first().associateBy { it.id }
        val dragged = listOf("B", "C", "A").map { byId.getValue(it) }
        val ranks = Ordering.rankOrders(dragged.size).reversed()
        dao.updateAll(dragged.mapIndexed { i, row -> row.copy(sortOrder = ranks[i]) })
        assertEquals(listOf("B", "C", "A"), dao.observeAll().first().map { it.id })
    }

    @Test
    fun completedRowsAreNeverRenderedInBothLists() {
        // Room re-runs the two queries independently, so one frame can hold the
        // same todo as open AND finished — the page must not show it twice
        val todo = Todo(id = "A", title = "A", sortOrder = 2)
        val state = com.azarai.goworkbro.ui.todo.TodoListState(
            undone = listOf(todo, Todo(id = "B", title = "B")),
            doneToday = listOf(todo.copy(isDone = true, doneDate = "2026-10-27")),
        ).withoutDuplicates()
        assertEquals(listOf("B"), state.undone.map { it.id })
        assertEquals(listOf("A"), state.doneToday.map { it.id })
    }

    @Test
    fun timingTypeRoundTrip() = runBlocking {
        val dao = db.todoDao()
        dao.upsert(
            Todo(
                id = "T",
                title = "T",
                timingType = "countdown",
                durationMinutes = 45,
            ),
        )
        val loaded = dao.observeAll().first().single()
        assertEquals(com.azarai.goworkbro.core.db.TimingType.COUNTDOWN, loaded.timing)
        assertEquals(45, loaded.durationMinutes)
    }
}
