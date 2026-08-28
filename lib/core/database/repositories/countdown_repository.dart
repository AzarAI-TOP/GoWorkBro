import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../../../models/models.dart';
import '../../sync/sync_compare.dart';
import '../app_database.dart';
import 'pending_deletes_repository.dart';

/// Countdowns data access.
abstract final class CountdownRepository {
  static Future<List<Countdown>> getAll() async {
    final db = await AppDatabase.database;
    final maps = await db.query('countdowns', orderBy: 'target_datetime ASC');
    return maps.map((m) => Countdown.fromMap(m)).toList();
  }

  static Future<String> insert(Countdown countdown) async {
    final db = await AppDatabase.database;
    await db.insert('countdowns', countdown.toMap()..['updated_at'] = nowStamp());
    return countdown.id;
  }

  static Future<void> update(Countdown countdown) async {
    final db = await AppDatabase.database;
    await db.update(
      'countdowns',
      countdown.toMap()..['updated_at'] = nowStamp(),
      where: 'id = ?',
      whereArgs: [countdown.id],
    );
  }

  static Future<void> deleteById(String id) async {
    final db = await AppDatabase.database;
    await db.delete('countdowns', where: 'id = ?', whereArgs: [id]);
  }

  /// Locally-initiated delete: drops the row and queues a tombstone in the
  /// same transaction so a pending remote delete can never be undone by the
  /// next pull resurrecting the row.
  static Future<void> deleteWithTombstone(String id) async {
    final db = await AppDatabase.database;
    await db.transaction((txn) async {
      await txn.delete('countdowns', where: 'id = ?', whereArgs: [id]);
      await PendingDeletesRepository.record(txn, 'countdowns', id);
    });
  }

  static Future<Countdown?> getById(String id) async {
    final db = await AppDatabase.database;
    final maps = await db.query('countdowns', where: 'id = ?', whereArgs: [id]);
    if (maps.isEmpty) return null;
    return Countdown.fromMap(maps.first);
  }

  /// Delete countdowns whose target date has passed (next day after target).
  /// Uses UTC for consistent comparison across timezones.
  ///
  /// Returns the ids of the deleted rows so the caller can mirror the
  /// deletion to the cloud (otherwise the next pull resurrects them).
  static Future<List<String>> cleanupExpired() async {
    final db = await AppDatabase.database;
    final todayUtc = DateTime.now().toUtc();
    final today = DateTime.utc(todayUtc.year, todayUtc.month, todayUtc.day);
    return db.transaction((txn) async {
      final rows = await txn.query(
        'countdowns',
        columns: ['id'],
        where: 'date(target_datetime) < date(?)',
        whereArgs: [today.toIso8601String()],
      );
      final ids = rows.map((r) => r['id'] as String).toList();
      if (ids.isNotEmpty) {
        await txn.delete(
          'countdowns',
          where: 'date(target_datetime) < date(?)',
          whereArgs: [today.toIso8601String()],
        );
        await PendingDeletesRepository.recordMany(txn, 'countdowns', ids);
      }
      return ids;
    });
  }

  /// Upsert a row pushed by the cloud sync (schema-normalized),
  /// last-write-wins by `updated_at`. A tombstoned row is skipped so a
  /// pending remote delete is not resurrected. Read-compare-write runs in
  /// one transaction.
  static Future<void> upsertFromRemote(Map<String, dynamic> row) async {
    final db = await AppDatabase.database;
    final id = row['id'] as String;
    await db.transaction((txn) async {
      if (await PendingDeletesRepository.contains(txn, 'countdowns', id)) {
        return;
      }
      final existing = await txn.query(
        'countdowns',
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
      await txn.insert('countdowns', {
        'id': id,
        'title': row['title'],
        'target_datetime': row['target_datetime'],
        'created_date': row['created_date'],
        'color_index': row['color_index'] ?? 0,
        'updated_at': row['updated_at'],
      }, conflictAlgorithm: ConflictAlgorithm.replace);
    });
  }
}
