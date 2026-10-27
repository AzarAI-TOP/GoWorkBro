package com.azarai.goworkbro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azarai.goworkbro.core.Backup
import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.db.Countdown
import com.azarai.goworkbro.core.db.FocusLog
import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.db.SettingRow
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.db.WaterLog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Export → import roundtrip over an in-memory database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = GoWorkBroApp::class)
class BackupTest {

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

    private fun freshDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

    private suspend fun seed(db: AppDatabase) {
        db.todoDao().upsert(Todo("t1", "写论文", icon = "scroll", colorIndex = 2, timingType = "forward", durationMinutes = 30, sortOrder = 5, createdDate = "2026-10-09"))
        db.todoDao().upsert(Todo("t2", "已完成的事", isDone = true, sortOrder = 4, createdDate = "2026-10-08"))
        db.habitDao().upsert(Habit("h1", "喝水8杯", targetCount = 8, currentCount = 3))
        db.habitDao().upsert(Habit("h2", "阅读", lastResetDate = "2026-10-09"))
        db.waterDao().insert(WaterLog("w1", "2026-10-09", 250, "09:30"))
        db.sleepDao().insert(SleepRecord("s1", "2026-10-08T23:00:00", "2026-10-09T06:30:00"))
        db.countdownDao().upsert(Countdown("c1", "考研", "2026-12-26T08:30:00", 3))
        db.focusDao().insert(FocusLog("fo1", "t1", "2026-10-09", 25))
        db.settingsDao().set(SettingRow("water_goal_ml", "2500"))
    }

    @Test
    fun roundtripRestoresEveryTable() = runBlocking {
        seed(db)
        val json = Backup.export(db)

        val other = freshDb()
        val summary = Backup.import(other, json)!!
        assertEquals(2, summary.todos)
        assertEquals(2, summary.habits)
        assertEquals(1, summary.waterLogs)
        assertEquals(1, summary.sleepRecords)
        assertEquals(1, summary.countdowns)
        assertEquals(1, summary.focusLogs)
        assertEquals(1, summary.settings)
        assertEquals(9, summary.total)

        val t1 = other.todoDao().getAll().first { it.id == "t1" }
        assertEquals("forward", t1.timingType)
        assertEquals(30, t1.durationMinutes)
        assertEquals(2, t1.colorIndex)
        assertEquals("2026-10-09", t1.createdDate)

        val h1 = other.habitDao().getAll().first { it.id == "h1" }
        assertEquals(3, h1.currentCount)
        assertEquals(8, h1.targetCount)
        assertNull(h1.lastResetDate)
        assertEquals("2026-10-09", other.habitDao().getAll().first { it.id == "h2" }.lastResetDate)

        val sleep = other.sleepDao().getAll().single()
        assertEquals("2026-10-08T23:00:00", sleep.sleepAt)
        assertEquals(450, sleep.minutes)

        assertEquals("2500", other.settingsDao().get("water_goal_ml"))
        other.close()
    }

    @Test
    fun importReplacesExistingRows() = runBlocking {
        db.todoDao().upsert(Todo("old", "旧待办"))
        val json = """{"format":1,"todos":[
            {"id":"new","title":"新待办","is_done":false}]}""".trimIndent()
        val summary = Backup.import(db, json)!!
        assertEquals(1, summary.todos)
        val todos = db.todoDao().getAll()
        assertEquals(listOf("new"), todos.map { it.id })
    }

    @Test
    fun garbageAndForeignFormatsAreRejected() = runBlocking {
        assertNull(Backup.import(db, "not json at all"))
        assertNull(Backup.import(db, """{"format":99,"todos":[]}"""))
    }

    @Test
    fun emptyPayloadClearsEverything() = runBlocking {
        seed(db)
        val summary = Backup.import(db, """{"format":1}""")!!
        assertEquals(0, summary.total)
        assertEquals(0, db.todoDao().getAll().size)
        assertEquals(0, db.sleepDao().getAll().size)
        assertEquals(0, db.settingsDao().getAll().size)
    }
}
