import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/core/database/app_database.dart';
import 'package:goworkbro/core/database/repositories/countdown_repository.dart';
import 'package:goworkbro/core/database/repositories/focus_repository.dart';
import 'package:goworkbro/core/database/repositories/habit_repository.dart';
import 'package:goworkbro/core/database/repositories/settings_repository.dart';
import 'package:goworkbro/core/database/repositories/sleep_repository.dart';
import 'package:goworkbro/core/database/repositories/todo_repository.dart';
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
      final done = Todo.create(title: 'done', timingType: TimingTypeExtension.fromValue('forward'))
          .copyWith(isCompleted: true);
      final pending = Todo.create(title: 'pending', timingType: TimingTypeExtension.fromValue('forward'));
      await TodoRepository.insert(done);
      await TodoRepository.insert(pending);

      await TodoRepository.rollOver('2026-08-13');

      final all = await TodoRepository.getAll();
      expect(all.map((t) => t.title), ['pending']);
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
      final settingsTable = syncTables.firstWhere((t) => t.name == 'user_settings');
      await settingsTable.applyRemote({'key': 'locale', 'value': 'en'});
      await settingsTable.applyRemote({'key': 'user_name', 'value': 'AzarAI'});

      expect(await SettingsRepository.get('locale'), isNull);
      expect(await SettingsRepository.get('user_name'), 'AzarAI');
    });

    test('focus_sessions applyRemote is idempotent', () async {
      final focusTable = syncTables.firstWhere((t) => t.name == 'focus_sessions');
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
      expect(names, containsAll([
        'todos',
        'habits',
        'focus_sessions',
        'countdowns',
        'sleep_records',
        'user_settings',
        'ustc_news_cache',
      ]));

      // default profile rows seeded on fresh install
      expect(await SettingsRepository.get('user_name'), '离线用户');
      expect(await SettingsRepository.get('lifetime_todos_completed'), '0');
    });

    test('deleteAllData wipes and recreates schema', () async {
      await TodoRepository.insert(
        Todo.create(title: 'temp', timingType: TimingTypeExtension.fromValue('forward')),
      );
      await AppDatabase.deleteAllData();

      expect(await TodoRepository.getAll(), isEmpty);
      // defaults re-seeded
      expect(await SettingsRepository.get('user_name'), '离线用户');
      final tables = await (await AppDatabase.database)
          .rawQuery("SELECT name FROM sqlite_master WHERE type='table'");
      expect(tables.map((t) => t['name']), contains('countdowns'));
    });
  });

  group('HabitRepository', () {
    test('insert/update/resetForNewDay roundtrip', () async {
      final habit = Habit.create(
        title: '喝水',
        targetCount: 8,
        unit: '杯',
      );
      await HabitRepository.insert(habit);

      final updated = habit.copyWith(currentCount: 3);
      await HabitRepository.update(updated);
      expect((await HabitRepository.getAll()).single.currentCount, 3);

      await HabitRepository.resetForNewDay('2026-08-13');
      final after = await HabitRepository.getAll();
      expect(after.single.currentCount, 0);
      expect(after.single.lastResetDate, '2026-08-13');
    });
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
  });
}
