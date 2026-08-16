import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/core/database/app_database.dart';
import 'package:goworkbro/core/device/device_identity_service.dart';
import 'package:goworkbro/core/sync/avatar_sync.dart';
import 'package:goworkbro/core/sync/sync_service.dart';
import 'package:goworkbro/core/utils/date_utils.dart';
import 'package:goworkbro/core/config/supabase_config.dart';
import 'package:goworkbro/core/database/repositories/countdown_repository.dart';
import 'package:goworkbro/core/database/repositories/focus_repository.dart';
import 'package:goworkbro/core/database/repositories/habit_repository.dart';
import 'package:goworkbro/core/database/repositories/settings_repository.dart';
import 'package:goworkbro/core/database/repositories/sleep_repository.dart';
import 'package:goworkbro/core/database/repositories/todo_repository.dart';

const _uuid = Uuid();

/// Central app state — manages all data and daily rollover logic.
/// Locale and theme mode live in AppLocaleProvider (single source of truth).
class AppProvider extends ChangeNotifier {
  AppProvider({DateTime Function()? now}) : _now = now ?? DateTime.now;

  final DateTime Function() _now;
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
  bool _lateNightModeEnabled = false;
  String _lateNightClosedThrough = '';
  Future<void>? _rolloverFuture;
  Timer? _rolloverTimer;
  Timer? _syncTimer;

  /// How often the background sync pull runs (covers realtime gaps on
  /// mobile: backgrounded apps, missed events, flaky connections).
  static const syncInterval = Duration(seconds: 60);

  List<Todo> get todos => _todos;
  List<Habit> get habits => _habits;
  List<Countdown> get countdowns => _countdowns;
  List<FocusSession> get todaySessions => _todaySessions;
  List<FocusSession> get syncSessionInventory =>
      List.unmodifiable(_allSessions);
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
  bool get lateNightModeEnabled => _lateNightModeEnabled;

  String get calendarDate => dateKeyOf(_now());

  String get todayDate {
    final now = _now();
    final calendar = dateKeyOf(now);
    return logicalDateKey(
      now: now,
      lateNightModeEnabled: _lateNightModeEnabled,
      sleepCheckedInForCalendarDate: _lateNightClosedThrough == calendar,
      lastRolloverDate: _lastRolloverDate,
    );
  }

  bool get isLateNightCarryoverActive => todayDate != calendarDate;

  Future<void> init() {
    if (_isInitialized) return Future.value();
    return _initializing ??= _initInternal();
  }

  Future<void> _initInternal() async {
    _userName = await SettingsRepository.get('user_name') ?? '离线用户';
    _deviceId = await DeviceIdentityService.getOrCreateDeviceId();
    // Display path: the device-local cached avatar file first; fall back to
    // the synced key for legacy installs that stored a local path there.
    _avatarPath =
        await SettingsRepository.get('avatar_local_path') ??
        await SettingsRepository.get('avatar_path');
    _lifetimeTodosCompleted =
        int.tryParse(
          await SettingsRepository.get('lifetime_todos_completed') ?? '0',
        ) ??
        0;
    _lifetimeHabitsCompleted =
        int.tryParse(
          await SettingsRepository.get('lifetime_habits_completed') ?? '0',
        ) ??
        0;
    _lastRolloverDate =
        await SettingsRepository.get('last_rollover_date') ?? '';
    _lateNightModeEnabled =
        await SettingsRepository.get('late_night_mode') == 'true';
    _lateNightClosedThrough =
        await SettingsRepository.get('late_night_closed_through') ?? '';
    // The sleep boundary participates in logical-day calculation, so it must
    // be loaded before the first rollover decision.
    _sleepRecords = await SleepRepository.getAll();
    _firstUsedDate =
        await SettingsRepository.get('first_used_date') ?? todayDate;

    var syncReady = false;
    if (isSupabaseConfigured) {
      await SyncService.initialize();
      syncReady =
          SyncService.isInitialized && SyncService.currentUserId != null;
      if (syncReady) {
        // Realtime writes land in SQLite; refresh the visible state when
        // they arrive so the UI updates live on both devices.
        SyncService.onRemoteChanged = _onRemoteChanged;
        await SyncService.pullAll();
        // Load the complete synced logical-day pair before any destructive
        // rollover decision is made.
        await refreshAll(notify: false);
      }
    }

    await _performDailyRollover();
    await refreshAll();

    if (syncReady) {
      // Self-heal legacy avatar rows (issue #12): a device-local avatar_path
      // is migrated to Storage and pushed as an object path.
      unawaited(SyncService.pushUserSettings());
      _pushAll();
      // Derive the profile after the remote profile has been applied.
      await applyAuthUser();
    }

    _rolloverTimer = Timer.periodic(const Duration(minutes: 1), (_) {
      if (_lastRolloverDate != todayDate) {
        _performDailyRollover().then((_) => refreshAll());
      }
    });
    _syncTimer = Timer.periodic(syncInterval, (_) {
      unawaited(syncNow());
    });

    _isInitialized = true;
    _initializing = null;
    notifyListeners();
  }

