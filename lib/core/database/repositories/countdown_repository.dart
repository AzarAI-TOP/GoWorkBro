import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../../../models/models.dart';
import '../app_database.dart';

/// Countdowns data access.
abstract final class CountdownRepository {
  static Future<List<Countdown>> getAll() async {
    final db = await AppDatabase.database;
    final maps = await db.query('countdowns', orderBy: 'target_datetime ASC');
    return maps.map((m) => Countdown.fromMap(m)).toList();
  }

  static Future<String> insert(Countdown countdown) async {
    final db = await AppDatabase.database;
    await db.insert('countdowns', countdown.toMap());
    return countdown.id;
  }

  static Future<void> update(Countdown countdown) async {
    final db = await AppDatabase.database;
    await db.update(
      'countdowns',
      countdown.toMap(),
      where: 'id = ?',
      whereArgs: [countdown.id],
    );
  }

  static Future<void> deleteById(String id) async {
    final db = await AppDatabase.database;
    await db.delete('countdowns', where: 'id = ?', whereArgs: [id]);
  }

  static Future<Countdown?> getById(String id) async {
    final db = await AppDatabase.database;
    final maps = await db.query('countdowns', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return Countdown.fromMap(maps.first);
  }

  /// Delete countdowns whose target date has passed (next day after target).
  /// Uses UTC for consistent comparison across timezones.
  static Future<void> cleanupExpired() async {
    final db = await AppDatabase.database;
    final todayUtc = DateTime.now().toUtc();
    final today = DateTime.utc(todayUtc.year, todayUtc.month, todayUtc.day);
    await db.delete(
      'countdowns',
      where: 'date(target_datetime) < date(?)',
      whereArgs: [today.toIso8601String()],
    );
  }

  /// Upsert a row pushed by the cloud sync (schema-normalized).
  static Future<void> upsertFromRemote(Map<String, dynamic> row) async {
    final db = await AppDatabase.database;
    await db.insert('countdowns', {
      'id': row['id'],
      'title': row['title'],
      'target_datetime': row['target_datetime'],
      'created_date': row['created_date'],
      'color_index': row['color_index'] ?? 0,
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }
}
