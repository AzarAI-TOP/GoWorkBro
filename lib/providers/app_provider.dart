import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/models.dart';
import '../services/database_service.dart';
import '../services/api_service.dart';
import '../services/sync_service.dart';
import '../services/supabase_config.dart';

/// Central app state — manages all data and daily rollover logic
class AppProvider extends ChangeNotifier {
  List<Todo> _todos = [];
  final bool _isInitialized = false;
  bool get isInitialized => _isInitialized;
  List<Habit> _habits = [];
  List<Countdown> _countdowns = [];
  List<FocusSession> _todaySessions = [];
  List<SleepRecord> _sleepRecords = [];
  String _userName = 'AzarAI';
  String _apiBaseUrl = 'http://localhost:8765';
  bool _apiConnected = false;
  String _lastRolloverDate = '';
  Timer? _rolloverTimer;

  List<Todo> get todos => _todos;
  List<Habit> get habits => _habits;
  List<Countdown> get countdowns => _countdowns;
  List<FocusSession> get todaySessions => _todaySessions;
  List<SleepRecord> get sleepRecords => _sleepRecords;
  String get userName => _userName;
  String get apiBaseUrl => _apiBaseUrl;
  bool get apiConnected => _apiConnected;

  String get todayDate {
    final now = DateTime.now();
    return '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
  }

  Future<void> init() async {
    _userName = await DatabaseService.getSetting('user_name') ?? 'AzarAI';
    _apiBaseUrl = await DatabaseService.getSetting('api_base_url') ??
        'http://localhost:8765';
    _lastRolloverDate =
        await DatabaseService.getSetting('last_rollover_date') ?? '';

    await _performDailyRollover();
    await refreshAll();

    // Initialize Supabase sync (if configured and logged in)
    if (isSupabaseConfigured) {
      await SyncService.initialize();
      if (SyncService.isInitialized) {
        // Pull remote data first
        await SyncService.pullAll();
        await refreshAll();
        // Then push all local data to remote (first-time sync)
        _pushAllLocal();
      }
    }

    _apiConnected = await ApiService.testConnection();

    // Check every minute if the date has changed (for cross-day rollover)
    _rolloverTimer = Timer.periodic(const Duration(minutes: 1), (_) {
      if (_lastRolloverDate != todayDate) {
        _performDailyRollover().then((_) => refreshAll());
      }
    });
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
    _todos = await DatabaseService.getTodos();
    notifyListeners();
    SyncService.pushTodo(todo);
  }

  Future<void> updateTodo(Todo todo) async {
    await DatabaseService.updateTodo(todo);
    _todos = await DatabaseService.getTodos();
    notifyListeners();
    SyncService.pushTodo(todo);
  }

  Future<void> toggleTodoComplete(Todo todo) async {
    final updated = todo.copyWith(
      isCompleted: !todo.isCompleted,
      completedDate: !todo.isCompleted ? DateTime.now().toIso8601String() : null,
    );
    await DatabaseService.updateTodo(updated);
    _todos = await DatabaseService.getTodos();
    notifyListeners();
    SyncService.pushTodo(updated);
  }

  Future<void> deleteTodo(String id) async {
    await DatabaseService.deleteTodo(id);
    _todos = await DatabaseService.getTodos();
    notifyListeners();
    SyncService.deleteRemoteTodo(id);
  }

  Future<void> reorderTodos(int oldIndex, int newIndex) async {
    if (newIndex > oldIndex) newIndex--;
    final item = _todos.removeAt(oldIndex);
    _todos.insert(newIndex, item);
    for (var i = 0; i < _todos.length; i++) {
      _todos[i] = _todos[i].copyWith(sortOrder: i);
      await DatabaseService.updateTodo(_todos[i]);
      SyncService.pushTodo(_todos[i]);
    }
    notifyListeners();
  }

  // ============ Habit ops ============

  Future<void> addHabit(Habit habit) async {
    await DatabaseService.insertHabit(habit);
    _habits = await DatabaseService.getHabits();
    notifyListeners();
    SyncService.pushHabit(habit);
  }

