package com.azarai.goworkbro.core

import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.db.SettingRow
import com.azarai.goworkbro.core.db.SettingsDao
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/**
 * Settings key-value store on top of `user_settings`. All v3 settings are
 * non-visual, so no boot mirror is needed anymore.
 */
class Store(private val dao: SettingsDao) {

    object Keys {
        /** After-midnight-before-4am activity counts for the previous day. */
        const val LATE_NIGHT_MODE = "late_night_mode"
        const val LAST_ROLLOVER_DATE = "last_rollover_date"
        const val WATER_GOAL_ML = "water_goal_ml"
        const val WATER_CUPS = "water_cups"
        const val FITNESS_GOAL_MIN = "fitness_goal_min"
    }

    suspend fun get(key: String): String? = dao.get(key)

    suspend fun set(key: String, value: String) = dao.set(SettingRow(key, value))

    fun observe(key: String): Flow<String?> = dao.observeAll().map { rows ->
        rows.firstOrNull { it.key == key }?.value
    }

    suspend fun waterGoalMl(): Int = get(Keys.WATER_GOAL_ML)?.toIntOrNull() ?: DEFAULT_WATER_GOAL

    suspend fun fitnessGoalMin(): Int = get(Keys.FITNESS_GOAL_MIN)?.toIntOrNull() ?: DEFAULT_FITNESS_GOAL

    suspend fun waterCups(): List<Int> = parseWaterCups(get(Keys.WATER_CUPS))

    suspend fun setWaterCups(cups: List<Int>) {
        set(Keys.WATER_CUPS, JSONArray(cups).toString())
    }

    companion object {
        /** Decodes the stored cup presets; anything unreadable falls back to defaults. */
        fun parseWaterCups(raw: String?): List<Int> {
            if (raw == null) return DEFAULT_WATER_CUPS
            val cups = runCatching {
                val arr = JSONArray(raw)
                List(arr.length()) { arr.getInt(it) }.filter { it > 0 }
            }.getOrDefault(emptyList())
            return cups.ifEmpty { DEFAULT_WATER_CUPS }
        }

        const val DEFAULT_WATER_GOAL = 2000
        const val DEFAULT_FITNESS_GOAL = 45
        val DEFAULT_WATER_CUPS = listOf(150, 250, 500)
    }
}

/** Daily rollover engine — resets habit counters at the logical day boundary. */
object Rollover {

    suspend fun logicalToday(store: Store): String {
        val lateNight = store.get(Store.Keys.LATE_NIGHT_MODE) != "false"
        val last = store.get(Store.Keys.LAST_ROLLOVER_DATE) ?: ""
        return Dates.logicalDateKey(LocalDateTime.now(), lateNight, last)
    }

    suspend fun ensure(db: AppDatabase, store: Store) {
        val target = logicalToday(store)
        val last = store.get(Store.Keys.LAST_ROLLOVER_DATE) ?: ""
        if (last == target) return
        db.habitDao().resetForNewDay(target)
        store.set(Store.Keys.LAST_ROLLOVER_DATE, target)
    }
}
