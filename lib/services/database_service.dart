import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import '../models/models.dart';

class DatabaseService {
  static Database? _db;

  static Future<Database> get database async {
    if (_db != null) return _db!;

    sqfliteFfiInit();
    final dbFactory = databaseFactoryFfi;

    final appDir = await getApplicationDocumentsDirectory();
    final dbPath = p.join(appDir.path, 'goworkbro.db');

    _db = await dbFactory.openDatabase(
      dbPath,
      options: OpenDatabaseOptions(
        version: 2,
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
        actual_duration_seconds INTEGER NOT NULL DEFAULT 0
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
        last_reset_date TEXT
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
        color_index INTEGER NOT NULL DEFAULT 0
      )
    ''');

    await db.execute('''
      CREATE TABLE sleep_records (
        id TEXT PRIMARY KEY,
        record_date TEXT NOT NULL,
        wake_time TEXT,
        sleep_time TEXT,
        workout_time TEXT,
        note TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE user_settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      )
    ''');

    // Insert default settings
    await db.insert('user_settings', {'key': 'user_name', 'value': 'AzarAI'});
  }

  /// Handle database schema migrations between versions.
  static Future<void> _onUpgrade(Database db, int oldVersion, int newVersion) async {
    // v1 -> v2: add workout_time column to sleep_records
    if (oldVersion < 2) {
      await db.execute('ALTER TABLE sleep_records ADD COLUMN workout_time TEXT;');
    }
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
    });
    await _onCreate(db, 2);
  }

  // ============ TODO CRUD ============

  static Future<List<Todo>> getTodos() async {
    final db = await database;
    final maps = await db.query('todos', orderBy: 'sort_order ASC');
    return maps.map((m) => Todo.fromMap(m)).toList();
  }

  static Future<String> insertTodo(Todo todo) async {
    final db = await database;
    await db.insert('todos', todo.toMap());
    return todo.id;
  }

  static Future<void> updateTodo(Todo todo) async {
    final db = await database;
    await db.update('todos', todo.toMap(), where: 'id = ?', whereArgs: [todo.id]);
  }

  static Future<void> deleteTodo(String id) async {
    final db = await database;
    await db.delete('todos', where: 'id = ?', whereArgs: [id]);
  }

  /// Roll over todos for a new day.
  /// With the new "明天继续" semantics, completed todos with keepTomorrow=true
  /// already had an incomplete copy created at completion time.
  /// So at rollover we simply delete all completed todos (both keep and non-keep).
  /// Incomplete todos carry over automatically (no action needed).
  static Future<void> rollOverTodos(String todayDate) async {
    final db = await database;
    // Delete completed — keepTomorrow copies were already created at completion time.
    await db.delete('todos', where: 'is_completed = 1');
  }

  // ============ HABIT CRUD ============

  static Future<List<Habit>> getHabits() async {
    final db = await database;
    final maps = await db.query('habits', orderBy: 'sort_order ASC');
    return maps.map((m) => Habit.fromMap(m)).toList();
  }

  static Future<String> insertHabit(Habit habit) async {
    final db = await database;
    await db.insert('habits', habit.toMap());
    return habit.id;
  }

  static Future<void> updateHabit(Habit habit) async {
    final db = await database;
    await db.update('habits', habit.toMap(), where: 'id = ?', whereArgs: [habit.id]);
  }

  static Future<void> deleteHabit(String id) async {
    final db = await database;
    await db.delete('habits', where: 'id = ?', whereArgs: [id]);
  }

  /// Reset all habit counts at the start of a new day
  static Future<void> resetHabitsForNewDay(String todayDate) async {
    final db = await database;
    await db.update('habits',
        {'current_count': 0, 'last_reset_date': todayDate},
        where: 'last_reset_date != ? OR last_reset_date IS NULL',
        whereArgs: [todayDate]);
  }

  // ============ FOCUS SESSIONS ============

  static Future<String> insertFocusSession(FocusSession session) async {
    final db = await database;
    await db.insert('focus_sessions', session.toMap());
    return session.id;
  }

  static Future<List<FocusSession>> getFocusSessionsByDate(String date) async {
    final db = await database;
    final maps = await db.query('focus_sessions',
        where: 'session_date = ?', whereArgs: [date]);
    return maps.map((m) => FocusSession.fromMap(m)).toList();
  }

  static Future<List<FocusSession>> getFocusSessionsDateRange(String start, String end) async {
    final db = await database;
    final maps = await db.query('focus_sessions',
        where: 'session_date >= ? AND session_date <= ?',
        whereArgs: [start, end]);
    return maps.map((m) => FocusSession.fromMap(m)).toList();
  }

  // ============ COUNTDOWN CRUD ============

  static Future<List<Countdown>> getCountdowns() async {
    final db = await database;
    final maps = await db.query('countdowns', orderBy: 'target_datetime ASC');
    return maps.map((m) => Countdown.fromMap(m)).toList();
  }

  static Future<String> insertCountdown(Countdown countdown) async {
    final db = await database;
    await db.insert('countdowns', countdown.toMap());
    return countdown.id;
  }

  static Future<void> deleteCountdown(String id) async {
    final db = await database;
    await db.delete('countdowns', where: 'id = ?', whereArgs: [id]);
  }

  static Future<void> updateCountdown(Countdown countdown) async {
    final db = await database;
    await db.update('countdowns', countdown.toMap(), where: 'id = ?', whereArgs: [countdown.id]);
  }

  /// Delete countdowns whose target date has passed (next day after target).
  /// Uses UTC for consistent comparison across timezones.
  static Future<void> cleanupExpiredCountdowns() async {
    final db = await database;
    final todayUtc = DateTime.now().toUtc();
    final today = DateTime.utc(todayUtc.year, todayUtc.month, todayUtc.day);
    await db.delete('countdowns',
        where: 'date(target_datetime) < date(?)',
        whereArgs: [today.toIso8601String()]);
  }

  // ============ SLEEP RECORDS ============

  static Future<List<SleepRecord>> getSleepRecords() async {
    final db = await database;
    final maps = await db.query('sleep_records', orderBy: 'record_date DESC');
    return maps.map((m) => SleepRecord.fromMap(m)).toList();
  }

  /// Upsert sleep record by record_date (not by id).
  /// If a record already exists for the same date, update it in-place
  /// (preserving the original id). Otherwise insert a new record.
  static Future<void> upsertSleepRecord(SleepRecord record) async {
    final db = await database;
    // Check if a record already exists for this date
    final existing = await db.query('sleep_records',
        where: 'record_date = ?', whereArgs: [record.recordDate]);
    if (existing.isNotEmpty) {
      // Update existing record (preserve original id)
      final existingId = existing.first['id'] as String;
      final updated = SleepRecord(
        id: existingId,
        recordDate: record.recordDate,
        wakeTime: record.wakeTime,
        sleepTime: record.sleepTime,
        workoutTime: record.workoutTime,
        note: record.note,
      );
      await db.update('sleep_records', updated.toMap(),
          where: 'id = ?', whereArgs: [existingId]);
    } else {
      await db.insert('sleep_records', record.toMap());
    }
  }

  // ============ SETTINGS ============

  static Future<String?> getSetting(String key) async {
    final db = await database;
    final maps = await db.query('user_settings',
        where: 'key = ?', whereArgs: [key]);
    if (maps.isEmpty) return null;
    return maps.first['value'] as String;
  }

  static Future<void> setSetting(String key, String value) async {
    final db = await database;
    await db.insert('user_settings', {'key': key, 'value': value},
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  // ============ Remote sync helpers ============

  static Future<Todo?> getTodoById(String id) async {
    final db = await database;
    final maps = await db.query('todos', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return Todo.fromMap(maps.first);
  }

  static Future<Habit?> getHabitById(String id) async {
    final db = await database;
    final maps = await db.query('habits', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return Habit.fromMap(maps.first);
  }

  static Future<Countdown?> getCountdownById(String id) async {
    final db = await database;
    final maps = await db.query('countdowns', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return Countdown.fromMap(maps.first);
  }

  static Future<SleepRecord?> getSleepRecordById(String id) async {
    final db = await database;
    final maps = await db.query('sleep_records', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return SleepRecord.fromMap(maps.first);
  }

  static Future<void> upsertTodoFromRemote(Map<String, dynamic> row) async {
    final db = await database;
    await db.insert('todos', {
      'id': row['id'],
      'title': row['title'],
      'timing_type': row['timing_type'],
      'duration_minutes': row['duration_minutes'],
      'is_completed': (row['is_completed'] is bool)
          ? (row['is_completed'] ? 1 : 0)
          : row['is_completed'] as int,
      'sort_order': row['sort_order'] ?? 0,
      'keep_tomorrow': (row['keep_tomorrow'] is bool)
          ? (row['keep_tomorrow'] ? 1 : 0)
          : row['keep_tomorrow'] as int,
      'created_date': row['created_date'],
      'completed_date': row['completed_date'],
      'actual_duration_seconds': row['actual_duration_seconds'] ?? 0,
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }

  static Future<void> upsertHabitFromRemote(Map<String, dynamic> row) async {
    final db = await database;
    await db.insert('habits', {
      'id': row['id'],
      'title': row['title'],
      'target_count': row['target_count'],
      'unit': row['unit'],
      'sort_order': row['sort_order'] ?? 0,
      'created_date': row['created_date'],
      'current_count': row['current_count'] ?? 0,
      'last_reset_date': row['last_reset_date'],
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }

  static Future<void> upsertCountdownFromRemote(Map<String, dynamic> row) async {
    final db = await database;
    await db.insert('countdowns', {
      'id': row['id'],
      'title': row['title'],
      'target_datetime': row['target_datetime'],
      'created_date': row['created_date'],
      'color_index': row['color_index'] ?? 0,
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }

  static Future<void> upsertSleepFromRemote(Map<String, dynamic> row) async {
    final db = await database;
    await db.insert('sleep_records', {
      'id': row['id'],
      'record_date': row['record_date'],
      'wake_time': row['wake_time'],
      'sleep_time': row['sleep_time'],
      'workout_time': row['workout_time'],
      'note': row['note'],
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }

  static Future<void> insertFocusSessionIfNotExists(Map<String, dynamic> row) async {
    final db = await database;
    final existing = await db.query('focus_sessions',
        where: 'id = ?', whereArgs: [row['id']]);
    if (existing.isEmpty) {
      await db.insert('focus_sessions', {
        'id': row['id'],
        'todo_id': row['todo_id'],
        'source_type': row['source_type'],
        'source_title': row['source_title'],
        'start_time': row['start_time'],
        'end_time': row['end_time'],
        'duration_seconds': row['duration_seconds'],
        'session_date': row['session_date'],
      });
    }
  }
}
