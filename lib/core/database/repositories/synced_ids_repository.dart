import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../app_database.dart';

/// Bookkeeping for cross-device delete propagation: the set of remote row
/// ids observed by the last successful pull, per table.
///
/// A row that IS in this set but is ABSENT from the next pull response was
/// deleted on another device — SyncService removes it locally instead of
/// letting pushAll re-upload it. Rows created locally are never in this set
/// until they have been observed remotely, so unsynced local work is safe.
abstract final class SyncedIdsRepository {
  static Future<Set<String>> getIds(String table) async {
    final db = await AppDatabase.database;
    final rows = await db.query(
      'synced_remote_ids',
      columns: ['row_id'],
      where: 'table_name = ?',
      whereArgs: [table],
    );
    return {for (final row in rows) row['row_id'] as String};
  }

  /// Atomically replaces the recorded id set for [table] with the ids of the
  /// pull snapshot that was just applied.
  static Future<void> replaceAll(String table, Set<String> ids) async {
    final db = await AppDatabase.database;
    await db.transaction((txn) async {
      await txn.delete(
        'synced_remote_ids',
        where: 'table_name = ?',
        whereArgs: [table],
      );
      for (final id in ids) {
        await txn.insert(
          'synced_remote_ids',
          {'table_name': table, 'row_id': id},
          conflictAlgorithm: ConflictAlgorithm.ignore,
        );
      }
    });
  }
}
