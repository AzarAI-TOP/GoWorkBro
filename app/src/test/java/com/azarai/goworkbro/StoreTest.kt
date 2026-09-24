package com.azarai.goworkbro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = GoWorkBroApp::class)
class StoreTest {

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

    @Test
    fun defaults() = runBlocking {
        val store = Store(db.settingsDao())
        assertEquals(Store.DEFAULT_WATER_GOAL, store.waterGoalMl())
        assertEquals(Store.DEFAULT_FITNESS_GOAL, store.fitnessGoalMin())
        assertEquals(Store.DEFAULT_WATER_CUPS, store.waterCups())
    }

    @Test
    fun cupsRoundTripAndSanitize() = runBlocking {
        val store = Store(db.settingsDao())
        store.setWaterCups(listOf(200, 300))
        assertEquals(listOf(200, 300), store.waterCups())
        // garbage falls back to defaults
        store.set(Store.Keys.WATER_CUPS, "not json")
        assertEquals(Store.DEFAULT_WATER_CUPS, store.waterCups())
    }

    @Test
    fun goalSetGet() = runBlocking {
        val store = Store(db.settingsDao())
        store.set(Store.Keys.WATER_GOAL_ML, "2500")
        assertEquals(2500, store.waterGoalMl())
    }
}
