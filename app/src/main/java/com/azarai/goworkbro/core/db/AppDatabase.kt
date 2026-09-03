package com.azarai.goworkbro.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Todo::class,
        Habit::class,
        FocusSession::class,
        Countdown::class,
        SleepRecord::class,
        SettingRow::class,
        NewsCache::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun habitDao(): HabitDao
    abstract fun focusDao(): FocusDao
    abstract fun countdownDao(): CountdownDao
    abstract fun sleepDao(): SleepDao
    abstract fun settingsDao(): SettingsDao
    abstract fun newsCacheDao(): NewsCacheDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val NAME = "goworkbro.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }

        /** Robolectric: methods share a classloader, so the static instance
         *  must be dropped between tests (see Graph.rebindForTesting). */
        fun clearForTesting() {
            synchronized(this) { instance = null }
        }
    }
}
