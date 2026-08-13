import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../../../models/models.dart';
import '../../sync/sync_compare.dart';
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
    await db.insert('todos', todo.toMap()..['updated_at'] = nowStamp());
    return todo.id;
  }

  static Future<void> update(Todo todo) async {
    final db = await AppDatabase.database;
    await db.update(
      'todos',
      todo.toMap()..['updated_at'] = nowStamp(),
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
  ///
  /// Returns the ids of the deleted rows so the caller can mirror the
  /// deletion to the cloud (otherwise the next pull resurrects them).
  static Future<List<String>> rollOver(String todayDate) async {
    final db = await AppDatabase.database;
    final rows = await db.query(
      'todos',
      columns: ['id'],
      where: 'is_completed = 1',
    );
    final ids = rows.map((r) => r['id'] as String).toList();
    if (ids.isNotEmpty) {
      await db.delete('todos', where: 'is_completed = 1');
    }
    return ids;
  }

  /// Upsert a row pushed by the cloud sync (schema-normalized),
  /// last-write-wins by `updated_at`: a remote row older than the local one
  /// is discarded instead of replacing newer local edits.
  static Future<void> upsertFromRemote(Map<String, dynamic> row) async {
    final db = await AppDatabase.database;
    final id = row['id'] as String;
    final existing = await db.query(
      'todos',
      columns: ['updated_at'],
      where: 'id = ?',
      whereArgs: [id],
    );
    if (existing.isNotEmpty &&
        isLocalNewer(
          existing.first['updated_at'] as String?,
          row['updated_at'] as String?,
        )) {
      return; // local is newer — keep local
    }
    await db.insert('todos', {
      'id': id,
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
      'updated_at': row['updated_at'],
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }
}
