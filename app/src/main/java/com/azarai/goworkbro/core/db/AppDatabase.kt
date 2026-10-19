package com.azarai.goworkbro.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Todo::class,
        Habit::class,
        SleepRecord::class,
        WaterLog::class,
        FitnessLog::class,
        FocusLog::class,
        Countdown::class,
        SettingRow::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun habitDao(): HabitDao
    abstract fun sleepDao(): SleepDao
    abstract fun waterDao(): WaterDao
    abstract fun fitnessDao(): FitnessDao
    abstract fun focusDao(): FocusDao
    abstract fun countdownDao(): CountdownDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        /** Fresh v3 database — deliberately a new file so it never collides
         *  with the v2 database of a replaced install. */
        const val NAME = "goworkbro_v3.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context, NAME).also { instance = it }
            }

        private fun build(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

        /**
         * v2 stored wake/sleep as two `HH:mm` columns on one date row; v3 keeps
         * an absolute-time event per check-in. A row's sleep belongs to its own
         * date when it was before noon, otherwise to the previous day's evening.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routine_events` (`id` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `at` TEXT NOT NULL, " +
                        "`no_sleep` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_events_at` ON `routine_events` (`at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_events_kind` ON `routine_events` (`kind`)")
                db.execSQL(
                    "INSERT INTO routine_events (id, kind, at, no_sleep) " +
                        "SELECT id || '-s', 'sleep', " +
                        "CASE WHEN CAST(substr(sleep_time, 1, 2) AS INTEGER) < 12 " +
                        "THEN record_date ELSE date(record_date, '-1 day') END " +
                        "|| 'T' || sleep_time || ':00', 0 " +
                        "FROM day_records WHERE sleep_time IS NOT NULL",
                )
                db.execSQL(
                    "INSERT INTO routine_events (id, kind, at, no_sleep) " +
                        "SELECT id || '-w', 'wake', record_date || 'T' || wake_time || ':00', 0 " +
                        "FROM day_records WHERE wake_time IS NOT NULL",
                )
                db.execSQL("DROP TABLE day_records")
            }
        }

        /**
         * v4 replaces the wake/sleep check-in event stream with one complete
         * row per night (`sleep_records`). The old routine data is deliberately
         * dropped — the user abandoned it — but a real Migration must still
         * exist: without it an on-device upgrade crashes with "a migration
         * from 3 to 4 was required but not found".
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS routine_events")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sleep_records` (`id` TEXT NOT NULL, " +
                        "`sleep_at` TEXT NOT NULL, `wake_at` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_records_sleep_at` ON `sleep_records` (`sleep_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_records_wake_at` ON `sleep_records` (`wake_at`)")
            }
        }

        /** v5 adds the countdown module (one row per countdown item). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `countdowns` (`id` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `target_at` TEXT NOT NULL, " +
                        "`color_index` INTEGER NOT NULL, `created_at` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_countdowns_target_at` ON `countdowns` (`target_at`)")
            }
        }

        /** v6 stamps finished todos with the logical day they were completed
         *  on, so the list can hide anything older than today. Existing done
         *  rows get "" (= un-dated history, shown under 更早完成). */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `todos` ADD COLUMN `done_date` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Robolectric: methods share a classloader, so the static instance
         *  must be dropped between tests (see Graph.rebindForTesting).
         *  The file name stays stable — Robolectric gives every test method a
         *  fresh filesDir, and a second Room instance on the same file keeps
         *  cross-instance invalidation working (that's how the tests seed data
         *  that the already-launched activity must observe). */
        fun clearForTesting() {
            synchronized(this) { instance = null }
        }
    }
}
