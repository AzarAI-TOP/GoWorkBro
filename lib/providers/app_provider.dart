import 'dart:async';
import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';
import '../models/models.dart';
import '../services/database_service.dart';
import '../services/sync_service.dart';
import '../services/supabase_config.dart';

const _uuid = Uuid();

/// Central app state — manages all data and daily rollover logic.
/// Locale and theme mode live in AppLocaleProvider (single source of truth).
class AppProvider extends ChangeNotifier {
  List<Todo> _todos = [];
  bool _isInitialized = false;
  bool get isInitialized => _isInitialized;
  List<Habit> _habits = [];
  List<Countdown> _countdowns = [];
  List<FocusSession> _todaySessions = [];
  List<SleepRecord> _sleepRecords = [];
  String _userName = 'AzarAI';
  String _lastRolloverDate = '';
  Timer? _rolloverTimer;

  List<Todo> get todos => _todos;
  List<Habit> get habits => _habits;
  List<Countdown> get countdowns => _countdowns;
  List<FocusSession> get todaySessions => _todaySessions;
  List<SleepRecord> get sleepRecords => _sleepRecords;
  String get userName => _userName;

  String get todayDate {
    final now = DateTime.now();
    return '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
  }

  Future<void> init() async {
    _userName = await DatabaseService.getSetting('user_name') ?? 'AzarAI';
    _lastRolloverDate =
        await DatabaseService.getSetting('last_rollover_date') ?? '';

    await _performDailyRollover();
    await refreshAll();

    if (isSupabaseConfigured) {
      await SyncService.initialize();
      if (SyncService.isInitialized) {
        await SyncService.pullAll();
        await refreshAll();
        _pushAllLocal();
      }
    }

    _rolloverTimer = Timer.periodic(const Duration(minutes: 1), (_) {
      if (_lastRolloverDate != todayDate) {
        _performDailyRollover().then((_) => refreshAll());
      }
    });

    _isInitialized = true;
    notifyListeners();
  }

  @override
  void dispose() {
    _rolloverTimer?.cancel();
    SyncService.dispose();
    super.dispose();
  }

  Future<void> _performDailyRollover() async {
    if (_lastRolloverDate == todayDate) return;

    await DatabaseService.rollOverTodos(todayDate);
    await DatabaseService.resetHabitsForNewDay(todayDate);
    await DatabaseService.cleanupExpiredCountdowns();

    await DatabaseService.setSetting('last_rollover_date', todayDate);
    _lastRolloverDate = todayDate;
  }

  /// Full reload from DB — used at startup and after sync pull.
  Future<void> refreshAll() async {
    _todos = await DatabaseService.getTodos();
    _habits = await DatabaseService.getHabits();
    _countdowns = await DatabaseService.getCountdowns();
    _todaySessions = await DatabaseService.getFocusSessionsByDate(todayDate);
    _sleepRecords = await DatabaseService.getSleepRecords();
    notifyListeners();
  }

  // ============ TODO ops ============

  Future<void> addTodo(Todo todo) async {
    await DatabaseService.insertTodo(todo);
    _todos.add(todo);
    notifyListeners();
    SyncService.pushTodo(todo);
  }

  Future<void> updateTodo(Todo todo) async {
    await DatabaseService.updateTodo(todo);
    final i = _todos.indexWhere((t) => t.id == todo.id);
    if (i >= 0) _todos[i] = todo;
    notifyListeners();
    SyncService.pushTodo(todo);
  }

  /// Toggle todo completion. Idempotent for keepTomorrow:
  /// only creates a copy if no incomplete duplicate already exists.
  Future<void> toggleTodoComplete(Todo todo) async {
    final isCompleting = !todo.isCompleted;
    final updated = todo.copyWith(
      isCompleted: isCompleting,
      completedDate: isCompleting ? DateTime.now().toIso8601String() : null,
    );
    await DatabaseService.updateTodo(updated);
    final i = _todos.indexWhere((t) => t.id == todo.id);
    if (i >= 0) _todos[i] = updated;

    if (isCompleting && todo.keepTomorrow) {
      final hasIncompleteCopy = _todos.any((t) =>
          t.title == todo.title &&
          !t.isCompleted &&
          t.createdDate != todo.createdDate);
      if (!hasIncompleteCopy) {
        final newTodo = Todo(
          id: _uuid.v4(),
          title: todo.title,
          timingType: todo.timingType,
          durationMinutes: todo.durationMinutes,
          isCompleted: false,
          sortOrder: todo.sortOrder,
          keepTomorrow: true,
          createdDate: DateTime.now().toIso8601String(),
        );
        await DatabaseService.insertTodo(newTodo);
        _todos.add(newTodo);
        SyncService.pushTodo(newTodo);
      }
    }

    notifyListeners();
    SyncService.pushTodo(updated);
  }

