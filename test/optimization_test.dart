import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/core/database/app_database.dart';
import 'package:goworkbro/core/utils/sleep_chart_utils.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:goworkbro/core/database/repositories/settings_repository.dart';

void main() {
  test('sleep chart splits observations across missing days', () {
    final runs = splitContiguousSleepSpots(const [
      FlSpot(0, 7),
      FlSpot(1, 7.5),
      FlSpot(3, 8),
      FlSpot(6, 7),
    ]);
    expect(runs.map((run) => run.map((spot) => spot.x).toList()).toList(), [
      [0, 1],
      [3],
      [6],
    ]);
  });

  test('overnight duration wraps across midnight', () {
    expect(overnightDurationHours(23, 7), 8);
    expect(overnightDurationHours(0.5, 8), 7.5);
  });

  group('English localization', () {
    final strings = S.of(AppLocale.en);

    test(
      'maps persisted Chinese habit units without changing stored values',
      () {
        expect(strings.habitUnitLabel('次'), 'times');
        expect(strings.habitUnitLabel('分钟'), 'minutes');
        expect(strings.habitProgress(1, 3, '页'), 'Daily 1/3 pages');
        expect(strings.habitUnitLabel('sets'), 'sets');
      },
    );

    test('maps persisted timing values', () {
      expect(strings.timingLabel('none'), 'No Timer');
      expect(strings.timingLabel('forward'), 'Count Up');
      expect(strings.timingLabel('backward'), 'Count Down');
      expect(strings.deleteData, 'Delete All Data');
      expect(strings.signOut, 'Sign Out');
      expect(strings.sleepRecordSummary('07:00', '18:00', '23:00'),
          'Wake 07:00  ·  Workout 18:00  ·  Sleep 23:00');
      expect(strings.countdownTarget('8/13 09:00'), 'Target: 8/13 09:00');
    });
  });

  test('completion counter is idempotent per event key', () async {
    sqfliteFfiInit();
    final temp = await Directory.systemTemp.createTemp('goworkbro_counter_');
    final path = '${temp.path}${Platform.pathSeparator}counter.db';
    final db = await databaseFactoryFfi.openDatabase(path);
    addTearDown(() async {
      await db.close();
      await temp.delete(recursive: true);
    });
    await db.execute('''
      CREATE TABLE user_settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      )
    ''');
    await db.insert('user_settings', {
      'key': 'lifetime_todos_completed',
      'value': '0',
    });

    final first = await SettingsRepository.incrementCounterOnceForTesting(
      db,
      counterKey: 'lifetime_todos_completed',
      eventKey: 'completion.todo.t1',
    );
    final duplicate =
        await SettingsRepository.incrementCounterOnceForTesting(
          db,
          counterKey: 'lifetime_todos_completed',
          eventKey: 'completion.todo.t1',
        );
    final secondEvent =
        await SettingsRepository.incrementCounterOnceForTesting(
          db,
          counterKey: 'lifetime_todos_completed',
          eventKey: 'completion.todo.t2',
        );

    expect(first, 1);
    expect(duplicate, 1);
    expect(secondEvent, 2);
  });

  test('database v2 to v4 migration preserves data and seeds counters', () async {
    sqfliteFfiInit();
    final temp = await Directory.systemTemp.createTemp('goworkbro_migration_');
    final path = '${temp.path}${Platform.pathSeparator}legacy.db';
    final db = await databaseFactoryFfi.openDatabase(path);
    addTearDown(() async {
      await db.close();
      await temp.delete(recursive: true);
    });

    await db.execute('''
      CREATE TABLE todos (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        is_completed INTEGER NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE habits (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        current_count INTEGER NOT NULL,
        target_count INTEGER NOT NULL,
        last_reset_date TEXT
      )
    ''');
    await db.execute('''
      CREATE TABLE user_settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE countdowns (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        target_datetime TEXT NOT NULL,
        created_date TEXT NOT NULL,
        color_index INTEGER NOT NULL DEFAULT 0
      )
    ''');
    await db.insert('todos', {
      'id': 't1',
      'title': 'keep me',
      'is_completed': 1,
    });
    await db.insert('todos', {
      'id': 't2',
      'title': 'also keep me',
      'is_completed': 0,
    });
    await db.insert('habits', {
      'id': 'h1',
      'title': 'habit',
      'current_count': 1,
      'target_count': 1,
      'last_reset_date': '2026-08-12',
    });
    await db.insert('user_settings', {
      'key': 'user_name',
      'value': 'Existing User',
    });

    await AppDatabase.migrateForTesting(db, 2, 4);

    final todos = await db.query('todos', orderBy: 'id');
    final userName = await db.query(
      'user_settings',
      where: 'key = ?',
      whereArgs: ['user_name'],
    );
    final cacheTable = await db.rawQuery(
      "SELECT name FROM sqlite_master WHERE type='table' AND name='ustc_news_cache'",
    );
    final todoCounter = await db.query(
      'user_settings',
      where: 'key = ?',
      whereArgs: ['lifetime_todos_completed'],
    );
    final habitCounter = await db.query(
      'user_settings',
      where: 'key = ?',
      whereArgs: ['lifetime_habits_completed'],
    );
    final todoMarker = await db.query(
      'user_settings',
      where: 'key = ?',
      whereArgs: ['completion.todo.t1'],
    );
    final yesterdayHabitMarker = await db.query(
      'user_settings',
      where: 'key = ?',
      whereArgs: ['completion.habit.h1.2026-08-12'],
    );
    final today = DateTime.now().toIso8601String().substring(0, 10);
    final todayHabitMarker = await db.query(
      'user_settings',
      where: 'key = ?',
      whereArgs: ['completion.habit.h1.$today'],
    );

    expect(todos.map((row) => row['title']), ['keep me', 'also keep me']);
    expect(userName.single['value'], 'Existing User');
    expect(cacheTable, hasLength(1));
    expect(todoCounter.single['value'], '1');
    expect(habitCounter.single['value'], '1');
    expect(todoMarker, hasLength(1));
    expect(yesterdayHabitMarker, hasLength(1));
    expect(todayHabitMarker, isEmpty);

    // v4: updated_at columns added for last-write-wins sync.
    for (final table in ['todos', 'habits', 'countdowns']) {
      final columns = await db.rawQuery('PRAGMA table_info($table)');
      expect(
        columns.map((c) => c['name']),
        contains('updated_at'),
        reason: '$table should gain updated_at in the v4 migration',
      );
    }
  });
}
