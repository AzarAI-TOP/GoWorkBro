package com.azarai.goworkbro.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY sort_order")
    fun observeAll(): Flow<List<Todo>>

    @Query("SELECT * FROM todos")
    suspend fun getAll(): List<Todo>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: String): Todo?

    @Upsert
    suspend fun upsert(todo: Todo)

    @Upsert
    suspend fun upsertAll(todos: List<Todo>)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM todos")
    suspend fun deleteAll()
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sort_order")
    fun observeAll(): Flow<List<Habit>>

    @Query("SELECT * FROM habits")
    suspend fun getAll(): List<Habit>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: String): Habit?

    @Upsert
    suspend fun upsert(habit: Habit)

    @Upsert
    suspend fun upsertAll(habits: List<Habit>)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE habits SET current_count = 0, last_reset_date = :date WHERE last_reset_date != :date OR last_reset_date IS NULL")
    suspend fun resetForNewDay(date: String)

    @Query("DELETE FROM habits")
    suspend fun deleteAll()
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions WHERE session_date = :date ORDER BY start_time")
    fun observeByDate(date: String): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE session_date BETWEEN :from AND :to")
    suspend fun getDateRange(from: String, to: String): List<FocusSession>

    @Query("SELECT * FROM focus_sessions")
    suspend fun getAll(): List<FocusSession>

    @Query("SELECT * FROM focus_sessions")
    fun observeAll(): Flow<List<FocusSession>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: FocusSession)

    @Upsert
    suspend fun upsert(session: FocusSession)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAll()
}

@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdowns ORDER BY target_datetime")
    fun observeAll(): Flow<List<Countdown>>

    @Query("SELECT * FROM countdowns")
    suspend fun getAll(): List<Countdown>

    @Query("SELECT * FROM countdowns WHERE id = :id")
    suspend fun getById(id: String): Countdown?

    @Upsert
    suspend fun upsert(countdown: Countdown)

    @Query("DELETE FROM countdowns WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM countdowns WHERE target_datetime <= :nowUtc")
    suspend fun cleanupExpired(nowUtc: String)

    @Query("DELETE FROM countdowns")
    suspend fun deleteAll()
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_records ORDER BY record_date DESC")
    fun observeAll(): Flow<List<SleepRecord>>

    @Query("SELECT * FROM sleep_records")
    suspend fun getAll(): List<SleepRecord>

    @Query("SELECT * FROM sleep_records WHERE record_date = :date")
    suspend fun getByDate(date: String): SleepRecord?

    @Upsert
    suspend fun upsert(record: SleepRecord)

    @Query("DELETE FROM sleep_records WHERE record_date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM sleep_records")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM user_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM user_settings")
    suspend fun getAll(): List<SettingRow>

    @Query("SELECT * FROM user_settings")
    fun observeAll(): Flow<List<SettingRow>>

    @Upsert
    suspend fun set(row: SettingRow)

    @Query("DELETE FROM user_settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM user_settings")
    suspend fun deleteAll()
}

@Dao
interface NewsCacheDao {
    @Query("SELECT * FROM ustc_news_cache WHERE date = :date")
    suspend fun getByDate(date: String): NewsCache?

    @Query("SELECT * FROM ustc_news_cache ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): NewsCache?

    @Query("SELECT * FROM ustc_news_cache ORDER BY date DESC")
    fun observeAll(): Flow<List<NewsCache>>

    @Query("SELECT * FROM ustc_news_cache ORDER BY date DESC")
    suspend fun getAll(): List<NewsCache>

    @Upsert
    suspend fun upsertAll(items: List<NewsCache>)

    @Query("DELETE FROM ustc_news_cache")
    suspend fun deleteAll()
}

@Dao
interface MaintenanceDao {
    /** Runs inside the import transaction: clears every data table. */
    @Transaction
    suspend fun wipeData(
        todoDao: TodoDao,
        habitDao: HabitDao,
        focusDao: FocusDao,
        countdownDao: CountdownDao,
        sleepDao: SleepDao,
        settingsDao: SettingsDao,
        newsDao: NewsCacheDao,
    ) {
        todoDao.deleteAll()
        habitDao.deleteAll()
        focusDao.deleteAll()
        countdownDao.deleteAll()
        sleepDao.deleteAll()
        settingsDao.deleteAll()
        newsDao.deleteAll()
    }
}