  /// Sync the visible profile with the signed-in Supabase user.
  ///
  /// Called after login / session restore / pull. If the user hasn't set a
  /// custom name yet (still the default "离线用户"), derive one from the
  /// account: `user_metadata.user_name` if present, otherwise the email
  /// prefix — so a logged-in user never sees "离线用户" in the UI.
  ///
  /// Only a name that came from real profile metadata is pushed to the
  /// cloud. The email-prefix fallback stays device-local so it can never
  /// race ahead of a pull and clobber a custom name pushed by another
  /// device (the next pull corrects it anyway).
  Future<void> applyAuthUser() async {
    if (!isSupabaseConfigured) return;
    final client = Supabase.instance.client;
    final user = client.auth.currentUser;
    if (user == null) return;

    final email = user.email;
    final meta = user.userMetadata;

    final currentName = await SettingsRepository.get('user_name');
    final isDefaultName = currentName == null || currentName == '离线用户';
    if (isDefaultName) {
      final metaName = (meta?['user_name'] as String?)?.trim();
      final fromMeta = metaName != null && metaName.isNotEmpty;
      final displayName = fromMeta
          ? metaName
          : (email != null && email.isNotEmpty)
          ? email.split('@').first
          : '离线用户';
      if (displayName != currentName) {
        await SettingsRepository.set('user_name', displayName);
        _userName = displayName;
        if (fromMeta) {
          unawaited(SyncService.pushUserSettings());
        }
      }
    }

    notifyListeners();
  }

  /// Called right after the user signs in (AuthGate).
  ///
  /// If the provider was already initialized earlier without a session
  /// (offline mode), the startup pull never ran — do it now. If init had
  /// not run yet, `init()` performs the full pull itself and this method
  /// has nothing left to do.
  Future<void> onSignedIn() async {
    final wasInitialized = _isInitialized;
    await init();
    if (!isSupabaseConfigured) return;
    await SyncService.initialize();
    if (!SyncService.isInitialized) return;
    if (wasInitialized) {
      SyncService.onRemoteChanged = _onRemoteChanged;
      await SyncService.pullAll();
      await _refreshAfterRemoteChange();
      unawaited(SyncService.pushUserSettings());
      _pushAll();
      await applyAuthUser();
    }
  }

  /// Pull remote changes and refresh the UI. Used by the periodic sync
  /// timer and by the app-resume hook (mobile).
  Future<void> syncNow() async {
    if (!isSupabaseConfigured) return;
    await SyncService.initialize();
    if (!SyncService.isInitialized || SyncService.currentUserId == null) {
      return;
    }
    await SyncService.pullAll();
    await _refreshAfterRemoteChange();
    await SyncService.pushUserSettings(onlyDirty: true);
    _pushAll();
  }

  void _onRemoteChanged() {
    // Re-pull the committed server snapshot instead of trusting that every
    // row in a bulk settings update has already reached this device's
    // realtime callback queue.
    unawaited(syncNow());
  }

  Future<void> _refreshAfterRemoteChange() async {
    await refreshAll(notify: false);
    if (_lastRolloverDate != todayDate) {
      await _performDailyRollover();
    } else {
      notifyListeners();
    }
  }

  @visibleForTesting
  Future<void> refreshAfterRemoteChangeForTesting() =>
      _refreshAfterRemoteChange();

  @override
  void dispose() {
    _rolloverTimer?.cancel();
    _syncTimer?.cancel();
    SyncService.dispose();
    super.dispose();
  }

  Future<void> _performDailyRollover() async {
    while (true) {
      final active = _rolloverFuture;
      if (active != null) {
        await active;
        if (_lastRolloverDate == todayDate) return;
        continue;
      }

      final targetDate = todayDate;
      final operation = _performDailyRolloverInternal(targetDate);
      _rolloverFuture = operation;
      try {
        await operation;
      } finally {
        if (identical(_rolloverFuture, operation)) _rolloverFuture = null;
      }
      return;
    }
  }

