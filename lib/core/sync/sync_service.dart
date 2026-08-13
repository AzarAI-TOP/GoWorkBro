import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/core/config/supabase_config.dart';
import 'package:goworkbro/core/database/repositories/settings_repository.dart';
import 'package:goworkbro/core/sync/avatar_sync.dart';
import 'package:goworkbro/core/sync/sync_compare.dart';
import 'package:goworkbro/core/sync/sync_table_registry.dart';

/// Sync service — bridges local SQLite and remote Supabase.
///
/// Strategy: **Local-first with background push + pull on startup / resume /
/// polling, plus realtime as the fast path.**
///
/// 1. All reads come from local SQLite (instant, offline-capable).
/// 2. All writes go to local SQLite first, then push to Supabase in background.
/// 3. Pull merges with last-write-wins by `updated_at` (todos / habits /
///    countdowns), so a periodic pull never rolls back newer local edits.
/// 4. Realtime subscriptions apply inserts/updates/deletes to the local cache
///    and notify the UI. Realtime is best-effort: mobile backgrounded apps and
///    missed events are covered by the 60s polling fallback.
///
/// Tables participating in sync are declared declaratively in
/// [syncTables] (sync_table_registry.dart) — pull and realtime here are a
/// single loop over that registry instead of per-table code.
class SyncService {
  static SupabaseClient? _client;
  static bool _isSyncing = false;
  static final List<RealtimeChannel> _channels = [];
  static Timer? _notifyTimer;

  /// Called (debounced) after remote changes land in the local database so
  /// the UI can refresh. Set by AppProvider.
  static VoidCallback? onRemoteChanged;

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

  // ============ Startup Pull ============

  /// Pulls every registered table once (in registry order).
  /// Per-table failures are logged and skipped so one broken table cannot
  /// block the rest of the pull.
  static Future<void> pullAll() async {
    if (_client == null || _isSyncing) return;
    _isSyncing = true;

    try {
      for (final table in syncTables) {
        try {
          await _pullTable(table.name, table.applyRemote);
        } catch (e) {
          debugPrint('Pull ${table.name} failed: $e');
        }
      }
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

  // ============ Push ============
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

  /// Push every local row whose mergeable tables are newer than (or missing
  /// from) the cloud. This replaces the old "push everything with a fresh
  /// timestamp" startup flush, which could resurrect stale content over a
  /// newer remote edit.
  static Future<void> pushAll({
    required List<Todo> todos,
    required List<Habit> habits,
    required List<Countdown> countdowns,
    required List<FocusSession> sessions,
    required List<SleepRecord> sleepRecords,
  }) async {
    if (_client == null) return;
    final uid = currentUserId;
    if (uid == null) return;

    Future<Map<String, String?>> remoteStamps(String table) async {
      try {
        final rows = await _client!
            .from(table)
            .select('id, updated_at')
            .eq('user_id', uid);
        return {
          for (final r in rows) r['id'] as String: r['updated_at'] as String?,
        };
      } catch (e) {
        debugPrint('Fetch $table stamps failed: $e');
        return const {};
      }
    }

    final todoStamps = await remoteStamps('todos');
    for (final t in todos) {
      if (isLocalNewer(t.updatedAt, todoStamps[t.id])) {
        unawaited(pushTodo(t));
      }
    }
    final habitStamps = await remoteStamps('habits');
    for (final h in habits) {
      if (isLocalNewer(h.updatedAt, habitStamps[h.id])) {
        unawaited(pushHabit(h));
      }
    }
    final countdownStamps = await remoteStamps('countdowns');
    for (final c in countdowns) {
      if (isLocalNewer(c.updatedAt, countdownStamps[c.id])) {
        unawaited(pushCountdown(c));
      }
    }
    // Append-only / idempotent tables are pushed as-is.
    for (final s in sessions) {
      unawaited(pushFocusSession(s));
    }
    for (final r in sleepRecords) {
      unawaited(pushSleepRecord(r));
    }
  }

  /// Push the local profile (user_name / avatar_path) to cloud user_settings.
  /// A legacy device-local avatar path is transparently migrated to Storage
  /// before pushing — only Storage object paths ever leave the device.
  static Future<void> pushUserSettings() async {
    if (_client == null) return;
    final uid = currentUserId;
    if (uid == null) return;
    try {
      for (final key in profileKeys) {
        final value = await SettingsRepository.get(key);
        if (value == null) continue;
        var cloudValue = value;
        if (key == 'avatar_path' && !AvatarSync.isStoragePath(value)) {
          try {
            cloudValue = await AvatarSync.upload(_client!, uid, value);
            await SettingsRepository.set('avatar_path', cloudValue);
          } catch (e) {
            debugPrint('Avatar migration upload failed: $e');
            continue; // never push a device-local path to the cloud
          }
        }
        await _client!.from('user_settings').upsert({
          'key': key,
          'user_id': uid,
          'value': cloudValue,
          'updated_at': DateTime.now().toUtc().toIso8601String(),
        });
      }
    } catch (e) {
      debugPrint('Push user settings failed: $e');
    }
  }

  // ============ Avatar ============

  /// Upload the picked avatar file to Storage and push the object path.
  /// On failure (bucket missing / offline) the avatar stays device-local.
  static Future<void> uploadAvatarAndPush(String localPath) async {
    if (_client == null) return;
    final uid = currentUserId;
    if (uid == null) return;
    try {
      final storagePath = await AvatarSync.upload(_client!, uid, localPath);
      await SettingsRepository.set('avatar_path', storagePath);
      await pushUserSettings();
    } catch (e) {
      debugPrint('Avatar upload failed: $e');
    }
  }

  /// Remove the avatar object from Storage and delete the cloud
  /// user_settings row (the realtime DELETE propagates the removal).
  static Future<void> removeRemoteAvatar(String storagePath) async {
    if (_client == null) return;
    await AvatarSync.remove(_client!, storagePath);
    await deleteRemoteUserSetting('avatar_path');
  }

  static Future<void> deleteRemoteUserSetting(String key) async {
    if (_client == null) return;
    final uid = currentUserId;
    if (uid == null) return;
    try {
      await _client!
          .from('user_settings')
          .delete()
          .eq('key', key)
          .eq('user_id', uid);
    } catch (e) {
      debugPrint('Delete remote user setting failed: $e');
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
              // DELETE payloads carry an empty newRecord in realtime_client
              // 2.x — detect them by eventType and take the id from oldRecord.
              if (payload.eventType == PostgresChangeEvent.delete) {
                final oldRow = payload.oldRecord;
                if (oldRow.isNotEmpty && table.applyDelete != null) {
                  unawaited(
                    table
                        .applyDelete!(oldRow)
                        .then((_) => _notifyRemoteChanged()),
                  );
                }
              } else {
                final newRow = payload.newRecord;
                if (newRow.isNotEmpty) {
                  unawaited(
                    table.applyRemote(newRow).then((_) => _notifyRemoteChanged()),
                  );
                }
              }
            },
          )
          .subscribe();
      _channels.add(channel);
    }
  }

  /// Debounced UI refresh after realtime bursts (a reorder fans out N rows).
  static void _notifyRemoteChanged() {
    _notifyTimer?.cancel();
    _notifyTimer = Timer(const Duration(milliseconds: 400), () {
      onRemoteChanged?.call();
    });
  }

  static void dispose() {
    _notifyTimer?.cancel();
    _notifyTimer = null;
    onRemoteChanged = null;
    for (final channel in _channels) {
      channel.unsubscribe();
    }
    _channels.clear();
  }
}
