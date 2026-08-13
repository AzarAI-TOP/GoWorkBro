import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/core/database/app_database.dart';
import 'package:goworkbro/core/device/device_identity_service.dart';
import 'package:goworkbro/core/sync/sync_service.dart';
import 'package:goworkbro/core/config/supabase_config.dart';

const _uuid = Uuid();

/// Central app state — manages all data and daily rollover logic.
/// Locale and theme mode live in AppLocaleProvider (single source of truth).
class AppProvider extends ChangeNotifier {
  List<Todo> _todos = [];
  bool _isInitialized = false;
  Future<void>? _initializing;
  bool get isInitialized => _isInitialized;
  List<Habit> _habits = [];
  List<Countdown> _countdowns = [];
  List<FocusSession> _todaySessions = [];
  List<SleepRecord> _sleepRecords = [];
  List<FocusSession> _allSessions = [];
  String _userName = '离线用户';
  String _deviceId = '';
  String? _avatarPath;
  String _firstUsedDate = '';
  int _lifetimeTodosCompleted = 0;
  int _lifetimeHabitsCompleted = 0;
  String _lastRolloverDate = '';
  Timer? _rolloverTimer;

  List<Todo> get todos => _todos;
  List<Habit> get habits => _habits;
  List<Countdown> get countdowns => _countdowns;
  List<FocusSession> get todaySessions => _todaySessions;
  List<SleepRecord> get sleepRecords => _sleepRecords;
  String get userName => _userName;
  String get deviceId => _deviceId;
  String? get avatarPath => _avatarPath;
  String get firstUsedDate => _firstUsedDate;
  int get lifetimeTodosCompleted => _lifetimeTodosCompleted;
  int get lifetimeHabitsCompleted => _lifetimeHabitsCompleted;
  int get lifetimeFocusSeconds =>
      _allSessions.fold(0, (sum, session) => sum + session.durationSeconds);
  int get lifetimeSessionCount => _allSessions.length;

  String get todayDate {
    final now = DateTime.now();
    return '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
  }

  Future<void> init() {
    if (_isInitialized) return Future.value();
    return _initializing ??= _initInternal();
  }

