package com.azarai.goworkbro.core

import android.content.Context
import android.content.SharedPreferences
import com.azarai.goworkbro.core.db.AppDatabase
import com.azarai.goworkbro.core.db.SettingRow
import com.azarai.goworkbro.core.db.SettingsDao
import com.azarai.goworkbro.core.util.Dates
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/**
 * Settings key-value store. SQLite `user_settings` is the source of truth
 * (and is exported); a small SharedPreferences mirror carries boot-critical
 * visual settings (locale / theme / font) so the first frame is correct
 * before the database finishes opening.
 */
class Store(private val dao: SettingsDao, private val bootPrefs: SharedPreferences) {

    object Keys {
        const val LOCALE = "locale"
        const val THEME_MODE = "theme_mode"
        const val FONT_FAMILY = "font_family"
        const val LATE_NIGHT_MODE = "late_night_mode"
        const val USER_NAME = "user_name"
        const val AVATAR_PATH = "avatar_path"
        const val FIRST_USED_DATE = "first_used_date"
        const val LAST_ROLLOVER_DATE = "last_rollover_date"
        const val LIFETIME_TODOS_COMPLETED = "lifetime_todos_completed"
        const val LIFETIME_HABITS_COMPLETED = "lifetime_habits_completed"
        const val CUSTOM_HABIT_UNITS = "custom_habit_units"
        const val NEWS_LAST_FETCH = "news.last_fetch_ts"
        const val LEGACY_CLEANUP_DONE = "legacy_cleanup_done"
        const val TIMER_ACTIVE = "timer.active"
    }

    val bootLocale: String = bootPrefs.getString(Keys.LOCALE, "zh") ?: "zh"
    val bootThemeMode: String = bootPrefs.getString(Keys.THEME_MODE, "system") ?: "system"
    val bootFontFamily: String = bootPrefs.getString(Keys.FONT_FAMILY, "proto") ?: "proto"

    suspend fun get(key: String): String? = dao.get(key)

    suspend fun set(key: String, value: String) {
        dao.set(SettingRow(key, value))
        if (key in mirrorKeys) bootPrefs.edit().putString(key, value).apply()
    }

    suspend fun delete(key: String) {
        dao.delete(key)
        if (key in mirrorKeys) bootPrefs.edit().remove(key).apply()
    }

    fun observeAll(): Flow<List<SettingRow>> = dao.observeAll()

    fun observe(key: String): Flow<String?> = dao.observeAll().map { rows ->
        rows.firstOrNull { it.key == key }?.value
    }

    suspend fun incrementCounterOnce(counterKey: String, eventKey: String): Int {
        if (dao.get(eventKey) != null) {
            return (dao.get(counterKey)?.toIntOrNull() ?: 0)
        }
        dao.set(SettingRow(eventKey, "1"))
        val next = (dao.get(counterKey)?.toIntOrNull() ?: 0) + 1
        dao.set(SettingRow(counterKey, next.toString()))
        return next
    }

    suspend fun customHabitUnits(): List<String> = parseUnits(dao.get(Keys.CUSTOM_HABIT_UNITS))

    fun observeCustomHabitUnits(): Flow<List<String>> =
        dao.observeAll().map { rows -> parseUnits(rows.firstOrNull { it.key == Keys.CUSTOM_HABIT_UNITS }?.value) }

    private fun parseUnits(raw: String?): List<String> = runCatching {
        if (raw == null) return emptyList()
        val arr = JSONArray(raw)
        List(arr.length()) { arr.getString(it) }
    }.getOrDefault(emptyList())

    suspend fun addCustomHabitUnit(unit: String) {
        val list = customHabitUnits().toMutableList()
        if (unit.isNotEmpty() && unit !in list) {
            list.add(unit)
            dao.set(SettingRow(Keys.CUSTOM_HABIT_UNITS, JSONArray(list).toString()))
        }
    }

    suspend fun firstUsedDate(today: String): String {
        val existing = dao.get(Keys.FIRST_USED_DATE)
        if (existing != null) return existing
        dao.set(SettingRow(Keys.FIRST_USED_DATE, today))
        return today
    }

    private val mirrorKeys = setOf(Keys.LOCALE, Keys.THEME_MODE, Keys.FONT_FAMILY)

    companion object {
        fun createBootPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences("goworkbro_boot", Context.MODE_PRIVATE)
    }
}

/** Daily rollover engine — resets habits and cleans expired countdowns. */
object Rollover {

    suspend fun logicalToday(store: Store): String {
        val lateNight = store.get(Store.Keys.LATE_NIGHT_MODE) == "true"
        val last = store.get(Store.Keys.LAST_ROLLOVER_DATE) ?: ""
        return Dates.logicalDateKey(LocalDateTime.now(), lateNight, last)
    }

    /**
     * Runs the rollover when the logical date changed. Completed todos are
     * intentionally kept (v2 semantics — they stay in the completed section
     * until manually removed); "keep tomorrow" todos already have their copy.
     */
    suspend fun ensure(db: AppDatabase, store: Store) {
        val target = logicalToday(store)
        val last = store.get(Store.Keys.LAST_ROLLOVER_DATE) ?: ""
        if (last == target) return
        db.habitDao().resetForNewDay(target)
        db.countdownDao().cleanupExpired(java.time.Instant.now().toString())
        store.set(Store.Keys.LAST_ROLLOVER_DATE, target)
    }
}
