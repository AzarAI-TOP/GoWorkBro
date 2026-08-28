import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../../../models/models.dart';
import '../../sync/sync_compare.dart';
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

  /// Deletes the row for [recordDate] — the business key. Used by pull-side
  /// delete reconciliation, which only knows the key, not the row id.
  static Future<void> deleteByRecordDate(String recordDate) async {
    final db = await AppDatabase.database;
    await db.delete(
      'sleep_records',
      where: 'record_date = ?',
      whereArgs: [recordDate],
    );
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
  static Future<SleepRecord> upsert(SleepRecord record) async {
    final db = await AppDatabase.database;
    final stamp = nowStamp();
    return db.transaction((txn) async {
      final existing = await txn.query(
        'sleep_records',
        where: 'record_date = ?',
        whereArgs: [record.recordDate],
      );
      if (existing.isEmpty) {
        final inserted = record.copyWith(updatedAt: stamp);
        await txn.insert('sleep_records', inserted.toMap());
        return inserted;
      }

      final current = SleepRecord.fromMap(existing.first);
      final updated = SleepRecord(
        id: current.id,
        recordDate: record.recordDate,
        wakeTime: record.wakeTime ?? current.wakeTime,
        sleepTime: record.sleepTime ?? current.sleepTime,
        workoutTime: record.workoutTime ?? current.workoutTime,
        workoutDurationMinutes:
            record.workoutDurationMinutes ?? current.workoutDurationMinutes,
        note: record.note ?? current.note,
        updatedAt: stamp,
      );
      await txn.update(
        'sleep_records',
        updated.toMap(),
        where: 'id = ?',
        whereArgs: [current.id],
      );
      return updated;
    });
  }

  /// Updates only workout-owned fields for [recordDate].
  ///
  /// Reading and patching inside one transaction prevents a long-lived UI
  /// sheet from writing stale sleep/wake values back over a newer sync event.
  static Future<SleepRecord> upsertWorkout({
    required String recordDate,
    required int durationMinutes,
    required String description,
  }) async {
    final db = await AppDatabase.database;
    final stamp = nowStamp();
    return db.transaction((txn) async {
      final existing = await txn.query(
        'sleep_records',
        where: 'record_date = ?',
        whereArgs: [recordDate],
      );
      if (existing.isEmpty) {
        final inserted = SleepRecord.create(
          recordDate: recordDate,
          workoutDurationMinutes: durationMinutes,
          note: description,
          updatedAt: stamp,
        );
        await txn.insert('sleep_records', inserted.toMap());
        return inserted;
      }

      final id = existing.first['id'] as String;
      await txn.update(
        'sleep_records',
        {
          'workout_duration_minutes': durationMinutes,
          'note': description,
          'updated_at': stamp,
        },
        where: 'id = ?',
        whereArgs: [id],
      );
      final rows = await txn.query(
        'sleep_records',
        where: 'id = ?',
        whereArgs: [id],
      );
      return SleepRecord.fromMap(rows.single);
    });
  }

  /// Upsert a row pushed by the cloud sync (schema-normalized).
  /// Read-compare-write runs in one transaction so a concurrent local edit
  /// cannot be overwritten by a stale check result.
  static Future<void> upsertFromRemote(Map<String, dynamic> row) async {
    final db = await AppDatabase.database;
    final recordDate = row['record_date'] as String;
    final normalized = <String, Object?>{
      'id': row['id'],
      'record_date': recordDate,
      'wake_time': row['wake_time'],
      'sleep_time': row['sleep_time'],
      'workout_time': row['workout_time'],
      'workout_duration_minutes': row['workout_duration_minutes'],
      'note': row['note'],
      'updated_at': row['updated_at'],
    };
    await db.transaction((txn) async {
      final existing = await txn.query(
        'sleep_records',
        columns: ['id', 'updated_at'],
        where: 'record_date = ?',
        whereArgs: [recordDate],
      );
      if (existing.isNotEmpty &&
          isLocalNewer(
            existing.first['updated_at'] as String?,
            row['updated_at'] as String?,
          )) {
        return;
      }
      // record_date is the business key. Adopt the cloud UUID so subsequent
      // realtime updates/deletes target the same row on every device.
      await txn.delete(
        'sleep_records',
        where: 'record_date = ? AND id <> ?',
        whereArgs: [recordDate, normalized['id']],
      );
      await txn.insert(
        'sleep_records',
        normalized,
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    });
  }
}
