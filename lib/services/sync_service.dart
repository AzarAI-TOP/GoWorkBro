import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import '../models/models.dart';
import 'database_service.dart';
import 'supabase_config.dart';

/// Sync service — bridges local SQLite and remote Supabase.
///
/// Strategy: **Local-first with background push + startup pull.**
///
/// 1. All reads come from local SQLite (instant, offline-capable).
/// 2. All writes go to local SQLite first, then push to Supabase in background.
/// 3. On app startup, pull remote changes and merge (last-write-wins by updated_at).
/// 4. Realtime subscription keeps local cache fresh while app is running.
class SyncService {
  static SupabaseClient? _client;
  static bool _isSyncing = false;
  static RealtimeChannel? _todosChannel;
  static RealtimeChannel? _habitsChannel;
  static RealtimeChannel? _countdownsChannel;
  static RealtimeChannel? _sleepChannel;
  static RealtimeChannel? _focusChannel;

  static bool get isConfigured => isSupabaseConfigured;
  static bool get isInitialized => _client != null;

  static Future<void> initialize() async {
    if (!isSupabaseConfigured) return;
    if (_client != null) return;

    await Supabase.initialize(
      url: supabaseUrl,
      anonKey: supabaseAnonKey,
      debug: kDebugMode,
    );
    _client = Supabase.instance.client;
    // Auth is handled by the AuthScreen — SyncService just uses the
    // authenticated session. No automatic sign-in here.

    _setupRealtime();
  }

  static String? get currentUserId => _client?.auth.currentUser?.id;

  // ============ Startup Pull ============

  /// Pull all data from Supabase and merge into local SQLite.
  /// Called on app startup after local data is loaded.
  static Future<void> pullAll() async {
    if (_client == null || _isSyncing) return;
    _isSyncing = true;

    try {
      await _pullTable('todos', (rows) async {
        for (final row in rows) {
          final remoteUpdatedAt = DateTime.parse(row['updated_at'] as String);
          final local = await DatabaseService.getTodoById(row['id'] as String);
          if (local == null || _remoteNewer(remoteUpdatedAt, local)) {
            await DatabaseService.upsertTodoFromRemote(row);
          }
        }
      });

      await _pullTable('habits', (rows) async {
        for (final row in rows) {
          final remoteUpdatedAt = DateTime.parse(row['updated_at'] as String);
          final local = await DatabaseService.getHabitById(row['id'] as String);
          if (local == null || _remoteNewer(remoteUpdatedAt, local)) {
            await DatabaseService.upsertHabitFromRemote(row);
          }
        }
      });

      await _pullTable('countdowns', (rows) async {
        for (final row in rows) {
          final remoteUpdatedAt = DateTime.parse(row['updated_at'] as String);
          final local = await DatabaseService.getCountdownById(row['id'] as String);
          if (local == null || _remoteNewer(remoteUpdatedAt, local)) {
            await DatabaseService.upsertCountdownFromRemote(row);
          }
        }
      });

      await _pullTable('sleep_records', (rows) async {
        for (final row in rows) {
          final remoteUpdatedAt = DateTime.parse(row['updated_at'] as String);
          final local = await DatabaseService.getSleepRecordById(row['id'] as String);
          if (local == null || _remoteNewer(remoteUpdatedAt, local)) {
            await DatabaseService.upsertSleepFromRemote(row);
          }
        }
      });

      // Focus sessions: pull all (append-only, no conflict)
      await _pullTable('focus_sessions', (rows) async {
        for (final row in rows) {
          await DatabaseService.insertFocusSessionIfNotExists(row);
        }
      });
    } catch (e) {
      debugPrint('Pull failed: $e');
    } finally {
      _isSyncing = false;
    }
  }

  static Future<void> _pullTable(
    String table,
    Future<void> Function(List<Map<String, dynamic>> rows) handler,
  ) async {
    final uid = currentUserId;
    if (uid == null) return;
    final response = await _client!
        .from(table)
        .select()
        .eq('user_id', uid);
    await handler(response);
  }

  static bool _remoteNewer(DateTime remoteUpdatedAt, dynamic localModel) {
    // For simplicity, always accept remote if local doesn't have updated_at.
    // In practice, we compare timestamps. For now, remote wins if it's newer.
    return true; // TODO: add updated_at to local models for proper comparison
  }

  // ============ Push (per-table) ============

  static Future<void> pushTodo(Todo todo) async {
    if (_client == null) return;
    final uid = currentUserId;
    if (uid == null) return;
    try {
      await _client!.from('todos').upsert({
        'id': todo.id,
        'user_id': uid,
        'title': todo.title,
        'timing_type': todo.timingType.value,
        'duration_minutes': todo.durationMinutes,
        'is_completed': todo.isCompleted,
        'sort_order': todo.sortOrder,
        'keep_tomorrow': todo.keepTomorrow,
        'created_date': todo.createdDate,
        'completed_date': todo.completedDate,
        'actual_duration_seconds': todo.actualDurationSeconds,
        'updated_at': DateTime.now().toUtc().toIso8601String(),
      });
    } catch (e) {
      debugPrint('Push todo failed: $e');
    }
  }