  /// Mark a todo as completed with accumulated focus duration.
  /// Used by the timer screen. Silently skips if todo was deleted.
  Future<void> completeTodoWithDuration(String todoId, int additionalSeconds) async {
    final i = _todos.indexWhere((t) => t.id == todoId);
    if (i < 0) return;

    final current = _todos[i];
    final updated = current.copyWith(
      isCompleted: true,
      completedDate: DateTime.now().toIso8601String(),
      actualDurationSeconds: current.actualDurationSeconds + additionalSeconds,
    );
    await DatabaseService.updateTodo(updated);
    _todos[i] = updated;

    if (current.keepTomorrow && !current.isCompleted) {
      final hasIncompleteCopy = _todos.any((t) =>
          t.title == current.title &&
          !t.isCompleted &&
          t.createdDate != current.createdDate);
      if (!hasIncompleteCopy) {
        final newTodo = Todo(
          id: _uuid.v4(),
          title: current.title,
          timingType: current.timingType,
          durationMinutes: current.durationMinutes,
          isCompleted: false,
          sortOrder: current.sortOrder,
          keepTomorrow: true,
          createdDate: DateTime.now().toIso8601String(),
        );
        await DatabaseService.insertTodo(newTodo);
        _todos.add(newTodo);
        SyncService.pushTodo(newTodo);
      }
    }

    notifyListeners();
    SyncService.pushTodo(updated);
  }

  Future<void> deleteTodo(String id) async {
    await DatabaseService.deleteTodo(id);
    _todos.removeWhere((t) => t.id == id);
    notifyListeners();
    SyncService.deleteRemoteTodo(id);
  }

  Future<void> reorderTodos(int oldIndex, int newIndex) async {
    if (newIndex > oldIndex) newIndex--;
    final item = _todos.removeAt(oldIndex);
    _todos.insert(newIndex, item);
    // Batch: update sortOrder for all items in a single DB transaction
    final db = await DatabaseService.database;
    await db.transaction((txn) async {
      for (var i = 0; i < _todos.length; i++) {
        _todos[i] = _todos[i].copyWith(sortOrder: i);
        await txn.update('todos', _todos[i].toMap(), where: 'id = ?', whereArgs: [_todos[i].id]);
      }
    });
    // Push to Supabase (fire-and-forget)
    for (final t in _todos) { SyncService.pushTodo(t); }
    notifyListeners();
  }

  // ============ Habit ops ============

  Future<void> addHabit(Habit habit) async {
    await DatabaseService.insertHabit(habit);
    _habits.add(habit);
    notifyListeners();
    SyncService.pushHabit(habit);
  }

  Future<void> updateHabit(Habit habit) async {
    await DatabaseService.updateHabit(habit);
    final i = _habits.indexWhere((h) => h.id == habit.id);
    if (i >= 0) _habits[i] = habit;
    notifyListeners();
    SyncService.pushHabit(habit);
  }

  Future<void> incrementHabit(Habit habit) async {
    if (habit.currentCount >= habit.targetCount) return;
    final updated = habit.copyWith(currentCount: habit.currentCount + 1);
    await DatabaseService.updateHabit(updated);
    final i = _habits.indexWhere((h) => h.id == habit.id);
    if (i >= 0) _habits[i] = updated;
    notifyListeners();
    SyncService.pushHabit(updated);
  }

  Future<void> decrementHabit(Habit habit) async {
    if (habit.currentCount == 0) return;
    final updated = habit.copyWith(currentCount: habit.currentCount - 1);
    await DatabaseService.updateHabit(updated);
    final i = _habits.indexWhere((h) => h.id == habit.id);
    if (i >= 0) _habits[i] = updated;
    notifyListeners();
    SyncService.pushHabit(updated);
  }

