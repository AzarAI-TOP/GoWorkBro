import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/core/database/app_database.dart';
import 'package:goworkbro/core/database/repositories/countdown_repository.dart';
import 'package:goworkbro/core/database/repositories/focus_repository.dart';
import 'package:goworkbro/core/database/repositories/habit_repository.dart';
import 'package:goworkbro/core/database/repositories/settings_repository.dart';
import 'package:goworkbro/core/database/repositories/sleep_repository.dart';
import 'package:goworkbro/core/database/repositories/todo_repository.dart';
import 'package:goworkbro/core/sync/sync_service.dart';
import 'package:goworkbro/core/sync/sync_table_registry.dart';
import 'package:goworkbro/models/models.dart';

void main() {
  late Directory tempDir;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('goworkbro_repo_');
    AppDatabase.setDataDirForTesting(tempDir.path);
  });

  tearDown(() async {
    await AppDatabase.closeForTesting();
    await tempDir.delete(recursive: true);
  });

  group('TodoRepository', () {
    test('insert / getAll / update / deleteById roundtrip', () async {
      final todo = Todo.create(
        title: '写代码',
        timingType: TimingTypeExtension.fromValue('forward'),
      );
      await TodoRepository.insert(todo);

      final all = await TodoRepository.getAll();
      expect(all, hasLength(1));
      expect(all.first.id, todo.id);
      expect(all.first.title, '写代码');

      final updated = todo.copyWith(title: '写测试');
      await TodoRepository.update(updated);
      final after = await TodoRepository.getAll();
      expect(after.first.title, '写测试');

      await TodoRepository.deleteById(todo.id);
      expect(await TodoRepository.getAll(), isEmpty);
    });

    test('rollOver removes completed todos, keeps incomplete', () async {
      final done = Todo.create(
        title: 'done',
        timingType: TimingTypeExtension.fromValue('forward'),
      ).copyWith(isCompleted: true);
      final pending = Todo.create(
        title: 'pending',
        timingType: TimingTypeExtension.fromValue('forward'),
      );
      await TodoRepository.insert(done);
      await TodoRepository.insert(pending);

      await TodoRepository.rollOver('2026-08-13');

      final all = await TodoRepository.getAll();
      expect(all.map((t) => t.title), ['pending']);
    });

    test(
      'rollOver returns deleted ids so the caller can mirror to cloud',
      () async {
        final done = Todo.create(
          title: 'done',
          timingType: TimingTypeExtension.fromValue('forward'),
        ).copyWith(isCompleted: true);
        final pending = Todo.create(
          title: 'pending',
          timingType: TimingTypeExtension.fromValue('forward'),
        );
        await TodoRepository.insert(done);
        await TodoRepository.insert(pending);

        final ids = await TodoRepository.rollOver('2026-08-14');
        expect(ids, [done.id]);
      },
    );

    test('upsertFromRemote is last-write-wins by updated_at', () async {
      final local = Todo.create(
        title: 'local',
        timingType: TimingTypeExtension.fromValue('forward'),
      );
      await TodoRepository.insert(local);
      final db = await AppDatabase.database;
      final rows = await db.query(
        'todos',
        columns: ['updated_at'],
        where: 'id = ?',
        whereArgs: [local.id],
      );
      final localStamp = rows.first['updated_at'] as String;
      final base = DateTime.parse(localStamp).toUtc();

      Map<String, dynamic> remote(String title, DateTime stamp) => {
        'id': local.id,
        'title': title,
        'timing_type': 'forward',
        'duration_minutes': 25,
        'is_completed': false,
        'sort_order': 0,
        'keep_tomorrow': true,
        'created_date': '2026-08-14',
        'completed_date': null,
        'actual_duration_seconds': 0,
        'updated_at': stamp.toIso8601String(),
      };

      // Older remote row → discarded, local edit survives.
      await TodoRepository.upsertFromRemote(
        remote('stale', base.subtract(const Duration(minutes: 5))),
      );
      expect((await TodoRepository.getAll()).single.title, 'local');

      // Newer remote row → applied.
      await TodoRepository.upsertFromRemote(
        remote('fresh', base.add(const Duration(minutes: 5))),
      );
      final after = await TodoRepository.getAll();
      expect(after.single.title, 'fresh');
      expect(after.single.updatedAt, isNotNull);
    });

    test('upsertFromRemote normalizes bool and int payloads', () async {
      await TodoRepository.upsertFromRemote({
        'id': 'r1',
        'title': 'remote',
        'timing_type': 'forward',
        'duration_minutes': 25,
        'is_completed': true,
        'sort_order': 3,
        'keep_tomorrow': false,
        'created_date': '2026-08-13',
      });
      final all = await TodoRepository.getAll();
      expect(all.single.isCompleted, isTrue);
      expect(all.single.keepTomorrow, isFalse);
      expect(all.single.sortOrder, 3);

      // int payloads also work (idempotent replace)
      await TodoRepository.upsertFromRemote({
        'id': 'r1',
        'title': 'remote',
        'timing_type': 'forward',
        'duration_minutes': 25,
        'is_completed': 0,
        'sort_order': 3,
        'keep_tomorrow': 1,
        'created_date': '2026-08-13',
      });
      final again = await TodoRepository.getAll();
      expect(again, hasLength(1));
      expect(again.single.isCompleted, isFalse);
    });
  });

  group('SettingsRepository', () {
    test('get/set/delete roundtrip', () async {
      expect(await SettingsRepository.get('missing'), isNull);
      await SettingsRepository.set('theme_mode', 'dark');
      expect(await SettingsRepository.get('theme_mode'), 'dark');
      await SettingsRepository.delete('theme_mode');
      expect(await SettingsRepository.get('theme_mode'), isNull);
    });

    test(
      'dirty synced setting rejects older remote and accepts newer remote',
      () async {
        await SettingsRepository.setSyncedLocal(
          'late_night_mode',
          'true',
          updatedAt: '2026-08-17T10:00:00.000Z',
        );
        expect(await SettingsRepository.isSyncDirty('late_night_mode'), isTrue);

        final appliedOlder = await SettingsRepository.applySyncedRemote(
          key: 'late_night_mode',
          value: 'false',
          updatedAt: '2026-08-17T09:00:00.000Z',
        );
        expect(appliedOlder, isFalse);
        expect(await SettingsRepository.get('late_night_mode'), 'true');
        expect(await SettingsRepository.isSyncDirty('late_night_mode'), isTrue);

        final appliedNewer = await SettingsRepository.applySyncedRemote(
          key: 'late_night_mode',
          value: 'false',
          updatedAt: '2026-08-17T11:00:00.000Z',
        );
        expect(appliedNewer, isTrue);
        expect(await SettingsRepository.get('late_night_mode'), 'false');
        expect(
          await SettingsRepository.isSyncDirty('late_night_mode'),
          isFalse,
        );
      },
    );

    test('successful push clears dirty only for the pushed snapshot', () async {
      await SettingsRepository.setSyncedLocal(
        'late_night_mode',
        'true',
        updatedAt: '2026-08-17T10:00:00.000Z',
      );
      await SettingsRepository.markSyncComplete(
        'late_night_mode',
        expectedUpdatedAt: '2026-08-17T09:00:00.000Z',
      );
      expect(await SettingsRepository.isSyncDirty('late_night_mode'), isTrue);

      await SettingsRepository.markSyncComplete(
        'late_night_mode',
        expectedUpdatedAt: '2026-08-17T10:00:00.000Z',
      );
      expect(await SettingsRepository.isSyncDirty('late_night_mode'), isFalse);
    });

    test('default pushes skip clean logical-day settings', () {
      expect(
        SyncService.shouldPushUserSetting(
          key: 'late_night_mode',
          isDirty: false,
          onlyDirty: false,
        ),
        isFalse,
      );
      expect(
        SyncService.shouldPushUserSetting(
          key: 'late_night_mode',
          isDirty: true,
          onlyDirty: false,
          localUpdatedAt: '2026-08-17T10:00:00.000Z',
          remoteUpdatedAt: '2026-08-17T11:00:00.000Z',
          remoteInventoryAvailable: true,
        ),
        isFalse,
      );
      expect(
        SyncService.shouldPushUserSetting(
          key: 'late_night_mode',
          isDirty: true,
          onlyDirty: false,
          localUpdatedAt: '2026-08-17T10:00:00.000Z',
          remoteUpdatedAt: null,
          remoteInventoryAvailable: false,
        ),
        isFalse,
      );
      expect(
        SyncService.shouldPushUserSetting(
          key: 'user_name',
          isDirty: false,
          onlyDirty: false,
        ),
        isTrue,
      );
      expect(
        SyncService.shouldPushUserSetting(
          key: 'user_name',
          isDirty: false,
          onlyDirty: true,
        ),
        isFalse,
      );
      expect(
        SyncService.didServerAcceptUserSetting(
          sentValue: 'true',
          sentUpdatedAt: '2026-08-17T10:00:00.000Z',
          returnedValue: 'true',
          returnedUpdatedAt: '2026-08-17T10:00:00+00:00',
        ),
        isTrue,
      );
      expect(
        SyncService.didServerAcceptUserSetting(
          sentValue: 'true',
          sentUpdatedAt: '2026-08-17T10:00:00.000Z',
          returnedValue: 'false',
          returnedUpdatedAt: '2026-08-17T11:00:00+00:00',
        ),
        isFalse,
      );
    });
  });

  group('sync table registry', () {
    test('registers all six cloud tables with unique names', () {
      final names = syncTables.map((t) => t.name).toList();
      expect(names, [
        'todos',
        'habits',
        'countdowns',
        'sleep_records',
        'focus_sessions',
        'user_settings',
      ]);
      expect(names.toSet(), hasLength(names.length));
    });

    test('user_settings applyRemote skips non-profile keys', () async {
      final settingsTable = syncTables.firstWhere(
        (t) => t.name == 'user_settings',
      );
      await settingsTable.applyRemote({'key': 'locale', 'value': 'en'});
      await settingsTable.applyRemote({'key': 'user_name', 'value': 'AzarAI'});
      await settingsTable.applyRemote({
        'key': 'late_night_closed_through',
        'value': '2026-08-17',
      });

      expect(await SettingsRepository.get('locale'), isNull);
      expect(await SettingsRepository.get('user_name'), 'AzarAI');
      expect(
        await SettingsRepository.get('late_night_closed_through'),
        isNull,
      );
    });

    test('focus_sessions applyRemote is idempotent', () async {
      final focusTable = syncTables.firstWhere(
        (t) => t.name == 'focus_sessions',
      );
      final row = {
        'id': 's1',
        'todo_id': null,
        'source_type': 'todo',
        'source_title': '任务',
        'start_time': '2026-08-13T08:00:00',
        'end_time': '2026-08-13T08:25:00',
        'duration_seconds': 1500,
        'session_date': '2026-08-13',
      };
      await focusTable.applyRemote(row);
      await focusTable.applyRemote(row);

      final sessions = await FocusRepository.getByDate('2026-08-13');
      expect(sessions, hasLength(1));
      expect(sessions.single.durationSeconds, 1500);
    });
  });

  group('AppDatabase schema', () {
    test('creates all tables with defaults', () async {
      final db = await AppDatabase.database;
      final tables = await db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table'",
      );
      final names = tables.map((t) => t['name']).toSet();
      expect(
        names,
        containsAll([
          'todos',
          'habits',
          'focus_sessions',
          'countdowns',
          'sleep_records',
          'user_settings',
          'ustc_news_cache',
        ]),
      );

      // default profile rows seeded on fresh install
      expect(await SettingsRepository.get('user_name'), '离线用户');
      expect(await SettingsRepository.get('lifetime_todos_completed'), '0');
    });

    test('deleteAllData wipes and recreates schema', () async {
      await TodoRepository.insert(
        Todo.create(
          title: 'temp',
          timingType: TimingTypeExtension.fromValue('forward'),
        ),
      );
      await AppDatabase.deleteAllData();

      expect(await TodoRepository.getAll(), isEmpty);
      // defaults re-seeded
      expect(await SettingsRepository.get('user_name'), '离线用户');
      final tables = await (await AppDatabase.database).rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table'",
      );
      expect(tables.map((t) => t['name']), contains('countdowns'));
    });
  });

  group('HabitRepository', () {
    test('insert/update/resetForNewDay roundtrip', () async {
      final habit = Habit.create(title: '喝水', targetCount: 8, unit: '杯');
      await HabitRepository.insert(habit);

      final updated = habit.copyWith(currentCount: 3);
      await HabitRepository.update(updated);
      expect((await HabitRepository.getAll()).single.currentCount, 3);

      await HabitRepository.resetForNewDay('2026-08-13');
      final after = await HabitRepository.getAll();
      expect(after.single.currentCount, 0);
      expect(after.single.lastResetDate, '2026-08-13');
    });

    test(
      'resetForNewDay stamps updated_at so the reset reaches the cloud',
      () async {
        final habit = Habit.create(title: '早起', targetCount: 1);
        await HabitRepository.insert(habit);
        final db = await AppDatabase.database;
        final before =
            (await db.query(
                  'habits',
                  columns: ['updated_at'],
                  where: 'id = ?',
                  whereArgs: [habit.id],
                )).first['updated_at']
                as String;

        await Future<void>.delayed(const Duration(milliseconds: 5));
        await HabitRepository.resetForNewDay('2026-08-14');
        final after =
            (await db.query(
                  'habits',
                  columns: ['updated_at'],
                  where: 'id = ?',
                  whereArgs: [habit.id],
                )).first['updated_at']
                as String;

        expect(DateTime.parse(after).isAfter(DateTime.parse(before)), isTrue);
      },
    );
  });

  group('CountdownRepository', () {
    test('insert/deleteById roundtrip', () async {
      final countdown = Countdown.create(
        title: '考试',
        targetDateTime: DateTime.now().add(const Duration(days: 30)),
      );
      await CountdownRepository.insert(countdown);
      expect(await CountdownRepository.getAll(), hasLength(1));

      await CountdownRepository.deleteById(countdown.id);
      expect(await CountdownRepository.getAll(), isEmpty);
    });
  });

  group('sleep upsert', () {
    test('upsert by record_date preserves id', () async {
      final record = SleepRecord(
        id: 'r1',
        recordDate: '2026-08-13',
        wakeTime: '07:30',
        sleepTime: null,
        workoutTime: null,
        note: null,
      );
      await SleepRepository.upsert(record);
      await SleepRepository.upsert(
        SleepRecord(
          id: 'other-id',
          recordDate: '2026-08-13',
          wakeTime: '08:00',
          sleepTime: null,
          workoutTime: null,
          note: null,
        ),
      );

      final all = await SleepRepository.getAll();
      expect(all, hasLength(1));
      expect(all.single.id, 'r1'); // original id preserved
      expect(all.single.wakeTime, '08:00');
    });

    test('concurrent same-date local upserts still produce one row', () async {
      await Future.wait([
        SleepRepository.upsert(
          SleepRecord(
            id: 'concurrent-a',
            recordDate: '2026-08-20',
            wakeTime: '07:00',
          ),
        ),
        SleepRepository.upsert(
          SleepRecord(
            id: 'concurrent-b',
            recordDate: '2026-08-20',
            sleepTime: '23:00',
          ),
        ),
      ]);

      final records = await SleepRepository.getAll();
      expect(records, hasLength(1));
      expect(records.single.wakeTime, '07:00');
      expect(records.single.sleepTime, '23:00');
    });

    test('stores workout duration and text description', () async {
      await SleepRepository.upsert(
        SleepRecord(
          id: 'workout-1',
          recordDate: '2026-08-16',
          workoutDurationMinutes: 35,
          note: '爬楼梯 20 层',
        ),
      );

      final record = (await SleepRepository.getAll()).single;
      expect(record.workoutDurationMinutes, 35);
      expect(record.note, '爬楼梯 20 层');
      expect(record.workoutTime, isNull);
    });

    test('workout update preserves newer sleep and wake fields', () async {
      await SleepRepository.upsert(
        SleepRecord(
          id: 'daily-row',
          recordDate: '2026-08-19',
          wakeTime: '07:30',
          sleepTime: '23:45',
        ),
      );

      final updated = await SleepRepository.upsertWorkout(
        recordDate: '2026-08-19',
        durationMinutes: 50,
        description: '力量训练',
      );

      expect(updated.wakeTime, '07:30');
      expect(updated.sleepTime, '23:45');
      expect(updated.workoutDurationMinutes, 50);
      expect(updated.note, '力量训练');
    });

    test(
      'remote same-date row replaces the local UUID without duplicating',
      () async {
        await SleepRepository.upsert(
          SleepRecord(
            id: 'local-id',
            recordDate: '2026-08-17',
            wakeTime: '07:00',
          ),
        );

        await SleepRepository.upsertFromRemote({
          'id': 'remote-id',
          'record_date': '2026-08-17',
          'wake_time': '07:30',
          'sleep_time': '01:00',
          'workout_time': null,
          'workout_duration_minutes': 45,
          'note': '力量训练',
          'updated_at': '2999-08-17T08:00:00.000Z',
        });

        final records = await SleepRepository.getAll();
        expect(records, hasLength(1));
        expect(records.single.id, 'remote-id');
        expect(records.single.wakeTime, '07:30');
        expect(records.single.workoutDurationMinutes, 45);
      },
    );

    test(
      'older remote same-date row does not overwrite a newer local edit',
      () async {
        await SleepRepository.upsert(
          SleepRecord(
            id: 'local-newer',
            recordDate: '2026-08-18',
            wakeTime: '08:00',
          ),
        );
        final db = await AppDatabase.database;
        await db.update(
          'sleep_records',
          {'updated_at': '2026-08-18T09:00:00.000Z'},
          where: 'record_date = ?',
          whereArgs: ['2026-08-18'],
        );

        await SleepRepository.upsertFromRemote({
          'id': 'remote-older',
          'record_date': '2026-08-18',
          'wake_time': '06:00',
          'sleep_time': null,
          'workout_time': null,
          'workout_duration_minutes': null,
          'note': null,
          'updated_at': '2026-08-18T07:00:00.000Z',
        });

        final records = await SleepRepository.getAll();
        expect(records, hasLength(1));
        expect(records.single.id, 'local-newer');
        expect(records.single.wakeTime, '08:00');
      },
    );
  });
}