  Future<void> _performDailyRolloverInternal(String targetDate) async {
    if (_lastRolloverDate == targetDate) return;

    final deletedTodoIds = await TodoRepository.rollOver(targetDate);
    await HabitRepository.resetForNewDay(targetDate);
    final deletedCountdownIds = await CountdownRepository.cleanupExpired();

    await SettingsRepository.set('last_rollover_date', targetDate);
    _lastRolloverDate = targetDate;

    // Reload before mirroring: _pushAll must not re-push the just-deleted
    // completed todos / expired countdowns (they would resurrect on the
    // cloud side).
    await refreshAll();

    // Mirror the local rollover to the cloud: deleted rows would otherwise
    // come back on the next pull, and habit resets must reach the other
    // device.
    for (final id in deletedTodoIds) {
      unawaited(SyncService.deleteRemoteTodo(id));
    }
    for (final id in deletedCountdownIds) {
      unawaited(SyncService.deleteRemoteCountdown(id));
    }
    _pushAll();
  }

  /// Full reload from DB — used at startup, after sync pull and on realtime
  /// events. Also refreshes the visible profile (name / avatar), which is
  /// part of the synced user_settings table.
  Future<void> refreshAll({bool notify = true}) async {
    _todos = await TodoRepository.getAll();
    _habits = await HabitRepository.getAll();
    _countdowns = await CountdownRepository.getAll();
    _sleepRecords = await SleepRepository.getAll();
    _lateNightModeEnabled =
        await SettingsRepository.get('late_night_mode') == 'true';
    _lateNightClosedThrough =
        await SettingsRepository.get('late_night_closed_through') ?? '';
    _todaySessions = await FocusRepository.getByDate(todayDate);
    _allSessions = await FocusRepository.getAll();
    final name = await SettingsRepository.get('user_name');
    if (name != null && name.isNotEmpty) _userName = name;
    final avatar =
        await SettingsRepository.get('avatar_local_path') ??
        await SettingsRepository.get('avatar_path');
    _avatarPath = avatar;
    if (notify) notifyListeners();
  }

  // ============ TODO ops ============

  Future<void> addTodo(Todo todo) async {
    await TodoRepository.insert(todo);
    _todos.add(todo);
    notifyListeners();
    unawaited(SyncService.pushTodo(todo));
  }

  Future<void> updateTodo(Todo todo) async {
    await TodoRepository.update(todo);
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
      _lifetimeTodosCompleted = await SettingsRepository.incrementCounterOnce(
        counterKey: 'lifetime_todos_completed',
        eventKey: 'completion.todo.${todo.id}',
      );
    }
    await TodoRepository.update(updated);
    final i = _todos.indexWhere((t) => t.id == todo.id);
    if (i >= 0) _todos[i] = updated;

    if (isCompleting && todo.keepTomorrow) {
      await _createTomorrowCopyIfNeeded(todo);
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
      _lifetimeTodosCompleted = await SettingsRepository.incrementCounterOnce(
        counterKey: 'lifetime_todos_completed',
        eventKey: 'completion.todo.${current.id}',
      );
    }
    await TodoRepository.update(updated);
    _todos[i] = updated;

    if (current.keepTomorrow && !current.isCompleted) {
      await _createTomorrowCopyIfNeeded(current);
    }

