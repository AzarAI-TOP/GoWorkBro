import 'package:flutter/foundation.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../../sync/sync_compare.dart';
import '../app_database.dart';

/// Key-value user settings + lifetime counters.
///
/// User-facing profile and logical-day settings are pushed by SyncService.
/// Per-setting timestamp/dirty metadata remains device-local.
abstract final class SettingsRepository {
  static String _syncUpdatedAtKey(String key) => 'sync.meta.$key.updated_at';
  static String _syncDirtyKey(String key) => 'sync.meta.$key.dirty';

  static Future<String?> get(String key) async {
    final db = await AppDatabase.database;
    final maps = await db.query(
      'user_settings',
      where: 'key = ?',
      whereArgs: [key],
    );
    if (maps.isEmpty) return null;
    return maps.first['value'] as String;
  }

  static Future<void> set(String key, String value) async {
    final db = await AppDatabase.database;
    await db.insert('user_settings', {
      'key': key,
      'value': value,
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }

  static Future<void> delete(String key) async {
    final db = await AppDatabase.database;
    await db.delete('user_settings', where: 'key = ?', whereArgs: [key]);
  }

  static Future<void> setSyncedLocal(
    String key,
    String value, {
    String? updatedAt,
  }) async {
    final db = await AppDatabase.database;
    final stamp = updatedAt ?? nowStamp();
    await db.transaction((txn) async {
      await txn.insert('user_settings', {
        'key': key,
        'value': value,
      }, conflictAlgorithm: ConflictAlgorithm.replace);
      await txn.insert('user_settings', {
        'key': _syncUpdatedAtKey(key),
        'value': stamp,
      }, conflictAlgorithm: ConflictAlgorithm.replace);
      await txn.insert('user_settings', {
        'key': _syncDirtyKey(key),
        'value': 'true',
      }, conflictAlgorithm: ConflictAlgorithm.replace);
    });
  }

  static Future<String?> getSyncUpdatedAt(String key) =>
      get(_syncUpdatedAtKey(key));

  static Future<bool> isSyncDirty(String key) async =>
      await get(_syncDirtyKey(key)) == 'true';

  /// Applies a cloud setting only when it is not older than the local edit.
  /// Returns whether the remote value was accepted.
  static Future<bool> applySyncedRemote({
    required String key,
    required String value,
    required String? updatedAt,
    bool preferLexicographicallyGreaterValue = false,
  }) async {
    final db = await AppDatabase.database;
    return db.transaction((txn) async {
      final localStamp = await _getValue(txn, _syncUpdatedAtKey(key));
      final localValue = await _getValue(txn, key);
      final remoteAdvancesValue =
          preferLexicographicallyGreaterValue &&
          localValue != null &&
          localValue.compareTo(value) < 0;
      if (!remoteAdvancesValue && isLocalNewer(localStamp, updatedAt)) {
        return false;
      }

      await txn.insert('user_settings', {
        'key': key,
        'value': value,
      }, conflictAlgorithm: ConflictAlgorithm.replace);
      if (updatedAt != null && updatedAt.isNotEmpty) {
        await txn.insert('user_settings', {
          'key': _syncUpdatedAtKey(key),
          'value': updatedAt,
        }, conflictAlgorithm: ConflictAlgorithm.replace);
      }
      await txn.delete(
        'user_settings',
        where: 'key = ?',
        whereArgs: [_syncDirtyKey(key)],
      );
      return true;
    });
  }

  /// Clears the outbox marker only if no newer local edit happened while the
  /// network request was in flight.
  static Future<void> markSyncComplete(
    String key, {
    required String expectedUpdatedAt,
  }) async {
    final db = await AppDatabase.database;
    await db.transaction((txn) async {
      final current = await _getValue(txn, _syncUpdatedAtKey(key));
      if (current != expectedUpdatedAt) return;
      await txn.delete(
        'user_settings',
        where: 'key = ?',
        whereArgs: [_syncDirtyKey(key)],
      );
    });
  }

  static Future<void> deleteSyncedState(String key) async {
    final db = await AppDatabase.database;
    await db.delete(
      'user_settings',
      where: 'key IN (?, ?, ?)',
      whereArgs: [key, _syncUpdatedAtKey(key), _syncDirtyKey(key)],
    );
  }

  static Future<String?> _getValue(DatabaseExecutor db, String key) async {
    final rows = await db.query(
      'user_settings',
      columns: ['value'],
      where: 'key = ?',
      whereArgs: [key],
      limit: 1,
    );
    return rows.isEmpty ? null : rows.first['value'] as String;
  }

  /// Increments [counterKey] once per [eventKey] occurrence.
  /// Returns the new counter value (unchanged if the event already fired).
  static Future<int> incrementCounterOnce({
    required String counterKey,
    required String eventKey,
  }) async {
    return _incrementCounterOnce(
      await AppDatabase.database,
      counterKey: counterKey,
      eventKey: eventKey,
    );
  }

  @visibleForTesting
  static Future<int> incrementCounterOnceForTesting(
    Database db, {
    required String counterKey,
    required String eventKey,
  }) => _incrementCounterOnce(db, counterKey: counterKey, eventKey: eventKey);

  static Future<int> _incrementCounterOnce(
    Database db, {
    required String counterKey,
    required String eventKey,
  }) async {
    return db.transaction((txn) async {
      final marker = await txn.query(
        'user_settings',
        columns: ['value'],
        where: 'key = ?',
        whereArgs: [eventKey],
        limit: 1,
      );
      final rows = await txn.query(
        'user_settings',
        columns: ['value'],
        where: 'key = ?',
        whereArgs: [counterKey],
      );
      final current = rows.isEmpty
          ? 0
          : int.tryParse(rows.first['value'] as String? ?? '') ?? 0;
      if (marker.isNotEmpty) return current;

      final next = current + 1;
      await txn.insert('user_settings', {
        'key': counterKey,
        'value': '$next',
      }, conflictAlgorithm: ConflictAlgorithm.replace);
      await txn.insert('user_settings', {
        'key': eventKey,
        'value': DateTime.now().toIso8601String(),
      }, conflictAlgorithm: ConflictAlgorithm.ignore);
      return next;
    });
  }
}
