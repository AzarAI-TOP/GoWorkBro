import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/core/config/supabase_config.dart';
import 'package:goworkbro/core/database/repositories/settings_repository.dart';
import 'package:goworkbro/core/sync/sync_table_registry.dart';

/// Sync service — bridges local SQLite and remote Supabase.
///
/// Strategy: **Local-first with background push + startup pull.**
///
/// 1. All reads come from local SQLite (instant, offline-capable).
/// 2. All writes go to local SQLite first, then push to Supabase in background.
/// 3. On app startup, pull remote changes and merge (last-write-wins by updated_at).
/// 4. Realtime subscription keeps local cache fresh while app is running.
///
/// Tables participating in sync are declared declaratively in
/// [syncTables] (sync_table_registry.dart) — pull and realtime here are a
/// single loop over that registry instead of per-table code.
class SyncService {
  static SupabaseClient? _client;
  static bool _isSyncing = false;
  static final List<RealtimeChannel> _channels = [];

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
  static const profileKeys = ['user_name', 'avatar_path'];

  // ============ Startup Pull ============

  /// Pulls every registered table once (in registry order).
  static Future<void> pullAll() async {
    if (_client == null || _isSyncing) return;
    _isSyncing = true;

    try {
      for (final table in syncTables) {
        await _pullTable(table.name, table.applyRemote);
      }
    } catch (e) {
      debugPrint('Pull failed: $e');
    } finally {
      _isSyncing = false;
    }
  }

  static Future<void> _pullTable(
    String table,
    Future<void> Function(Map<String, dynamic> row) applyRemote,
  ) async {
    final uid = currentUserId;
    if (uid == null) return;
    final response = await _client!
        .from(table)
        .select()
        .eq('user_id', uid);
    for (final row in response) {
      await applyRemote(row);
    }
  }

  // ============ Push (per-table) ============
  // Push stays explicit per table because each row payload is shaped
  // differently (foreign keys, booleans, timestamp formatting).

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
        final value = await SettingsRepository.get(key);
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

    for (final table in syncTables) {
      final channel = _client!
          .channel('public:${table.name}')
          .onPostgresChanges(
            event: table.event,
            schema: 'public',
            table: table.name,
            callback: (payload) {
              table.applyRemote(payload.newRecord);
            },
          )
          .subscribe();
      _channels.add(channel);
    }
  }

  static void dispose() {
    for (final channel in _channels) {
      channel.unsubscribe();
    }
    _channels.clear();
  }
}
