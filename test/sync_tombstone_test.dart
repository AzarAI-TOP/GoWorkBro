import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import 'package:goworkbro/core/database/app_database.dart';
import 'package:goworkbro/core/database/repositories/countdown_repository.dart';
import 'package:goworkbro/core/database/repositories/habit_repository.dart';
import 'package:goworkbro/core/database/repositories/pending_deletes_repository.dart';
import 'package:goworkbro/core/database/repositories/todo_repository.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/providers/app_provider.dart';

Map<String, dynamic> _remoteTodoRow(Todo t, {String? updatedAt}) => {
  'id': t.id,
  'title': t.title,
  'timing_type': t.timingType.value,
  'duration_minutes': t.durationMinutes,
  'is_completed': t.isCompleted,
  'sort_order': t.sortOrder,
  'keep_tomorrow': t.keepTomorrow,
  'created_date': t.createdDate,
  'completed_date': t.completedDate,
  'actual_duration_seconds': t.actualDurationSeconds,
  'updated_at': updatedAt ?? DateTime.now().toUtc().toIso8601String(),
};

Map<String, dynamic> _remoteHabitRow(Habit h, {String? updatedAt}) => {
  'id': h.id,
  'title': h.title,
  'target_count': h.targetCount,
  'unit': h.unit,
  'sort_order': h.sortOrder,
  'created_date': h.createdDate,
  'current_count': h.currentCount,
  'last_reset_date': h.lastResetDate,
  'updated_at': updatedAt ?? DateTime.now().toUtc().toIso8601String(),
};

