import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../../../models/models.dart';
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
    await db.insert('habits', habit.toMap());
    return habit.id;
  }

  static Future<void> update(Habit habit) async {
    final db = await AppDatabase.database;
    await db.update(
      'habits',
      habit.toMap(),
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
  static Future<void> resetForNewDay(String todayDate) async {
    final db = await AppDatabase.database;
    await db.update(
      'habits',
      {'current_count': 0, 'last_reset_date': todayDate},
      where: 'last_reset_date != ? OR last_reset_date IS NULL',
      whereArgs: [todayDate],
    );
  }

  /// Upsert a row pushed by the cloud sync (schema-normalized).
  static Future<void> upsertFromRemote(Map<String, dynamic> row) async {
    final db = await AppDatabase.database;
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
}
