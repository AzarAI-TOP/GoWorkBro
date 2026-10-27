package com.azarai.goworkbro.core

import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.db.SettingRow
import com.azarai.goworkbro.core.db.SettingsDao
import com.azarai.goworkbro.core.util.Dates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/**
 * Settings key-value store on top of `user_settings`. All v3 settings are
 * non-visual, so no boot mirror is needed anymore.
 */
class Store(private val dao: SettingsDao) {

    object Keys {
        const val LAST_ROLLOVER_DATE = "last_rollover_date"
        const val WATER_GOAL_ML = "water_goal_ml"
        const val WATER_CUPS = "water_cups"
        /** "false" opts out of GitHub update checks + downloads. */
        const val AUTO_UPDATE = "auto_update"
        /** Epoch ms of the last automatic update check. */
        const val LAST_UPDATE_CHECK = "last_update_check"
        /** Calendar day of the last cold-start reveal (play at most once a day). */
        const val LAST_REVEAL_DATE = "last_reveal_date"
    }

    suspend fun get(key: String): String? = dao.get(key)

    suspend fun set(key: String, value: String) = dao.set(SettingRow(key, value))

    fun observe(key: String): Flow<String?> = dao.observeAll().map { rows ->
        rows.firstOrNull { it.key == key }?.value
    }

    suspend fun waterGoalMl(): Int = get(Keys.WATER_GOAL_ML)?.toIntOrNull() ?: DEFAULT_WATER_GOAL

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
        val DEFAULT_WATER_CUPS = listOf(150, 250, 500)
    }
}

/** Daily rollover engine — resets habit counters when the calendar day changes. */
object Rollover {

    /**
     * One guard against the reset running twice a day: the timer flow emits
     * once per day boundary already, but a foreground return can re-fire it.
     */
    suspend fun ensure(db: AppDatabase, store: Store) {
        val target = Dates.todayKey()
        val last = store.get(Store.Keys.LAST_ROLLOVER_DATE) ?: ""
        if (last == target) return
        db.habitDao().resetForNewDay(target)
        store.set(Store.Keys.LAST_ROLLOVER_DATE, target)
    }
}