void main() {
  late Directory tempDir;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('goworkbro_tombstone_');
    AppDatabase.setDataDirForTesting(tempDir.path);
  });

  tearDown(() async {
    await AppDatabase.closeForTesting();
    await tempDir.delete(recursive: true);
  });

  group('delete tombstones', () {
    test('a tombstoned todo is not resurrected by pull', () async {
      final todo = Todo.create(
        title: '删除后不能复活',
        timingType: TimingTypeExtension.fromValue('forward'),
      );
      await TodoRepository.insert(todo);
      await TodoRepository.deleteWithTombstone(todo.id);
      expect(await TodoRepository.getAll(), isEmpty);
      expect(
        await PendingDeletesRepository.takePending(),
        contains(('todos', todo.id)),
      );

      // The remote copy still exists (delete not confirmed yet) — pull
      // must skip it instead of re-inserting.
      await TodoRepository.upsertFromRemote(_remoteTodoRow(todo));
      expect(await TodoRepository.getAll(), isEmpty);

      // Once the remote delete is confirmed the tombstone is cleared and
      // a legitimately re-created row may be applied again.
      await PendingDeletesRepository.clear('todos', todo.id);
      await TodoRepository.upsertFromRemote(_remoteTodoRow(todo));
      expect((await TodoRepository.getAll()).single.id, todo.id);
    });

    test('a tombstoned habit is not resurrected by pull', () async {
      final habit = Habit.create(title: '习惯');
      await HabitRepository.insert(habit);
      await HabitRepository.deleteWithTombstone(habit.id);

      await HabitRepository.upsertFromRemote(_remoteHabitRow(habit));
      expect(await HabitRepository.getAll(), isEmpty);
    });

    test('daily rollover queues tombstones for completed todos', () async {
      final done = Todo.create(
        title: '已完成',
        timingType: TimingTypeExtension.fromValue('none'),
      ).copyWith(isCompleted: true);
      final open = Todo.create(
        title: '未完成',
        timingType: TimingTypeExtension.fromValue('none'),
      );
      await TodoRepository.insert(done);
      await TodoRepository.insert(open);

      final deleted = await TodoRepository.rollOver('2026-08-28');
      expect(deleted, [done.id]);
      expect(
        await PendingDeletesRepository.takePending(),
        contains(('todos', done.id)),
      );

      // The cloud mirror of a rolled-over row must not come back either.
      await TodoRepository.upsertFromRemote(_remoteTodoRow(done));
      final remaining = await TodoRepository.getAll();
      expect(remaining.single.id, open.id);
    });

    test('expired countdown cleanup queues tombstones', () async {
      final yesterday = DateTime.now().toUtc().subtract(const Duration(days: 1));
      final expired = Countdown(
        id: 'c-expired',
        title: '过期',
        targetDateTime: yesterday,
        createdDate: DateTime.now().toIso8601String(),
      );
      await CountdownRepository.insert(expired);

      final deleted = await CountdownRepository.cleanupExpired();
      expect(deleted, [expired.id]);
      expect(
        await PendingDeletesRepository.takePending(),
        contains(('countdowns', expired.id)),
      );
    });

    test('deleteAllData wipes queued tombstones with everything else',
        () async {
      final todo = Todo.create(
        title: 'x',
        timingType: TimingTypeExtension.fromValue('none'),
      );
      await TodoRepository.insert(todo);
      await TodoRepository.deleteWithTombstone(todo.id);

      await AppDatabase.deleteAllData();
      expect(await PendingDeletesRepository.takePending(), isEmpty);
    });
  });

  group('v7 -> v8 migration', () {
    test('creates pending_deletes and the focus session_date index',
        () async {
      sqfliteFfiInit();
      final temp = await Directory.systemTemp.createTemp('goworkbro_v8_');
      final path = '${temp.path}${Platform.pathSeparator}v8.db';
      final db = await databaseFactoryFfi.openDatabase(path);
      addTearDown(() async {
        await db.close();
        await temp.delete(recursive: true);
      });
      await db.execute('''
        CREATE TABLE focus_sessions (
          id TEXT PRIMARY KEY,
          todo_id TEXT,
          source_type TEXT NOT NULL,
          source_title TEXT NOT NULL,
          start_time TEXT NOT NULL,
          end_time TEXT NOT NULL,
          duration_seconds INTEGER NOT NULL,
          session_date TEXT NOT NULL
        )
      ''');

      await AppDatabase.migrateForTesting(db, 7, 8);

      final tables = await db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='pending_deletes'",
      );
      expect(tables, isNotEmpty);
      final indexes = await db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_focus_sessions_session_date'",
      );
      expect(indexes, isNotEmpty);
    });
  });

  group('reorder stamps', () {
    late AppProvider provider;

    setUp(() async {
      provider = AppProvider();
      await provider.init();
    });

    tearDown(() async {
      provider.dispose();
    });

    test('reorderTodos stamps updated_at so the order survives LWW', () async {
      await provider.addTodo(
        Todo.create(
          title: 'A',
          timingType: TimingTypeExtension.fromValue('none'),
        ),
      );
      await provider.addTodo(
        Todo.create(
          title: 'B',
          timingType: TimingTypeExtension.fromValue('none'),
        ),
      );
      expect(provider.todos.first.updatedAt, isNull);

      await provider.reorderTodos(1, 0);

      expect(provider.todos.map((t) => t.title).toList(), ['B', 'A']);
      for (final t in provider.todos) {
        expect(t.updatedAt, isNotNull, reason: 'reorder must bump the stamp');
        expect(t.sortOrder, provider.todos.indexOf(t));
      }
      // Persisted too — a reload must not lose the new order/stamps.
      final reloaded = await TodoRepository.getAll();
      expect(reloaded.map((t) => t.title).toList(), ['B', 'A']);
      expect(reloaded.every((t) => t.updatedAt != null), isTrue);
    });

    test('reorderHabits stamps updated_at as well', () async {
      await provider.addHabit(Habit.create(title: 'H1'));
      await provider.addHabit(Habit.create(title: 'H2'));

      await provider.reorderHabits(1, 0);

      expect(provider.habits.map((h) => h.title).toList(), ['H2', 'H1']);
      for (final h in provider.habits) {
        expect(h.updatedAt, isNotNull, reason: 'reorder must bump the stamp');
        expect(h.sortOrder, provider.habits.indexOf(h));
      }
      final reloaded = await HabitRepository.getAll();
      expect(reloaded.map((h) => h.title).toList(), ['H2', 'H1']);
      expect(reloaded.every((h) => h.updatedAt != null), isTrue);
    });
  });
}
