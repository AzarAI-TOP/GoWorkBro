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

  group('mapReorder (onReorderItem → provider indexes)', () {
    // Provider lists are sortOrder-ordered, as loaded from the repositories.
    // Display order: [h0, h1, t0, t1, t2, hc, tc].
    List<Todo> baseTodos() => [
      mkTodo('t0', sort: 0),
      mkTodo('t1', sort: 1),
      mkTodo('t2', sort: 2),
      mkTodo('tc', completed: true, sort: 3, completedDate: '2026-08-14T09:00:00'),
    ];
    List<Habit> baseHabits() => [
      mkHabit('h0', sort: 0),
      mkHabit('h1', sort: 1),
      mkHabit('hc', current: 1, target: 1, sort: 2),
    ];

    List<String> applyTodoMove(List<Todo> list, ReorderMapping m) {
      final copy = [...list];
      var n = m.newProviderIndex;
      if (n > m.oldProviderIndex) n--;
      copy.insert(n, copy.removeAt(m.oldProviderIndex));
      return idsOf(copy);
    }

    test('drag todo down to the end of the incomplete zone', () {
      // t0 (display 2) → after t2 (finalPos 4).
      final m = mapReorder(
        todos: baseTodos(),
        habits: baseHabits(),
        oldIndex: 2,
        newIndex: 4,
      );
      expect(m, isNotNull);
      expect(m!.kind, 'todo');
      expect(m.oldProviderIndex, 0);
      expect(m.newProviderIndex, 3); // todos.length — insert at end
      expect(applyTodoMove(baseTodos(), m), ['t1', 't2', 't0', 'tc']);
    });

    test('drag todo up to the top of the incomplete zone', () {
      // t2 (display 4) → before t0 (finalPos 2).
      final m = mapReorder(
        todos: baseTodos(),
        habits: baseHabits(),
        oldIndex: 4,
        newIndex: 2,
      );
      expect(m!.kind, 'todo');
      expect(m.oldProviderIndex, 2);
      expect(m.newProviderIndex, 0);
      expect(applyTodoMove(baseTodos(), m), ['t2', 't0', 't1', 'tc']);
    });

    test('drag todo down one slot', () {
      // t0 (display 2) → between t1 and t2 (finalPos 3).
      final m = mapReorder(
        todos: baseTodos(),
        habits: baseHabits(),
        oldIndex: 2,
        newIndex: 3,
      );
      expect(m!.kind, 'todo');
      expect(m.oldProviderIndex, 0);
      expect(m.newProviderIndex, 2);
      expect(applyTodoMove(baseTodos(), m), ['t1', 't0', 't2', 'tc']);
    });

    test('drop todo at its own position is a no-op', () {
      // t1 (display 3) → own slot.
      final m = mapReorder(
        todos: baseTodos(),
        habits: baseHabits(),
        oldIndex: 3,
        newIndex: 3,
      );
      expect(m, isNull);
    });

    test('drag habit down within the unfinished zone', () {
      // h0 (display 0) → after h1 (finalPos 1).
      final m = mapReorder(
        todos: baseTodos(),
        habits: baseHabits(),
        oldIndex: 0,
        newIndex: 1,
      );
      expect(m!.kind, 'habit');
      expect(m.oldProviderIndex, 0);
      expect(m.newProviderIndex, 2);
    });

    test('drag habit down past the zone stays inside the zone', () {
      // h0 (display 0) → into the todo zone (finalPos 3) — clamped to the
      // end of the unfinished-habit zone.
      final m = mapReorder(
        todos: baseTodos(),
        habits: baseHabits(),
        oldIndex: 0,
        newIndex: 3,
      );
      expect(m!.kind, 'habit');
      expect(m.oldProviderIndex, 0);
      expect(m.newProviderIndex, 2); // after h1 — habits cannot enter todos
    });

    test('drag todo up past the habit zone stays inside its zone', () {
      // t2 (display 4) → into the habit zone (finalPos 1) — clamped to the
      // top of the incomplete-todo zone.
      final m = mapReorder(
        todos: baseTodos(),
        habits: baseHabits(),
        oldIndex: 4,
        newIndex: 1,
      );
      expect(m!.kind, 'todo');
      expect(m.oldProviderIndex, 2);
      expect(m.newProviderIndex, 0);
    });

    test('completed items are not draggable', () {
      // tc sits at display 5; hc sits at display 6 (after todos).
      expect(
        mapReorder(
          todos: baseTodos(),
          habits: baseHabits(),
          oldIndex: 5,
          newIndex: 6,
        ),
        isNull,
      );
      expect(
        mapReorder(
          todos: baseTodos(),
          habits: baseHabits(),
          oldIndex: 6,
          newIndex: 5,
        ),
        isNull,
      );
    });

    test('habit index mapping accounts for completed habits in the list', () {
      // habits list: [u0, c0, u1] — completed habit sits between unfinished
      // ones in sortOrder. Drag u0 (display 0) to after u1 (finalPos 1).
      final habits = [
        mkHabit('u0', sort: 0),
        mkHabit('c0', current: 1, target: 1, sort: 1),
        mkHabit('u1', sort: 2),
      ];
      final m = mapReorder(todos: [], habits: habits, oldIndex: 0, newIndex: 1);
      expect(m!.kind, 'habit');
      expect(m.oldProviderIndex, 0);
      expect(m.newProviderIndex, 3); // indexOf(u1) + 1
    });
  });
}
