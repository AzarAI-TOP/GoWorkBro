import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../../../models/models.dart';
import '../app_database.dart';

/// Sleep records data access.
abstract final class SleepRepository {
  static Future<List<SleepRecord>> getAll() async {
    final db = await AppDatabase.database;
    final maps = await db.query('sleep_records', orderBy: 'record_date DESC');
    return maps.map((m) => SleepRecord.fromMap(m)).toList();
  }

  static Future<void> deleteById(String id) async {
    final db = await AppDatabase.database;
    await db.delete('sleep_records', where: 'id = ?', whereArgs: [id]);
  }

  static Future<SleepRecord?> getById(String id) async {
    final db = await AppDatabase.database;
    final maps = await db.query(
      'sleep_records',
      where: 'id = ?',
      whereArgs: [id],
    );
    if (maps.isEmpty) return null;
    return SleepRecord.fromMap(maps.first);
  }

  /// Upsert sleep record by record_date (not by id).
  /// If a record already exists for the same date, update it in-place
  /// (preserving the original id). Otherwise insert a new record.
  static Future<void> upsert(SleepRecord record) async {
    final db = await AppDatabase.database;
    // Check if a record already exists for this date
    final existing = await db.query(
      'sleep_records',
      where: 'record_date = ?',
      whereArgs: [record.recordDate],
    );
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
      await db.update(
        'sleep_records',
        updated.toMap(),
        where: 'id = ?',
        whereArgs: [existingId],
      );
    } else {
      await db.insert('sleep_records', record.toMap());
    }
  }

  /// Upsert a row pushed by the cloud sync (schema-normalized).
  static Future<void> upsertFromRemote(Map<String, dynamic> row) async {
    final db = await AppDatabase.database;
    await db.insert('sleep_records', {
      'id': row['id'],
      'record_date': row['record_date'],
      'wake_time': row['wake_time'],
      'sleep_time': row['sleep_time'],
      'workout_time': row['workout_time'],
      'note': row['note'],
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }
}
