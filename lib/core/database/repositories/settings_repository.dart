import 'package:flutter/foundation.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../app_database.dart';

/// Key-value user settings + lifetime counters.
///
/// Synced profile keys (user_name, avatar_path) are pushed to the cloud by
/// SyncService; the remaining keys are device-local.
abstract final class SettingsRepository {
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
  }) => _incrementCounterOnce(
    db,
    counterKey: counterKey,
    eventKey: eventKey,
  );

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
