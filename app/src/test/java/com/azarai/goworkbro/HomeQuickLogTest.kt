package com.azarai.goworkbro

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azarai.goworkbro.core.Rollover
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.ui.home.HomeState
import com.azarai.goworkbro.ui.home.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * The home grid's one-tap water log. The nested 打卡 button cannot be clicked
 * through Compose semantics (see AGENTS.md "Device automation"), so the path
 * is covered here through the ViewModel instead.
 */
@RunWith(AndroidJUnit4::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [35], application = GoWorkBroApp::class)
class HomeQuickLogTest {

    @Before
    fun setup() {
        Graph.rebindForTesting(ApplicationProvider.getApplicationContext())
        com.azarai.goworkbro.core.LogicalDay.notifyResumed()
    }

    private fun todayRows() = runBlocking {
        Graph.db.waterDao().observeByDate(Rollover.logicalToday(Graph.store)).first()
    }

    @Test
    fun quickCupDefaultsToTheFirstPreset() {
        val vm = HomeViewModel(ApplicationProvider.getApplicationContext())
        var state: HomeState? = null
        val collector = CoroutineScope(Dispatchers.Unconfined).launch {
            vm.state.first { it.waterGoal > 0 }.let { state = it }
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (state == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        collector.cancel()
        assertNotNull("home state never arrived", state)
        assertEquals(Store.DEFAULT_WATER_CUPS.first(), state!!.quickCupMl)
    }

    @Test
    fun drinkWaterWritesATodayRow() {
        val vm = HomeViewModel(ApplicationProvider.getApplicationContext())
        vm.drinkWater(250)
        var rows = emptyList<com.azarai.goworkbro.core.db.WaterLog>()
        val deadline = System.currentTimeMillis() + 10_000
        while (rows.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            rows = todayRows()
            Thread.sleep(40)
        }
        assertEquals(1, rows.size)
        assertEquals(250, rows.single().ml)
    }

    @Test
    fun drinkWaterIgnoresNonPositiveAmounts() {
        val vm = HomeViewModel(ApplicationProvider.getApplicationContext())
        vm.drinkWater(0)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        assertEquals(0, todayRows().size)
    }
}
