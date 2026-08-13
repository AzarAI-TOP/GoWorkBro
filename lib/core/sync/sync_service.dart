import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/core/database/app_database.dart';
import 'package:goworkbro/core/config/supabase_config.dart';

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
  static RealtimeChannel? _settingsChannel;

  static bool get isConfigured => isSupabaseConfigured;
  static bool get isInitialized => _client != null;

  /// Initialize — does NOT call Supabase.initialize (that's done in main.dart).
  /// Just grabs the already-initialized client and sets up realtime.
  static Future<void> initialize() async {
    if (!isSupabaseConfigured) return;
    if (_client != null) return;

    // Supabase.initialize is called in main.dart before runApp.
    // Here we just reference the singleton client.
    _client = Supabase.instance.client;
    _setupRealtime();
  }

  static String? get currentUserId => _client?.auth.currentUser?.id;

  /// Profile keys synced between local SQLite and cloud `user_settings`.
  /// Other settings (counters, first_used_date, …) stay device-local.
  static const profileKeys = ['user_name', 'avatar_path'];

  // ============ Startup Pull ============

  static Future<void> pullAll() async {
    if (_client == null || _isSyncing) return;
    _isSyncing = true;

    try {
      await _pullTable('todos', (rows) async {
        for (final row in rows) {
          await DatabaseService.upsertTodoFromRemote(row);
        }
      });

      await _pullTable('habits', (rows) async {
        for (final row in rows) {
          await DatabaseService.upsertHabitFromRemote(row);
        }
      });

      await _pullTable('countdowns', (rows) async {
        for (final row in rows) {
          await DatabaseService.upsertCountdownFromRemote(row);
        }
      });

      await _pullTable('sleep_records', (rows) async {
        for (final row in rows) {
          await DatabaseService.upsertSleepFromRemote(row);
        }
      });

      await _pullTable('focus_sessions', (rows) async {
        for (final row in rows) {
          await DatabaseService.insertFocusSessionIfNotExists(row);
        }
      });

      // Profile settings (user_name / avatar_path) — cloud wins on startup,
      // so a name set on another device shows up here too.
      await _pullTable('user_settings', (rows) async {
        for (final row in rows) {
          final key = row['key'] as String?;
          if (key == null || !profileKeys.contains(key)) continue;
          final value = row['value'] as String?;
          if (value != null) {
            await DatabaseService.setSetting(key, value);
          }
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
        'workout_time': record.workoutTime,
        'note': record.note,
        'updated_at': DateTime.now().toUtc().toIso8601String(),
      });
    } catch (e) {
      debugPrint('Push sleep record failed: $e');
    }
  }

  /// Push the local profile (user_name / avatar_path) to cloud user_settings.
  static Future<void> pushUserSettings() async {
    if (_client == null) return;
    final uid = currentUserId;
    if (uid == null) return;
    try {
      for (final key in profileKeys) {
        final value = await DatabaseService.getSetting(key);
        if (value == null) continue;
        await _client!.from('user_settings').upsert({
          'key': key,
          'user_id': uid,
          'value': value,
          'updated_at': DateTime.now().toUtc().toIso8601String(),
        });
      }
    } catch (e) {
      debugPrint('Push user settings failed: $e');
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
    // Note: Supabase RLS policies filter by user_id automatically.
    // Realtime payloads only contain rows the authenticated user can SELECT.

    _todosChannel = _client!
        .channel('public:todos')
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'todos',
          callback: (payload) {
            DatabaseService.upsertTodoFromRemote(payload.newRecord);
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
            DatabaseService.upsertHabitFromRemote(payload.newRecord);
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
            DatabaseService.upsertCountdownFromRemote(payload.newRecord);
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
            DatabaseService.upsertSleepFromRemote(payload.newRecord);
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
            DatabaseService.insertFocusSessionIfNotExists(payload.newRecord);
          },
        )
        .subscribe();

    _settingsChannel = _client!
        .channel('public:user_settings')
        .onPostgresChanges(
          event: PostgresChangeEvent.all,
          schema: 'public',
          table: 'user_settings',
          callback: (payload) {
            final row = payload.newRecord;
            final key = row['key'] as String?;
            if (key == null || !profileKeys.contains(key)) return;
            final value = row['value'] as String?;
            if (value != null) {
              DatabaseService.setSetting(key, value);
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
    _settingsChannel?.unsubscribe();
  }
}
