import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/features/todos/todo_screen.dart';
import 'package:goworkbro/models/models.dart';

Todo mkTodo(
  String id, {
  bool completed = false,
  int sort = 0,
  String? completedDate,
}) => Todo(
  id: id,
  title: id,
  timingType: TimingType.none,
  sortOrder: sort,
  createdDate: '2026-08-14',
  isCompleted: completed,
  completedDate: completedDate,
);

Habit mkHabit(
  String id, {
  int current = 0,
  int target = 1,
  int sort = 0,
}) => Habit(
  id: id,
  title: id,
  createdDate: '2026-08-14',
  currentCount: current,
  targetCount: target,
  sortOrder: sort,
);

List<String> idsOf(List<Object> items) =>
    items.map((e) => (e as dynamic).id as String).toList();

void main() {
  group('buildCombinedList ordering', () {
    test('unfinished habits are forced above todos', () {
      final habits = [
        mkHabit('h_done', current: 1),
        mkHabit('h_open'),
      ];
      final todos = [mkTodo('t_open'), mkTodo('t_done', completed: true)];

      final items = buildCombinedList(todos, habits);
      // unfinished habit first, then incomplete todo, then done zone.
      expect(items.first, isA<Habit>());
      expect(idsOf(items), ['h_open', 't_open', 'h_done', 't_done']);
    });

    test('completed todos sink to the very bottom (after completed habits)',
        () {
      final habits = [mkHabit('h1', current: 1, target: 1)];
      final todos = [
        mkTodo('t_done', completed: true, completedDate: '2026-08-14T10:00:00'),
        mkTodo('t_open'),
      ];

      expect(idsOf(buildCombinedList(todos, habits)), ['t_open', 'h1', 't_done']);
    });

    test('completed todos are sorted by completedDate desc', () {
      final todos = [
        mkTodo('t_early', completed: true, completedDate: '2026-08-14T08:00:00'),
        mkTodo('t_open'),
        mkTodo('t_late', completed: true, completedDate: '2026-08-14T12:00:00'),
      ];

      expect(
        idsOf(buildCombinedList(todos, [])),
        ['t_open', 't_late', 't_early'],
      );
    });

    test('incomplete todos and habits keep their sortOrder within zones', () {
      final todos = [
        mkTodo('t2', sort: 2),
        mkTodo('t1', sort: 1),
        mkTodo('t0', sort: 0),
      ];
      final habits = [
        mkHabit('h0', sort: 0),
        mkHabit('h2', sort: 2),
        mkHabit('h1', sort: 1),
      ];

      expect(
        idsOf(buildCombinedList(todos, habits)),
        ['h0', 'h1', 'h2', 't0', 't1', 't2'],
      );
    });
  });
}
