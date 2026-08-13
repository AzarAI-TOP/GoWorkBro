import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../../../models/models.dart';
import '../app_database.dart';

/// Todos data access. Domain-agnostic SQL lives in [AppDatabase].
abstract final class TodoRepository {
  static Future<List<Todo>> getAll() async {
    final db = await AppDatabase.database;
    final maps = await db.query('todos', orderBy: 'sort_order ASC');
    return maps.map((m) => Todo.fromMap(m)).toList();
  }

  static Future<String> insert(Todo todo) async {
    final db = await AppDatabase.database;
    await db.insert('todos', todo.toMap());
    return todo.id;
  }

  static Future<void> update(Todo todo) async {
    final db = await AppDatabase.database;
    await db.update(
      'todos',
      todo.toMap(),
      where: 'id = ?',
      whereArgs: [todo.id],
    );
  }

  static Future<void> deleteById(String id) async {
    final db = await AppDatabase.database;
    await db.delete('todos', where: 'id = ?', whereArgs: [id]);
  }

  static Future<Todo?> getById(String id) async {
    final db = await AppDatabase.database;
    final maps = await db.query('todos', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return Todo.fromMap(maps.first);
  }

  /// Roll over todos for a new day.
  /// With the "明天继续" semantics, completed todos with keepTomorrow=true
  /// already had an incomplete copy created at completion time.
  /// So at rollover we simply delete all completed todos (both keep and
  /// non-keep). Incomplete todos carry over automatically (no action needed).
  static Future<void> rollOver(String todayDate) async {
    final db = await AppDatabase.database;
    // Delete completed — keepTomorrow copies were already created at
    // completion time.
    await db.delete('todos', where: 'is_completed = 1');
  }

  /// Upsert a row pushed by the cloud sync (schema-normalized).
  static Future<void> upsertFromRemote(Map<String, dynamic> row) async {
    final db = await AppDatabase.database;
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
}
