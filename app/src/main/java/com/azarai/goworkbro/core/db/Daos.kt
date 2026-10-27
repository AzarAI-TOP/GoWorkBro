package com.azarai.goworkbro.core.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    /** Undone first (newest on top); done items sink to the bottom. */
    @Query("SELECT * FROM todos ORDER BY is_done ASC, sort_order DESC")
    fun observeAll(): Flow<List<Todo>>

    /** The todo page's bounded halves: active work in full, finished work
     *  split by the logical day it was completed on. */
    @Query("SELECT * FROM todos WHERE is_done = 0 ORDER BY sort_order DESC")
    fun observeUndone(): Flow<List<Todo>>

    /** Finished today: what still shows under "已完成". */
    @Query("SELECT * FROM todos WHERE is_done = 1 AND done_date = :date ORDER BY sort_order DESC")
    fun observeDoneOn(date: String): Flow<List<Todo>>

    /** Older completions — hidden behind "更早完成", never deleted. */
    @Query(
        "SELECT * FROM todos WHERE is_done = 1 AND done_date <> :date " +
            "ORDER BY done_date DESC, sort_order DESC LIMIT :limit",
    )
    fun observeDoneBefore(date: String, limit: Int): Flow<List<Todo>>

    @Query("SELECT COUNT(*) FROM todos WHERE is_done = 1 AND done_date <> :date")
    fun observeDoneCountBefore(date: String): Flow<Int>

    /** Aggregate counts for the home tile — done todos are kept forever, so
     *  home must not load the full history just to count. */    @Query("SELECT COUNT(*) AS total, IFNULL(SUM(CASE WHEN is_done = 0 THEN 1 ELSE 0 END), 0) AS undone FROM todos")
    fun observeCounts(): Flow<TodoCounts>

    @Upsert
    suspend fun upsert(todo: Todo)

    /** One statement for a drag-reordered list — a per-row loop would emit a
     *  burst of invalidations and let the grid flicker through half orders. */
    @Update
    suspend fun updateAll(todos: List<Todo>)

    @Insert
    suspend fun insertAll(todos: List<Todo>)

    @Query("SELECT * FROM todos")
    suspend fun getAll(): List<Todo>

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: String)
}

/** Result row of the todo count aggregate. */
data class TodoCounts(val total: Int, val undone: Int)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sort_order")
    fun observeAll(): Flow<List<Habit>>

    /** Aggregate mirror of `Habit.isCompleted` (`current_count >= target_count`). */
    @Query("SELECT COUNT(*) AS total, IFNULL(SUM(CASE WHEN current_count >= target_count THEN 1 ELSE 0 END), 0) AS done FROM habits")
    fun observeCounts(): Flow<HabitCounts>

    @Upsert
    suspend fun upsert(habit: Habit)

    /** One statement for a drag-reordered list (see TodoDao.updateAll). */
    @Update
    suspend fun updateAll(habits: List<Habit>)

    @Insert
    suspend fun insertAll(habits: List<Habit>)

    @Query("SELECT * FROM habits")
    suspend fun getAll(): List<Habit>

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE habits SET current_count = 0, last_reset_date = :date WHERE last_reset_date != :date OR last_reset_date IS NULL")
    suspend fun resetForNewDay(date: String)
}