  static Future<void> pushHabit(Habit habit) async {
    if (_client == null) return;
    final uid = currentUserId;
    if (uid == null) return;
    try {
      await _client!.from('habits').upsert({
        'id': habit.id,
        'user_id': uid,
        'title': habit.title,
        'target_count': habit.targetCount,
        'unit': habit.unit,
        'sort_order': habit.sortOrder,
        'created_date': habit.createdDate,
        'current_count': habit.currentCount,
        'last_reset_date': habit.lastResetDate,
        'updated_at': DateTime.now().toUtc().toIso8601String(),
      });
    } catch (e) {
      debugPrint('Push habit failed: $e');
    }
  }

  static Future<void> pushFocusSession(FocusSession session) async {
    if (_client == null) return;
    final uid = currentUserId;
    if (uid == null) return;
    try {
      await _client!.from('focus_sessions').upsert({
        'id': session.id,
        'user_id': uid,
        'todo_id': session.todoId,
        'source_type': session.sourceType,
        'source_title': session.sourceTitle,
        'start_time': session.startTime,
        'end_time': session.endTime,
        'duration_seconds': session.durationSeconds,
        'session_date': session.sessionDate,
      });
    } catch (e) {
      debugPrint('Push focus session failed: $e');
    }
  }

  static Future<void> pushCountdown(Countdown countdown) async {
    if (_client == null) return;
    final uid = currentUserId;
    if (uid == null) return;
    try {
      await _client!.from('countdowns').upsert({
        'id': countdown.id,
        'user_id': uid,
        'title': countdown.title,
        'target_datetime': countdown.targetDateTime.toIso8601String(),
        'created_date': countdown.createdDate,
        'color_index': countdown.colorIndex,
        'updated_at': DateTime.now().toUtc().toIso8601String(),
      });
    } catch (e) {
      debugPrint('Push countdown failed: $e');
    }
  }

  static Future<void> pushSleepRecord(SleepRecord record) async {
    if (_client == null) return;
    final uid = currentUserId;
    if (uid == null) return;
    try {
      await _client!.from('sleep_records').upsert({
        'id': record.id,
        'user_id': uid,
        'record_date': record.recordDate,
        'wake_time': record.wakeTime,
        'sleep_time': record.sleepTime,
        'note': record.note,
        'updated_at': DateTime.now().toUtc().toIso8601String(),
      });
    } catch (e) {
      debugPrint('Push sleep record failed: $e');
    }
  }

  static Future<void> deleteRemoteTodo(String id) async {
    if (_client == null) return;
    try { await _client!.from('todos').delete().eq('id', id); }
    catch (e) { debugPrint('Delete remote todo failed: $e'); }
  }

  static Future<void> deleteRemoteHabit(String id) async {
    if (_client == null) return;
    try { await _client!.from('habits').delete().eq('id', id); }
    catch (e) { debugPrint('Delete remote habit failed: $e'); }
  }

  static Future<void> deleteRemoteCountdown(String id) async {
    if (_client == null) return;
    try { await _client!.from('countdowns').delete().eq('id', id); }
    catch (e) { debugPrint('Delete remote countdown failed: $e'); }
  }

  // ============ Realtime ============

  static void _setupRealtime() {
    if (_client == null) return;

    _todosChannel = _client!
        .channel('public:todos')
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'todos',
          callback: (payload) {
            debugPrint('Realtime todo change: ${payload.eventType}');
            if (payload.newRecord != null) {
              DatabaseService.upsertTodoFromRemote(payload.newRecord!);
            }
          },
        )
        .subscribe();

    _habitsChannel = _client!
        .channel('public:habits')
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'habits',
          callback: (payload) {
            if (payload.newRecord != null) {
              DatabaseService.upsertHabitFromRemote(payload.newRecord!);
            }
          },
        )
        .subscribe();

    _countdownsChannel = _client!
        .channel('public:countdowns')
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'countdowns',
          callback: (payload) {
            if (payload.newRecord != null) {
              DatabaseService.upsertCountdownFromRemote(payload.newRecord!);
            }
          },
        )
        .subscribe();

    _sleepChannel = _client!
        .channel('public:sleep_records')
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'sleep_records',
          callback: (payload) {
            if (payload.newRecord != null) {
              DatabaseService.upsertSleepFromRemote(payload.newRecord!);
            }
          },
        )
        .subscribe();

    _focusChannel = _client!
        .channel('public:focus_sessions')
        .onPostgresChanges(
          event: PostgresChangeEvent.insert,
          schema: 'public',
          table: 'focus_sessions',
          callback: (payload) {
            if (payload.newRecord != null) {
              DatabaseService.insertFocusSessionIfNotExists(payload.newRecord!);
            }
          },
        )
        .subscribe();
  }

  static void dispose() {
    _todosChannel?.unsubscribe();
    _habitsChannel?.unsubscribe();
    _countdownsChannel?.unsubscribe();
    _sleepChannel?.unsubscribe();
    _focusChannel?.unsubscribe();
  }
}