  Future<void> deleteHabit(String id) async {
    await DatabaseService.deleteHabit(id);
    _habits.removeWhere((h) => h.id == id);
    notifyListeners();
    SyncService.deleteRemoteHabit(id);
  }

  Future<void> reorderHabits(int oldIndex, int newIndex) async {
    if (newIndex > oldIndex) newIndex--;
    final item = _habits.removeAt(oldIndex);
    _habits.insert(newIndex, item);
    final db = await DatabaseService.database;
    await db.transaction((txn) async {
      for (var i = 0; i < _habits.length; i++) {
        _habits[i] = _habits[i].copyWith(sortOrder: i);
        await txn.update('habits', _habits[i].toMap(), where: 'id = ?', whereArgs: [_habits[i].id]);
      }
    });
    notifyListeners();
  }

  // ============ Focus Session ============

  Future<void> recordFocusSession(FocusSession session) async {
    await DatabaseService.insertFocusSession(session);
    _todaySessions.add(session);
    notifyListeners();
    SyncService.pushFocusSession(session);
  }

  /// Single range query instead of 7 sequential queries.
  Future<List<int>> getWeeklyFocusSeconds() async {
    final now = DateTime.now();
    final start = now.subtract(const Duration(days: 6));
    final startStr = '${start.year}-${start.month.toString().padLeft(2, '0')}-${start.day.toString().padLeft(2, '0')}';
    final sessions = await DatabaseService.getFocusSessionsDateRange(startStr, todayDate);
    final byDate = <String, int>{};
    for (final s in sessions) {
      byDate[s.sessionDate] = (byDate[s.sessionDate] ?? 0) + s.durationSeconds;
    }
    return [
      for (int i = 6; i >= 0; i--)
        byDate['${(now.subtract(Duration(days: i)))..toIso8601String()}'] ?? 0,
    ];
  }

  // ============ Countdown ops ============

  Future<void> addCountdown(Countdown countdown) async {
    await DatabaseService.insertCountdown(countdown);
    _countdowns.add(countdown);
    _countdowns.sort((a, b) => a.targetDateTime.compareTo(b.targetDateTime));
    notifyListeners();
    SyncService.pushCountdown(countdown);
  }

  Future<void> deleteCountdown(String id) async {
    await DatabaseService.deleteCountdown(id);
    _countdowns.removeWhere((c) => c.id == id);
    notifyListeners();
    SyncService.deleteRemoteCountdown(id);
  }

  // ============ Sleep ============

  Future<void> recordSleep(SleepRecord record) async {
    await DatabaseService.upsertSleepRecord(record);
    // Update in-memory: replace if same date exists, else add
    final i = _sleepRecords.indexWhere((r) => r.recordDate == record.recordDate);
    if (i >= 0) {
      _sleepRecords[i] = record;
    } else {
      _sleepRecords.insert(0, record);
    }
    notifyListeners();
    SyncService.pushSleepRecord(record);
  }

  // ============ Settings ============

  Future<void> setUserName(String name) async {
    _userName = name;
    await DatabaseService.setSetting('user_name', name);
    notifyListeners();
  }

  Future<void> deleteAllData() async {
    await DatabaseService.deleteAllData();
    _todos = [];
    _habits = [];
    _countdowns = [];
    _todaySessions = [];
    _sleepRecords = [];
    _lastRolloverDate = '';
    notifyListeners();
  }

  // ============ Computed stats ============

  void _pushAllLocal() {
    for (final t in _todos) { SyncService.pushTodo(t); }
    for (final h in _habits) { SyncService.pushHabit(h); }
    for (final c in _countdowns) { SyncService.pushCountdown(c); }
    for (final s in _todaySessions) { SyncService.pushFocusSession(s); }
    for (final r in _sleepRecords) { SyncService.pushSleepRecord(r); }
  }

  int get todayTotalFocusSeconds =>
      _todaySessions.fold(0, (sum, s) => sum + s.durationSeconds);

  int get todaySessionCount => _todaySessions.length;

  Map<String, int> get todayFocusBySource {
    final map = <String, int>{};
    for (final s in _todaySessions) {
      map[s.sourceTitle] = (map[s.sourceTitle] ?? 0) + s.durationSeconds;
    }
    return map;
  }
}
