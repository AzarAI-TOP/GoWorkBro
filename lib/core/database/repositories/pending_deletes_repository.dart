import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../app_database.dart';

/// Delete tombstones — the outbox for locally deleted rows whose remote
/// delete has not been confirmed yet.
///
/// A row is recorded in the same transaction as the local delete, blocks
/// pull/realtime from re-inserting the remote copy, and is cleared only
/// once the remote delete succeeds (direct retry or the realtime echo of
/// our own delete).
abstract final class PendingDeletesRepository {
  /// Records tombstones inside an ongoing transaction. Use [recordMany].
  static Future<void> record(
    DatabaseExecutor txn,
    String table,
    String rowId,
  ) async {
    await txn.insert(
      'pending_deletes',
      {
        'table_name': table,
        'row_id': rowId,
        'queued_at': DateTime.now().toUtc().toIso8601String(),
      },
      conflictAlgorithm: ConflictAlgorithm.ignore,
    );
  }

  static Future<void> recordMany(
    DatabaseExecutor txn,
    String table,
    Iterable<String> rowIds,
  ) async {
    for (final id in rowIds) {
      await record(txn, table, id);
    }
  }

  /// True when [rowId] in [table] was deleted locally and the remote delete
  /// is still pending — pull must skip such rows instead of resurrecting them.
  /// Pass the active transaction's executor when called inside one.
  static Future<bool> contains(
    DatabaseExecutor db,
    String table,
    String rowId,
  ) async {
    final rows = await db.query(
      'pending_deletes',
      columns: ['row_id'],
      where: 'table_name = ? AND row_id = ?',
      whereArgs: [table, rowId],
      limit: 1,
    );
    return rows.isNotEmpty;
  }

  static Future<List<(String, String)>> takePending() async {
    final db = await AppDatabase.database;
    final rows = await db.query('pending_deletes');
    return [
      for (final row in rows)
        (row['table_name'] as String, row['row_id'] as String),
    ];
  }

  /// Clears a tombstone after the remote delete was confirmed (server
  /// accepted our delete, or the realtime DELETE echo arrived).
  static Future<void> clear(String table, String rowId) async {
    final db = await AppDatabase.database;
    await db.delete(
      'pending_deletes',
      where: 'table_name = ? AND row_id = ?',
      whereArgs: [table, rowId],
    );
  }
}
