import 'dart:io';

import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../sync/sync_compare.dart' show nowStamp;

/// Owns the SQLite connection, schema and migrations.
///
/// Domain CRUD lives in the repositories under
/// `repositories/` (TodoRepository, HabitRepository, …) — this class only
/// knows how to open/upgrade/reset the database file.
class AppDatabase {
  static const int schemaVersion = 7;

  static Database? _db;

  /// Test-only override for the data directory (Platform.environment is
  /// read-only, so tests cannot set GOWORKBRO_TEST_DATA_DIR at runtime).
  static String? _overrideDataDir;

  @visibleForTesting
  static void setDataDirForTesting(String path) {
    _overrideDataDir = path;
  }

  static Future<Database> get database async {
    if (_db != null) return _db!;

    sqfliteFfiInit();
    final dbFactory = databaseFactoryFfi;

    final testDataDir =
        _overrideDataDir ?? Platform.environment['GOWORKBRO_TEST_DATA_DIR'];
    final appDir = testDataDir == null
        ? await getApplicationDocumentsDirectory()
        : Directory(testDataDir);
    if (!await appDir.exists()) await appDir.create(recursive: true);
    final dbPath = p.join(appDir.path, 'goworkbro.db');

    _db = await dbFactory.openDatabase(
      dbPath,
      options: OpenDatabaseOptions(
        version: schemaVersion,
        onCreate: _onCreate,
        onUpgrade: _onUpgrade,
      ),
    );
    return _db!;
  }

  static Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE todos (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        timing_type TEXT NOT NULL,
        duration_minutes INTEGER NOT NULL DEFAULT 25,
        is_completed INTEGER NOT NULL DEFAULT 0,
        sort_order INTEGER NOT NULL DEFAULT 0,
        keep_tomorrow INTEGER NOT NULL DEFAULT 1,
        created_date TEXT NOT NULL,
        completed_date TEXT,
        actual_duration_seconds INTEGER NOT NULL DEFAULT 0,
        updated_at TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE habits (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        target_count INTEGER NOT NULL DEFAULT 1,
        unit TEXT NOT NULL DEFAULT '次',
        sort_order INTEGER NOT NULL DEFAULT 0,
        created_date TEXT NOT NULL,
        current_count INTEGER NOT NULL DEFAULT 0,
        last_reset_date TEXT,
        updated_at TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE focus_sessions (
        id TEXT PRIMARY KEY,
        todo_id TEXT,
        source_type TEXT NOT NULL,
        source_title TEXT NOT NULL,
        start_time TEXT NOT NULL,
        end_time TEXT NOT NULL,
        duration_seconds INTEGER NOT NULL,
        session_date TEXT NOT NULL
      )
    ''');

    await db.execute('''
      CREATE TABLE countdowns (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        target_datetime TEXT NOT NULL,
        created_date TEXT NOT NULL,
        color_index INTEGER NOT NULL DEFAULT 0,
        updated_at TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE sleep_records (
        id TEXT PRIMARY KEY,
        record_date TEXT NOT NULL,
        wake_time TEXT,
        sleep_time TEXT,
        workout_time TEXT,
        workout_duration_minutes INTEGER,
        note TEXT,
        updated_at TEXT
      )
    ''');

    await db.execute('''
      CREATE UNIQUE INDEX IF NOT EXISTS idx_sleep_records_record_date_unique
      ON sleep_records(record_date)
    ''');

    await db.execute('''
      CREATE TABLE user_settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      )
    ''');

    await _createNewsCacheTable(db);

    // Insert defaults for a brand-new install. Existing databases are migrated
    // in-place by _onUpgrade and retain all user data.
    await db.insert('user_settings', {'key': 'user_name', 'value': '离线用户'});
    await db.insert('user_settings', {
      'key': 'first_used_date',
      'value': DateTime.now().toIso8601String(),
    });
    await db.insert('user_settings', {
      'key': 'lifetime_todos_completed',
      'value': '0',
    });
    await db.insert('user_settings', {
      'key': 'lifetime_habits_completed',
      'value': '0',
    });
  }

  static Future<void> _createNewsCacheTable(DatabaseExecutor db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS ustc_news_cache (
        date TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        markdown TEXT NOT NULL,
        cached_at TEXT NOT NULL
      )
    ''');
  }