  Future<void> updateHabit(Habit habit) async {
    await DatabaseService.updateHabit(habit);
    _habits = await DatabaseService.getHabits();
    notifyListeners();
    SyncService.pushHabit(habit);
  }

  Future<void> incrementHabit(Habit habit) async {
    if (habit.currentCount >= habit.targetCount) return;
    final updated = habit.copyWith(currentCount: habit.currentCount + 1);
    await DatabaseService.updateHabit(updated);
    _habits = await DatabaseService.getHabits();
    notifyListeners();
    SyncService.pushHabit(updated);
  }

  Future<void> decrementHabit(Habit habit) async {
    if (habit.currentCount == 0) return;
    final updated = habit.copyWith(currentCount: habit.currentCount - 1);
    await DatabaseService.updateHabit(updated);
    _habits = await DatabaseService.getHabits();
    notifyListeners();
    SyncService.pushHabit(updated);
  }

  Future<void> deleteHabit(String id) async {
    await DatabaseService.deleteHabit(id);
    _habits = await DatabaseService.getHabits();
    notifyListeners();
    SyncService.deleteRemoteHabit(id);
  }

  Future<void> reorderHabits(int oldIndex, int newIndex) async {
    if (newIndex > oldIndex) newIndex--;
    final item = _habits.removeAt(oldIndex);
    _habits.insert(newIndex, item);
    for (var i = 0; i < _habits.length; i++) {
      _habits[i] = _habits[i].copyWith(sortOrder: i);
      await DatabaseService.updateHabit(_habits[i]);
    }
    notifyListeners();
  }

  // ============ Focus Session ============

  Future<void> recordFocusSession(FocusSession session) async {
    await DatabaseService.insertFocusSession(session);
    _todaySessions = await DatabaseService.getFocusSessionsByDate(todayDate);
    notifyListeners();
    SyncService.pushFocusSession(session);
  }

  /// Get focus seconds for the last 7 days (oldest first, today last)
  Future<List<int>> getWeeklyFocusSeconds() async {
    final now = DateTime.now();
    final results = <int>[];
    for (int i = 6; i >= 0; i--) {
      final day = now.subtract(Duration(days: i));
      final dateStr = '${day.year}-${day.month.toString().padLeft(2, '0')}-${day.day.toString().padLeft(2, '0')}';
      final sessions = await DatabaseService.getFocusSessionsByDate(dateStr);
      results.add(sessions.fold(0, (sum, s) => sum + s.durationSeconds));
    }
    return results;
  }

  // ============ Countdown ops ============

  Future<void> addCountdown(Countdown countdown) async {
    await DatabaseService.insertCountdown(countdown);
    _countdowns = await DatabaseService.getCountdowns();
    notifyListeners();
    SyncService.pushCountdown(countdown);
  }

  Future<void> deleteCountdown(String id) async {
    await DatabaseService.deleteCountdown(id);
    _countdowns = await DatabaseService.getCountdowns();
    notifyListeners();
    SyncService.deleteRemoteCountdown(id);
  }

  // ============ Sleep ============

  Future<void> recordSleep(SleepRecord record) async {
    await DatabaseService.upsertSleepRecord(record);
    _sleepRecords = await DatabaseService.getSleepRecords();
    notifyListeners();
    SyncService.pushSleepRecord(record);
  }

  // ============ Settings ============

  Future<void> setUserName(String name) async {
    _userName = name;
    await DatabaseService.setSetting('user_name', name);
    notifyListeners();
  }

  Future<void> setApiBaseUrl(String url) async {
    _apiBaseUrl = url;
    await ApiService.setBaseUrl(url);
    _apiConnected = await ApiService.testConnection();
    notifyListeners();
  }

  // ============ Computed stats ============

  /// Push all local data to Supabase (first-time sync)
  void _pushAllLocal() {
    for (final t in _todos) {
      SyncService.pushTodo(t);
    }
    for (final h in _habits) {
      SyncService.pushHabit(h);
    }
    for (final c in _countdowns) {
      SyncService.pushCountdown(c);
    }
    for (final s in _todaySessions) {
      SyncService.pushFocusSession(s);
    }
    for (final r in _sleepRecords) {
      SyncService.pushSleepRecord(r);
    }
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
