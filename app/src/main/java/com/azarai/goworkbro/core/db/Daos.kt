package com.azarai.goworkbro.core.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    /** Undone first (newest on top); done items sink to the bottom. */
    @Query("SELECT * FROM todos ORDER BY is_done ASC, sort_order DESC")
    fun observeAll(): Flow<List<Todo>>

    @Upsert
    suspend fun upsert(todo: Todo)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sort_order")
    fun observeAll(): Flow<List<Habit>>

    @Upsert
    suspend fun upsert(habit: Habit)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE habits SET current_count = 0, last_reset_date = :date WHERE last_reset_date != :date OR last_reset_date IS NULL")
    suspend fun resetForNewDay(date: String)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine_events ORDER BY at DESC")
    fun observeAll(): Flow<List<RoutineEvent>>

    @Query("SELECT * FROM routine_events ORDER BY at DESC LIMIT 1")
    suspend fun latest(): RoutineEvent?

    @Query("SELECT * FROM routine_events")
    suspend fun getAll(): List<RoutineEvent>

    @Upsert
    suspend fun upsert(event: RoutineEvent)

    @Query("DELETE FROM routine_events WHERE id = :id")
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

    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface FitnessDao {
    @Query("SELECT * FROM fitness_logs WHERE log_date = :date ORDER BY logged_at")
    fun observeByDate(date: String): Flow<List<FitnessLog>>

    @Query("SELECT log_date, SUM(minutes) AS total FROM fitness_logs WHERE log_date BETWEEN :from AND :to GROUP BY log_date")
    suspend fun dailyTotals(from: String, to: String): List<DailyTotal>

    @Insert
    suspend fun insert(log: FitnessLog)

    @Query("DELETE FROM fitness_logs WHERE id = :id")
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

    @Query("DELETE FROM fitness_logs")
    suspend fun clearFitnessLogs()

    @Query("DELETE FROM routine_events")
    suspend fun clearRoutineEvents()
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM user_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM user_settings")
    fun observeAll(): Flow<List<SettingRow>>

    @Upsert
    suspend fun set(row: SettingRow)

    @Query("DELETE FROM user_settings WHERE `key` = :key")
    suspend fun delete(key: String)
}