  /// Handle database schema migrations between versions.
  static Future<void> _onUpgrade(
    Database db,
    int oldVersion,
    int newVersion,
  ) async {
    if (oldVersion < 2 && newVersion >= 2) {
      await db.execute(
        'ALTER TABLE sleep_records ADD COLUMN workout_time TEXT;',
      );
    }
    if (oldVersion < 3 && newVersion >= 3) {
      await _createNewsCacheTable(db);
      final completedTodoRows = await db.rawQuery(
        'SELECT COUNT(*) AS count FROM todos WHERE is_completed = 1',
      );
      final completedTodos = completedTodoRows.first['count'] as int? ?? 0;
      final completedHabitRows = await db.rawQuery(
        'SELECT COUNT(*) AS count FROM habits WHERE current_count >= target_count',
      );
      final completedHabits = completedHabitRows.first['count'] as int? ?? 0;
      await db.insert('user_settings', {
        'key': 'first_used_date',
        'value': DateTime.now().toIso8601String(),
      }, conflictAlgorithm: ConflictAlgorithm.ignore);
      await db.insert('user_settings', {
        'key': 'lifetime_todos_completed',
        'value': completedTodos.toString(),
      }, conflictAlgorithm: ConflictAlgorithm.ignore);
      await db.insert('user_settings', {
        'key': 'lifetime_habits_completed',
        'value': completedHabits.toString(),
      }, conflictAlgorithm: ConflictAlgorithm.ignore);
      final completedTodoIds = await db.query(
        'todos',
        columns: ['id'],
        where: 'is_completed = 1',
      );
      for (final row in completedTodoIds) {
        await db.insert('user_settings', {
          'key': 'completion.todo.${row['id']}',
          'value': DateTime.now().toIso8601String(),
        }, conflictAlgorithm: ConflictAlgorithm.ignore);
      }
      final completedHabitMarkerRows = await db.query(
        'habits',
        columns: ['id', 'last_reset_date'],
        where: 'current_count >= target_count',
      );
      final today = DateTime.now().toIso8601String().substring(0, 10);
      for (final row in completedHabitMarkerRows) {
        final completionDate = row['last_reset_date'] as String? ?? today;
        await db.insert('user_settings', {
          'key': 'completion.habit.${row['id']}.$completionDate',
          'value': DateTime.now().toIso8601String(),
        }, conflictAlgorithm: ConflictAlgorithm.ignore);
      }
    }
    if (oldVersion < 4 && newVersion >= 4) {
      // v4: last-write-wins sync — updated_at stamps for the mergeable tables.
      await db.execute('ALTER TABLE todos ADD COLUMN updated_at TEXT;');
      await db.execute('ALTER TABLE habits ADD COLUMN updated_at TEXT;');
      await db.execute('ALTER TABLE countdowns ADD COLUMN updated_at TEXT;');
    }
    if (oldVersion < 5 && newVersion >= 5) {
      // v5: workouts are described by duration + text, not a clock time.
      // Keep workout_time so legacy records remain losslessly exportable.
      await db.execute(
        'ALTER TABLE sleep_records ADD COLUMN workout_duration_minutes INTEGER;',
      );
    }
    if (oldVersion < 6 && newVersion >= 6) {
      // v6: sleep rows participate in last-write-wins cloud sync.
      // Legacy rows keep a null stamp (unknown age): an existing cloud row
      // must win, while pushAll separately uploads dates missing in cloud.
      await db.execute('ALTER TABLE sleep_records ADD COLUMN updated_at TEXT;');
      await _deduplicateSleepRecords(db);
      await db.execute('''
        CREATE UNIQUE INDEX IF NOT EXISTS idx_sleep_records_record_date_unique
        ON sleep_records(record_date)
      ''');
    }
    if (oldVersion < 7 && newVersion >= 7) {
      // v7: countdowns store target_datetime as UTC ISO-8601 (Z suffix) so
      // the instant survives cross-timezone sync. Legacy rows are
      // timezone-naive local strings — re-encode as the same instant in UTC
      // and bump updated_at so the UTC form wins LWW against stale cloud
      // rows (otherwise the next pull would overwrite it back to naive).
      final rows = await db.query(
        'countdowns',
        columns: ['id', 'target_datetime'],
      );
      for (final row in rows) {
        final raw = row['target_datetime'] as String?;
        if (raw == null) continue;
        final parsed = DateTime.tryParse(raw);
        if (parsed == null || parsed.isUtc) continue;
        await db.update(
          'countdowns',
          {
            'target_datetime': parsed.toUtc().toIso8601String(),
            'updated_at': nowStamp(),
          },
          where: 'id = ?',
          whereArgs: [row['id']],
        );
      }
    }
  }

  static Future<void> _deduplicateSleepRecords(
    DatabaseExecutor db,
  ) async {
    final dates = await db.rawQuery('''
      SELECT record_date
      FROM sleep_records
      GROUP BY record_date
      HAVING COUNT(*) > 1
    ''');
    const mergeColumns = [
      'wake_time',
      'sleep_time',
      'workout_time',
      'workout_duration_minutes',
      'note',
      'updated_at',
    ];
    for (final duplicate in dates) {
      final recordDate = duplicate['record_date'] as String;
      final rows = await db.query(
        'sleep_records',
        where: 'record_date = ?',
        whereArgs: [recordDate],
        orderBy: 'rowid ASC',
      );
      final keeperId = rows.first['id'] as String;
      final merged = <String, Object?>{};
      for (final column in mergeColumns) {
        for (final row in rows.reversed) {
          if (row[column] != null) {
            merged[column] = row[column];
            break;
          }
        }
      }
      if (merged.isNotEmpty) {
        await db.update(
          'sleep_records',
          merged,
          where: 'id = ?',
          whereArgs: [keeperId],
        );
      }
      await db.delete(
        'sleep_records',
        where: 'record_date = ? AND id <> ?',
        whereArgs: [recordDate, keeperId],
      );
    }
  }

  @visibleForTesting
  static Future<void> migrateForTesting(
    Database db,
    int oldVersion,
    int newVersion,
  ) => _onUpgrade(db, oldVersion, newVersion);

  @visibleForTesting
  static Future<void> closeForTesting() async {
    await _db?.close();
    _db = null;
  }

  /// Drop all tables and recreate the database from scratch.
  /// Used by "delete all data" — wipes todos, habits, focus sessions,
  /// countdowns, sleep records, and user settings.
  static Future<void> deleteAllData() async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.execute('DROP TABLE IF EXISTS todos');
      await txn.execute('DROP TABLE IF EXISTS habits');
      await txn.execute('DROP TABLE IF EXISTS focus_sessions');
      await txn.execute('DROP TABLE IF EXISTS countdowns');
      await txn.execute('DROP TABLE IF EXISTS sleep_records');
      await txn.execute('DROP TABLE IF EXISTS user_settings');
      await txn.execute('DROP TABLE IF EXISTS ustc_news_cache');
    });
    await _onCreate(db, schemaVersion);
  }
}
