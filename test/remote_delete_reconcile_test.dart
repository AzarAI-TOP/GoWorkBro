import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import 'package:goworkbro/core/database/app_database.dart';
import 'package:goworkbro/core/database/repositories/settings_repository.dart';
import 'package:goworkbro/core/database/repositories/sleep_repository.dart';
import 'package:goworkbro/core/database/repositories/synced_ids_repository.dart';
import 'package:goworkbro/core/database/repositories/todo_repository.dart';
import 'package:goworkbro/core/sync/sync_service.dart';
import 'package:goworkbro/core/sync/sync_table_registry.dart';
import 'package:goworkbro/models/models.dart';

void main() {
  late Directory tempDir;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('goworkbro_reconcile_');
    AppDatabase.setDataDirForTesting(tempDir.path);
  });

  tearDown(() async {
    await AppDatabase.closeForTesting();
    await tempDir.delete(recursive: true);
  });

  Future<void> insertTodo(String id) => TodoRepository.insert(
        Todo(
          id: id,
          title: id,
          timingType: TimingTypeExtension.fromValue('none'),
          createdDate: '2026-08-28T00:00:00',
        ),
      );

  group('cross-device delete propagation', () {
    test('rows seen remotely but now absent are deleted locally', () async {
      final todosTable = syncTables.firstWhere((t) => t.name == 'todos');
      // Local state: a and b were synced before, c was created locally and
      // never pushed, d was already deleted locally (stale synced entry).
      await insertTodo('a');
      await insertTodo('b');
      await insertTodo('c');
      await SyncedIdsRepository.replaceAll('todos', {'a', 'b', 'd'});

      // Remote snapshot after another device deleted b (and d earlier).
      await SyncService.reconcileRemoteDeletes(todosTable, {'a'});

      final remaining = (await TodoRepository.getAll()).map((t) => t.id).toSet();
      expect(remaining, {'a', 'c'},
          reason: 'vanished rows go, unsynced local rows stay');
      expect(await SyncedIdsRepository.getIds('todos'), {'a'});
    });

    test('synced id bookkeeping roundtrip', () async {
      expect(await SyncedIdsRepository.getIds('todos'), isEmpty);
      await SyncedIdsRepository.replaceAll('todos', {'x', 'y'});
      expect(await SyncedIdsRepository.getIds('todos'), {'x', 'y'});
      await SyncedIdsRepository.replaceAll('todos', {'x'});
      expect(await SyncedIdsRepository.getIds('todos'), {'x'});
    });

    test('sleep reconciliation keys on record_date, not the mutable row id',
        () async {
      final sleepTable = syncTables.firstWhere((t) => t.name == 'sleep_records');
      // Yesterday's record was seen remotely; today's was created locally
      // and never synced.
      await SleepRepository.upsert(
        SleepRecord.create(recordDate: '2026-08-27', updatedAt: '2026-08-27T06:00:00Z'),
      );
      await SleepRepository.upsert(
        SleepRecord.create(recordDate: '2026-08-28', updatedAt: '2026-08-28T06:00:00Z'),
      );
      await SyncedIdsRepository.replaceAll('sleep_records', {'2026-08-27'});

      // Remote snapshot: 08-27 deleted on another device, 08-28 never pushed.
      await SyncService.reconcileRemoteDeletes(sleepTable, {});

      final remaining = (await SleepRepository.getAll())
          .map((r) => r.recordDate)
          .toSet();
      expect(remaining, {'2026-08-28'},
          reason: 'vanished date goes, unsynced local date stays');
      expect(await SyncedIdsRepository.getIds('sleep_records'), isEmpty);
    });

    test('deleteAllData clears the synced id bookkeeping', () async {
      await insertTodo('a');
      await SyncedIdsRepository.replaceAll('todos', {'a'});
      await AppDatabase.deleteAllData();
      expect(await SyncedIdsRepository.getIds('todos'), isEmpty);
    });
  });

  group('user_settings pull is LWW-protected', () {
    final userSettingsTable = syncTables.firstWhere(
      (t) => t.name == 'user_settings',
    );

    test('an offline user_name edit survives an older cloud value', () async {
      await SettingsRepository.setSyncedLocal(
        'user_name',
        '本地改名',
        updatedAt: '2026-08-28T12:00:00Z',
      );

      await userSettingsTable.applyRemote({
        'key': 'user_name',
        'value': 'CloudOld',
        'updated_at': '2026-08-28T10:00:00Z',
      });
      expect(await SettingsRepository.get('user_name'), '本地改名');
      expect(await SettingsRepository.isSyncDirty('user_name'), isTrue);

      // A genuinely newer cloud rename wins and clears the outbox marker.
      await userSettingsTable.applyRemote({
        'key': 'user_name',
        'value': 'CloudNew',
        'updated_at': '2026-08-28T14:00:00Z',
      });
      expect(await SettingsRepository.get('user_name'), 'CloudNew');
      expect(await SettingsRepository.isSyncDirty('user_name'), isFalse);
    });

    test('a pending local avatar edit is not rolled back by an old cloud row',
        () async {
      await SettingsRepository.setSyncedLocal(
        'avatar_path',
        r'C:\users\me\picked.jpg',
        updatedAt: '2026-08-28T12:00:00Z',
      );

      await userSettingsTable.applyRemote({
        'key': 'avatar_path',
        'value': '11111111-1111-1111-1111-111111111111/avatar.jpg',
        'updated_at': '2026-08-28T10:00:00Z',
      });

      expect(
        await SettingsRepository.get('avatar_path'),
        r'C:\users\me\picked.jpg',
      );
      expect(await SettingsRepository.isSyncDirty('avatar_path'), isTrue);
    });
  });

  group('v8 -> v9 migration', () {
    test('creates synced_remote_ids', () async {
      sqfliteFfiInit();
      final temp = await Directory.systemTemp.createTemp('goworkbro_v9_');
      final path = '${temp.path}${Platform.pathSeparator}v9.db';
      final db = await databaseFactoryFfi.openDatabase(path);
      addTearDown(() async {
        await db.close();
        await temp.delete(recursive: true);
      });

      await AppDatabase.migrateForTesting(db, 8, 9);

      final tables = await db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='synced_remote_ids'",
      );
      expect(tables, isNotEmpty);
    });
  });
}