  Future<void> _initInternal() async {
    _userName = await DatabaseService.getSetting('user_name') ?? '离线用户';
    _deviceId = await DeviceIdentityService.getOrCreateDeviceId();
    _avatarPath = await DatabaseService.getSetting('avatar_path');
    _firstUsedDate =
        await DatabaseService.getSetting('first_used_date') ?? todayDate;
    _lifetimeTodosCompleted =
        int.tryParse(
          await DatabaseService.getSetting('lifetime_todos_completed') ?? '0',
        ) ??
        0;
    _lifetimeHabitsCompleted =
        int.tryParse(
          await DatabaseService.getSetting('lifetime_habits_completed') ?? '0',
        ) ??
        0;
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
        // After the pull, derive the profile from the signed-in user
        // (email prefix / user_metadata) when no custom name is set yet.
        await applyAuthUser();
      }
    }

    _rolloverTimer = Timer.periodic(const Duration(minutes: 1), (_) {
      if (_lastRolloverDate != todayDate) {
        _performDailyRollover().then((_) => refreshAll());
      }
    });

    _isInitialized = true;
    _initializing = null;
    notifyListeners();
  }

  /// Sync the visible profile with the signed-in Supabase user.
  ///
  /// Called after login / session restore. If the user hasn't set a custom
  /// name yet (still the default "离线用户"), derive one from the account:
  /// `user_metadata.user_name` if present, otherwise the email prefix —
  /// so a logged-in user never sees "离线用户" in the UI.
  Future<void> applyAuthUser() async {
    if (!isSupabaseConfigured) return;
    final client = Supabase.instance.client;
    final user = client.auth.currentUser;
    if (user == null) return;

    final email = user.email;
    final meta = user.userMetadata;

    final currentName = await DatabaseService.getSetting('user_name');
    final isDefaultName = currentName == null || currentName == '离线用户';
    if (isDefaultName) {
      final metaName = (meta?['user_name'] as String?)?.trim();
      final displayName =
          (metaName != null && metaName.isNotEmpty)
              ? metaName
              : (email != null && email.isNotEmpty)
              ? email.split('@').first
              : '离线用户';
      if (displayName != currentName) {
        await DatabaseService.setSetting('user_name', displayName);
        _userName = displayName;
      }
    }

    final metaAvatar = (meta?['avatar_path'] as String?)?.trim();
    final hasLocalAvatar = _avatarPath != null && _avatarPath!.isNotEmpty;
    if (metaAvatar != null && metaAvatar.isNotEmpty && !hasLocalAvatar) {
      await DatabaseService.setSetting('avatar_path', metaAvatar);
      _avatarPath = metaAvatar;
    }

    unawaited(SyncService.pushUserSettings());
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
    _allSessions = await DatabaseService.getAllFocusSessions();
    _sleepRecords = await DatabaseService.getSleepRecords();
    notifyListeners();
  }

  // ============ TODO ops ============

  Future<void> addTodo(Todo todo) async {
    await DatabaseService.insertTodo(todo);
    _todos.add(todo);
    notifyListeners();
    unawaited(SyncService.pushTodo(todo));
  }

  Future<void> updateTodo(Todo todo) async {
    await DatabaseService.updateTodo(todo);
    final i = _todos.indexWhere((t) => t.id == todo.id);
    if (i >= 0) _todos[i] = todo;
    notifyListeners();
    unawaited(SyncService.pushTodo(todo));
  }

  /// Toggle todo completion. Idempotent for keepTomorrow:
  /// only creates a copy if no incomplete duplicate already exists.
  Future<void> toggleTodoComplete(Todo todo) async {
    final isCompleting = !todo.isCompleted;
    final updated = todo.copyWith(
      isCompleted: isCompleting,
      completedDate: isCompleting ? DateTime.now().toIso8601String() : null,
    );
    if (isCompleting) {
      _lifetimeTodosCompleted =
          await DatabaseService.incrementSettingCounterOnce(
            counterKey: 'lifetime_todos_completed',
            eventKey: 'completion.todo.${todo.id}',
          );
    }
    await DatabaseService.updateTodo(updated);
    final i = _todos.indexWhere((t) => t.id == todo.id);
    if (i >= 0) _todos[i] = updated;

    if (isCompleting && todo.keepTomorrow) {
      final hasIncompleteCopy = _todos.any(
        (t) =>
            t.title == todo.title &&
            !t.isCompleted &&
            t.createdDate != todo.createdDate,
      );
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
        unawaited(SyncService.pushTodo(newTodo));
      }
    }

    notifyListeners();
    unawaited(SyncService.pushTodo(updated));
  }

  /// Mark a todo as completed with accumulated focus duration.
  /// Used by the timer screen. Silently skips if todo was deleted.
  Future<void> completeTodoWithDuration(
    String todoId,
    int additionalSeconds,
  ) async {
    final i = _todos.indexWhere((t) => t.id == todoId);
    if (i < 0) return;

    final current = _todos[i];
    final updated = current.copyWith(
      isCompleted: true,
      completedDate: DateTime.now().toIso8601String(),
      actualDurationSeconds: current.actualDurationSeconds + additionalSeconds,
    );
    if (!current.isCompleted) {
      _lifetimeTodosCompleted =
          await DatabaseService.incrementSettingCounterOnce(
            counterKey: 'lifetime_todos_completed',
            eventKey: 'completion.todo.${current.id}',
          );
    }
    await DatabaseService.updateTodo(updated);
    _todos[i] = updated;

    if (current.keepTomorrow && !current.isCompleted) {
      final hasIncompleteCopy = _todos.any(
        (t) =>
            t.title == current.title &&
            !t.isCompleted &&
            t.createdDate != current.createdDate,
      );
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
        unawaited(SyncService.pushTodo(newTodo));
      }
    }

    notifyListeners();
    unawaited(SyncService.pushTodo(updated));
  }

  Future<void> deleteTodo(String id) async {
    await DatabaseService.deleteTodo(id);
    _todos.removeWhere((t) => t.id == id);
    notifyListeners();
    unawaited(SyncService.deleteRemoteTodo(id));
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
        await txn.update(
          'todos',
          _todos[i].toMap(),
          where: 'id = ?',
          whereArgs: [_todos[i].id],
        );
      }
    });
    // Push to Supabase (fire-and-forget)
    for (final t in _todos) {
      unawaited(SyncService.pushTodo(t));
    }
    notifyListeners();
  }

  // ============ Habit ops ============

  Future<void> addHabit(Habit habit) async {
    await DatabaseService.insertHabit(habit);
    _habits.add(habit);
    notifyListeners();
    unawaited(SyncService.pushHabit(habit));
  }

  Future<void> updateHabit(Habit habit) async {
    await DatabaseService.updateHabit(habit);
    final i = _habits.indexWhere((h) => h.id == habit.id);
    if (i >= 0) _habits[i] = habit;
    notifyListeners();
    unawaited(SyncService.pushHabit(habit));
  }

  Future<void> incrementHabit(Habit habit) async {
    if (habit.currentCount >= habit.targetCount) return;
    final updated = habit.copyWith(currentCount: habit.currentCount + 1);
    if (!habit.isCompleted && updated.isCompleted) {
      _lifetimeHabitsCompleted =
          await DatabaseService.incrementSettingCounterOnce(
            counterKey: 'lifetime_habits_completed',
            eventKey: 'completion.habit.${habit.id}.$todayDate',
          );
    }
    await DatabaseService.updateHabit(updated);
    final i = _habits.indexWhere((h) => h.id == habit.id);
    if (i >= 0) _habits[i] = updated;
    notifyListeners();
    unawaited(SyncService.pushHabit(updated));
  }

  Future<void> decrementHabit(Habit habit) async {
    if (habit.currentCount == 0) return;
    final updated = habit.copyWith(currentCount: habit.currentCount - 1);
    await DatabaseService.updateHabit(updated);
    final i = _habits.indexWhere((h) => h.id == habit.id);
    if (i >= 0) _habits[i] = updated;
    notifyListeners();
    unawaited(SyncService.pushHabit(updated));
  }

  Future<void> deleteHabit(String id) async {
    await DatabaseService.deleteHabit(id);
    _habits.removeWhere((h) => h.id == id);
    notifyListeners();
    unawaited(SyncService.deleteRemoteHabit(id));
  }

  Future<void> reorderHabits(int oldIndex, int newIndex) async {
    if (newIndex > oldIndex) newIndex--;
    final item = _habits.removeAt(oldIndex);
    _habits.insert(newIndex, item);
    final db = await DatabaseService.database;
    await db.transaction((txn) async {
      for (var i = 0; i < _habits.length; i++) {
        _habits[i] = _habits[i].copyWith(sortOrder: i);
        await txn.update(
          'habits',
          _habits[i].toMap(),
          where: 'id = ?',
          whereArgs: [_habits[i].id],
        );
      }
    });
    notifyListeners();
  }

  // ============ Focus Session ============

  Future<void> recordFocusSession(FocusSession session) async {
    await DatabaseService.insertFocusSession(session);
    _todaySessions.add(session);
    _allSessions.add(session);
    notifyListeners();
    unawaited(SyncService.pushFocusSession(session));
  }

  /// Single range query instead of 7 sequential queries.
  Future<List<int>> getWeeklyFocusSeconds() async {
    final now = DateTime.now();
    final start = now.subtract(const Duration(days: 6));
    final startStr =
        '${start.year}-${start.month.toString().padLeft(2, '0')}-${start.day.toString().padLeft(2, '0')}';
    final sessions = await DatabaseService.getFocusSessionsDateRange(
      startStr,
      todayDate,
    );
    final byDate = <String, int>{};
    for (final s in sessions) {
      byDate[s.sessionDate] = (byDate[s.sessionDate] ?? 0) + s.durationSeconds;
    }
    return [
      for (int i = 6; i >= 0; i--)
        byDate[_dateKey(now.subtract(Duration(days: i)))] ?? 0,
    ];
  }

  String _dateKey(DateTime date) =>
      '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';

  // ============ Countdown ops ============

  Future<void> addCountdown(Countdown countdown) async {
    await DatabaseService.insertCountdown(countdown);
    _countdowns.add(countdown);
    _countdowns.sort((a, b) => a.targetDateTime.compareTo(b.targetDateTime));
    notifyListeners();
    unawaited(SyncService.pushCountdown(countdown));
  }

  Future<void> updateCountdown(Countdown countdown) async {
    await DatabaseService.updateCountdown(countdown);
    final i = _countdowns.indexWhere((c) => c.id == countdown.id);
    if (i >= 0) _countdowns[i] = countdown;
    _countdowns.sort((a, b) => a.targetDateTime.compareTo(b.targetDateTime));
    notifyListeners();
    unawaited(SyncService.pushCountdown(countdown));
  }

  Future<void> deleteCountdown(String id) async {
    await DatabaseService.deleteCountdown(id);
    _countdowns.removeWhere((c) => c.id == id);
    notifyListeners();
    unawaited(SyncService.deleteRemoteCountdown(id));
  }

  // ============ Sleep ============

  Future<void> recordSleep(SleepRecord record) async {
    await DatabaseService.upsertSleepRecord(record);
    // Update in-memory: replace if same date exists, else add
    final i = _sleepRecords.indexWhere(
      (r) => r.recordDate == record.recordDate,
    );
    if (i >= 0) {
      _sleepRecords[i] = record;
    } else {
      _sleepRecords.insert(0, record);
    }
    notifyListeners();
    unawaited(SyncService.pushSleepRecord(record));
  }

  // ============ Settings ============

  Future<void> setUserName(String name) async {
    _userName = name;
    await DatabaseService.setSetting('user_name', name);
    notifyListeners();
    unawaited(SyncService.pushUserSettings());
  }

  Future<void> setAvatarPath(String? path) async {
    _avatarPath = path;
    if (path == null || path.isEmpty) {
      final db = await DatabaseService.database;
      await db.delete(
        'user_settings',
        where: 'key = ?',
        whereArgs: ['avatar_path'],
      );
    } else {
      await DatabaseService.setSetting('avatar_path', path);
    }
    notifyListeners();
    unawaited(SyncService.pushUserSettings());
  }

  Future<void> deleteAllData() async {
    final oldAvatarPath = _avatarPath;
    await DatabaseService.deleteAllData();
    if (oldAvatarPath != null) {
      final avatar = File(oldAvatarPath);
      if (await avatar.exists()) await avatar.delete();
    }
    _todos = [];
    _habits = [];
    _countdowns = [];
    _todaySessions = [];
    _sleepRecords = [];
    _allSessions = [];
    _userName = '离线用户';
    _avatarPath = null;
    _deviceId = await DeviceIdentityService.getOrCreateDeviceId();
    _firstUsedDate = todayDate;
    _lifetimeTodosCompleted = 0;
    _lifetimeHabitsCompleted = 0;
    _lastRolloverDate = '';
    notifyListeners();
  }

  // ============ Computed stats ============

  void _pushAllLocal() {
    for (final t in _todos) {
      unawaited(SyncService.pushTodo(t));
    }
    for (final h in _habits) {
      unawaited(SyncService.pushHabit(h));
    }
    for (final c in _countdowns) {
      unawaited(SyncService.pushCountdown(c));
    }
    for (final s in _todaySessions) {
      unawaited(SyncService.pushFocusSession(s));
    }
    for (final r in _sleepRecords) {
      unawaited(SyncService.pushSleepRecord(r));
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
