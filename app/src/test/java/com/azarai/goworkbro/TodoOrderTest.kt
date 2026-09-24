package com.azarai.goworkbro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.db.Todo
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