    notifyListeners();
    unawaited(SyncService.pushTodo(updated));
  }

  /// For keepTomorrow todos: create an incomplete copy for the next day
  /// unless one already exists (idempotent — safe on double-taps).
  Future<void> _createTomorrowCopyIfNeeded(Todo completed) async {
    final hasIncompleteCopy = _todos.any(
      (t) =>
          t.title == completed.title &&
          !t.isCompleted &&
          t.createdDate != completed.createdDate,
    );
    if (hasIncompleteCopy) return;

    final newTodo = Todo(
      id: _uuid.v4(),
      title: completed.title,
      timingType: completed.timingType,
      durationMinutes: completed.durationMinutes,
      isCompleted: false,
      sortOrder: completed.sortOrder,
      keepTomorrow: true,
      createdDate: DateTime.now().toIso8601String(),
    );
    await TodoRepository.insert(newTodo);
    _todos.add(newTodo);
    unawaited(SyncService.pushTodo(newTodo));
  }

  Future<void> deleteTodo(String id) async {
    await TodoRepository.deleteById(id);
    _todos.removeWhere((t) => t.id == id);
    notifyListeners();
    unawaited(SyncService.deleteRemoteTodo(id));
  }

  Future<void> reorderTodos(int oldIndex, int newIndex) async {
    if (newIndex > oldIndex) newIndex--;
    final item = _todos.removeAt(oldIndex);
    _todos.insert(newIndex, item);
    // Batch: update sortOrder for all items in a single DB transaction
    final db = await AppDatabase.database;
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
    await HabitRepository.insert(habit);
    _habits.add(habit);
    notifyListeners();
    unawaited(SyncService.pushHabit(habit));
  }

  Future<void> updateHabit(Habit habit) async {
    await HabitRepository.update(habit);
    final i = _habits.indexWhere((h) => h.id == habit.id);
    if (i >= 0) _habits[i] = habit;
    notifyListeners();
    unawaited(SyncService.pushHabit(habit));
  }

  Future<void> incrementHabit(Habit habit) async {
    if (habit.currentCount >= habit.targetCount) return;
    final updated = habit.copyWith(currentCount: habit.currentCount + 1);
    if (!habit.isCompleted && updated.isCompleted) {
      _lifetimeHabitsCompleted = await SettingsRepository.incrementCounterOnce(
        counterKey: 'lifetime_habits_completed',
        eventKey: 'completion.habit.${habit.id}.$todayDate',
      );
    }
    await HabitRepository.update(updated);
    final i = _habits.indexWhere((h) => h.id == habit.id);
    if (i >= 0) _habits[i] = updated;
    notifyListeners();
    unawaited(SyncService.pushHabit(updated));
  }

  Future<void> decrementHabit(Habit habit) async {
    if (habit.currentCount == 0) return;
    final updated = habit.copyWith(currentCount: habit.currentCount - 1);
    await HabitRepository.update(updated);
    final i = _habits.indexWhere((h) => h.id == habit.id);
    if (i >= 0) _habits[i] = updated;
    notifyListeners();
    unawaited(SyncService.pushHabit(updated));
  }

  Future<void> deleteHabit(String id) async {
    await HabitRepository.deleteById(id);
    _habits.removeWhere((h) => h.id == id);
    notifyListeners();
    unawaited(SyncService.deleteRemoteHabit(id));
  }

  Future<void> reorderHabits(int oldIndex, int newIndex) async {
    if (newIndex > oldIndex) newIndex--;
    final item = _habits.removeAt(oldIndex);
    _habits.insert(newIndex, item);
    final db = await AppDatabase.database;
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
    // The provider owns day-bucketing so every recording path follows the
    // same calendar/late-night rule instead of trusting individual callers.
    final normalized = FocusSession(
      id: session.id,
      todoId: session.todoId,
      sourceType: session.sourceType,
      sourceTitle: session.sourceTitle,
      startTime: session.startTime,
      endTime: session.endTime,
      durationSeconds: session.durationSeconds,
      sessionDate: todayDate,
    );
    await FocusRepository.insert(normalized);
    _todaySessions.add(normalized);
    _allSessions.add(normalized);
    notifyListeners();
    unawaited(SyncService.pushFocusSession(normalized));
  }

  /// Single range query instead of 7 sequential queries.
  Future<List<int>> getWeeklyFocusSeconds() async {
    final logicalToday = DateTime.parse(todayDate);
    final start = logicalToday.subtract(const Duration(days: 6));
    final startStr =
        '${start.year}-${start.month.toString().padLeft(2, '0')}-${start.day.toString().padLeft(2, '0')}';
    final sessions = await FocusRepository.getDateRange(startStr, todayDate);
    final byDate = <String, int>{};
    for (final s in sessions) {
      byDate[s.sessionDate] = (byDate[s.sessionDate] ?? 0) + s.durationSeconds;
    }
    return [
      for (int i = 6; i >= 0; i--)
        byDate[dateKeyOf(logicalToday.subtract(Duration(days: i)))] ?? 0,
    ];
  }

  // ============ Countdown ops ============

  Future<void> addCountdown(Countdown countdown) async {
    await CountdownRepository.insert(countdown);
    _countdowns.add(countdown);
    _countdowns.sort((a, b) => a.targetDateTime.compareTo(b.targetDateTime));
    notifyListeners();
    unawaited(SyncService.pushCountdown(countdown));
  }

  Future<void> updateCountdown(Countdown countdown) async {
    await CountdownRepository.update(countdown);
    final i = _countdowns.indexWhere((c) => c.id == countdown.id);
    if (i >= 0) _countdowns[i] = countdown;
    _countdowns.sort((a, b) => a.targetDateTime.compareTo(b.targetDateTime));
    notifyListeners();
    unawaited(SyncService.pushCountdown(countdown));
  }

  Future<void> deleteCountdown(String id) async {
    await CountdownRepository.deleteById(id);
    _countdowns.removeWhere((c) => c.id == id);
    notifyListeners();
    unawaited(SyncService.deleteRemoteCountdown(id));
  }

  // ============ Sleep ============

  Future<void> recordWorkout({
    required String recordDate,
    required int durationMinutes,
    required String description,
  }) async {
    final persisted = await SleepRepository.upsertWorkout(
      recordDate: recordDate,
      durationMinutes: durationMinutes,
      description: description,
    );
    final i = _sleepRecords.indexWhere(
      (record) => record.recordDate == persisted.recordDate,
    );
    if (i >= 0) {
      _sleepRecords[i] = persisted;
    } else {
      _sleepRecords.add(persisted);
    }
    _sleepRecords.sort((a, b) => b.recordDate.compareTo(a.recordDate));
    notifyListeners();
    unawaited(SyncService.pushSleepRecord(persisted));
  }

  Future<void> recordSleep(
    SleepRecord record, {
    String? closesLogicalDayThrough,
  }) async {
    final previousLogicalDate = todayDate;
    final persisted = await SleepRepository.upsert(record);
    // Update in-memory: replace if same date exists, else add
    final i = _sleepRecords.indexWhere(
      (r) => r.recordDate == persisted.recordDate,
    );
    if (i >= 0) {
      _sleepRecords[i] = persisted;
    } else {
      _sleepRecords.insert(0, persisted);
    }
    _sleepRecords.sort((a, b) => b.recordDate.compareTo(a.recordDate));
    if (closesLogicalDayThrough != null) {
      if (_lateNightClosedThrough.isEmpty ||
          _lateNightClosedThrough.compareTo(closesLogicalDayThrough) < 0) {
        _lateNightClosedThrough = closesLogicalDayThrough;
        await SettingsRepository.setSyncedLocal(
          'late_night_closed_through',
          _lateNightClosedThrough,
        );
      }
      unawaited(SyncService.pushUserSettings(onlyDirty: true));
    }
    if (previousLogicalDate != todayDate) {
      await _performDailyRollover();
    } else {
      notifyListeners();
    }
    unawaited(SyncService.pushSleepRecord(persisted));
  }

  // ============ Settings ============

  Future<void> setUserName(String name) async {
    _userName = name;
    await SettingsRepository.set('user_name', name);
    notifyListeners();
    unawaited(SyncService.pushUserSettings());
  }

  Future<void> setLateNightModeEnabled(bool enabled) async {
    if (_lateNightModeEnabled == enabled) return;
    final previousLogicalDate = todayDate;
    _lateNightModeEnabled = enabled;
    await SettingsRepository.setSyncedLocal(
      'late_night_mode',
      enabled.toString(),
    );
    if (previousLogicalDate != todayDate) {
      await _performDailyRollover();
    } else {
      notifyListeners();
    }
    unawaited(SyncService.pushUserSettings(onlyDirty: true));
  }

  Future<void> setAvatarPath(String? path) async {
    _avatarPath = path;
    if (path == null || path.isEmpty) {
      final db = await AppDatabase.database;
      final oldStoragePath = await SettingsRepository.get('avatar_path');
      await db.delete(
        'user_settings',
        where: 'key IN (?, ?)',
        whereArgs: ['avatar_path', 'avatar_local_path'],
      );
      // Remove the cloud copy too — the realtime DELETE propagates to the
      // other device. A legacy local-path value cannot be a storage path.
      if (oldStoragePath != null && AvatarSync.isStoragePath(oldStoragePath)) {
        unawaited(SyncService.removeRemoteAvatar(oldStoragePath));
      } else {
        unawaited(SyncService.deleteRemoteUserSetting('avatar_path'));
      }
    } else {
      // Device-local display cache + Storage upload for cross-device sync.
      await SettingsRepository.set('avatar_local_path', path);
      unawaited(SyncService.uploadAvatarAndPush(path));
    }
    notifyListeners();
  }

  Future<void> deleteAllData() async {
    final oldAvatarPath = _avatarPath;
    await AppDatabase.deleteAllData();
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
    _lateNightModeEnabled = false;
    _lateNightClosedThrough = '';
    notifyListeners();
  }

  // ============ Computed stats ============

  void _pushAll() {
    unawaited(
      SyncService.pushAll(
        todos: _todos,
        habits: _habits,
        countdowns: _countdowns,
        sessions: syncSessionInventory,
        sleepRecords: _sleepRecords,
      ),
    );
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
