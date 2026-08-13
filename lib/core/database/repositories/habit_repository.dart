import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../../../models/models.dart';
import '../../sync/sync_compare.dart';
import '../app_database.dart';

/// Habits data access.
abstract final class HabitRepository {
  static Future<List<Habit>> getAll() async {
    final db = await AppDatabase.database;
    final maps = await db.query('habits', orderBy: 'sort_order ASC');
    return maps.map((m) => Habit.fromMap(m)).toList();
  }

  static Future<String> insert(Habit habit) async {
    final db = await AppDatabase.database;
    await db.insert('habits', habit.toMap()..['updated_at'] = nowStamp());
    return habit.id;
  }

  static Future<void> update(Habit habit) async {
    final db = await AppDatabase.database;
    await db.update(
      'habits',
      habit.toMap()..['updated_at'] = nowStamp(),
      where: 'id = ?',
      whereArgs: [habit.id],
    );
  }

  static Future<void> deleteById(String id) async {
    final db = await AppDatabase.database;
    await db.delete('habits', where: 'id = ?', whereArgs: [id]);
  }

  static Future<Habit?> getById(String id) async {
    final db = await AppDatabase.database;
    final maps = await db.query('habits', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return Habit.fromMap(maps.first);
  }

  /// Reset all habit counts at the start of a new day.
  ///
  /// Stamps `updated_at` so the reset propagates to the cloud — without it
  /// the other device would keep yesterday's counts until its own rollover.
  static Future<void> resetForNewDay(String todayDate) async {
    final db = await AppDatabase.database;
    await db.update(
      'habits',
      {'current_count': 0, 'last_reset_date': todayDate, 'updated_at': nowStamp()},
      where: 'last_reset_date != ? OR last_reset_date IS NULL',
      whereArgs: [todayDate],
    );
  }

  /// Upsert a row pushed by the cloud sync (schema-normalized),
  /// last-write-wins by `updated_at`.
  static Future<void> upsertFromRemote(Map<String, dynamic> row) async {
    final db = await AppDatabase.database;
    final id = row['id'] as String;
    final existing = await db.query(
      'habits',
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
    await db.insert('habits', {
      'id': id,
      'title': row['title'],
      'target_count': row['target_count'],
      'unit': row['unit'],
      'sort_order': row['sort_order'] ?? 0,
      'created_date': row['created_date'],
      'current_count': row['current_count'] ?? 0,
      'last_reset_date': row['last_reset_date'],
      'updated_at': row['updated_at'],
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }
}