/** Result row of the habit count aggregate. */
data class HabitCounts(val total: Int, val done: Int)

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_records ORDER BY wake_at DESC")
    fun observeAll(): Flow<List<SleepRecord>>

    /** Records that WOKE in `[from, to)` — exactly the set
     *  `SleepOps.dailyKeyOf` files under that day (ISO strings sort
     *  chronologically). */
    @Query("SELECT * FROM sleep_records WHERE wake_at >= :from AND wake_at < :to ORDER BY wake_at DESC")
    fun observeWakesBetween(from: String, to: String): Flow<List<SleepRecord>>

    /** Newest-first records that woke since [from] — bounded history for the
     *  stats page. */
    @Query("SELECT * FROM sleep_records WHERE wake_at >= :from ORDER BY wake_at DESC")
    fun observeWakesSince(from: String): Flow<List<SleepRecord>>

    @Insert
    suspend fun insert(record: SleepRecord)

    @Insert
    suspend fun insertAll(records: List<SleepRecord>)

    @Query("SELECT * FROM sleep_records")
    suspend fun getAll(): List<SleepRecord>

    @Query("DELETE FROM sleep_records WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdowns ORDER BY target_at")
    fun observeAll(): Flow<List<Countdown>>

    @Upsert
    suspend fun upsert(item: Countdown)

    @Insert
    suspend fun insertAll(items: List<Countdown>)

    @Query("SELECT * FROM countdowns")
    suspend fun getAll(): List<Countdown>

    @Query("DELETE FROM countdowns WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_logs WHERE log_date = :date ORDER BY logged_at")
    fun observeByDate(date: String): Flow<List<WaterLog>>

    @Query("SELECT log_date, SUM(ml) AS total FROM water_logs WHERE log_date BETWEEN :from AND :to GROUP BY log_date")
    suspend fun dailyTotals(from: String, to: String): List<DailyTotal>

    @Insert
    suspend fun insert(log: WaterLog)

    @Insert
    suspend fun insertAll(logs: List<WaterLog>)

    @Query("SELECT * FROM water_logs")
    suspend fun getAll(): List<WaterLog>

    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun deleteById(id: String)
}

/** Result row of the GROUP BY total queries above. */
data class DailyTotal(
    @ColumnInfo(name = "log_date") val logDate: String,
    val total: Int,
)

/** Focus rounds + minutes for one day (or all time, with date = null filter). */
data class FocusStats(
    val sessions: Int,
    val minutes: Int,
)

/** Focus minutes grouped by todo, for the today-share pie. */
data class FocusByTodo(
    @ColumnInfo(name = "todo_id") val todoId: String,
    val minutes: Int,
)

@Dao
interface FocusDao {
    @Insert
    suspend fun insert(log: FocusLog)

    @Insert
    suspend fun insertAll(logs: List<FocusLog>)

    @Query("SELECT * FROM focus_logs")
    suspend fun getAll(): List<FocusLog>

    @Query("SELECT COUNT(*) FROM focus_logs WHERE todo_id = :todoId")
    suspend fun countForTodo(todoId: String): Int

    @Query("SELECT COUNT(*) AS sessions, IFNULL(SUM(minutes), 0) AS minutes FROM focus_logs WHERE log_date = :date")
    fun observeDayStats(date: String): Flow<FocusStats>

    @Query("SELECT COUNT(*) AS sessions, IFNULL(SUM(minutes), 0) AS minutes FROM focus_logs")
    fun observeTotalStats(): Flow<FocusStats>

    @Query("SELECT todo_id, SUM(minutes) AS minutes FROM focus_logs WHERE log_date = :date GROUP BY todo_id")
    fun observeDayByTodo(date: String): Flow<List<FocusByTodo>>

}

@Dao
interface MaintenanceDao {
    @Query("DELETE FROM todos")
    suspend fun clearTodos()

    @Query("DELETE FROM focus_logs")
    suspend fun clearFocusLogs()

    @Query("DELETE FROM habits")
    suspend fun clearHabits()

    @Query("DELETE FROM water_logs")
    suspend fun clearWaterLogs()

    @Query("DELETE FROM sleep_records")
    suspend fun clearSleepRecords()

    @Query("DELETE FROM countdowns")
    suspend fun clearCountdowns()

    @Query("DELETE FROM user_settings")
    suspend fun clearSettings()
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM user_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM user_settings")
    fun observeAll(): Flow<List<SettingRow>>

    @Query("SELECT * FROM user_settings")
    suspend fun getAll(): List<SettingRow>

    @Upsert
    suspend fun set(row: SettingRow)

    @Insert
    suspend fun insertAll(rows: List<SettingRow>)

    @Query("DELETE FROM user_settings WHERE `key` = :key")
    suspend fun delete(key: String)
}
