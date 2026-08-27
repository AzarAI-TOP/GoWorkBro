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
///    countdowns / sleep records), so a periodic pull never rolls back newer
///    local edits.
/// 4. Realtime subscriptions apply inserts/updates/deletes to the local cache
///    and notify the UI. Realtime is best-effort: mobile backgrounded apps and
///    missed events are covered by the 60s polling fallback.
///
/// Tables participating in sync are declared declaratively in
/// [syncTables] (sync_table_registry.dart) — pull and realtime here are a
/// single loop over that registry instead of per-table code.
class SyncService {
  static SupabaseClient? _client;
  static Future<void>? _pulling;
  static Future<void>? _pushingSettings;
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
    if (_client == null) return;
    final active = _pulling;
    if (active != null) {
      await active;
      return;
    }

    final operation = _pullAllInternal();
    _pulling = operation;
    try {
      await operation;
    } finally {
      if (identical(_pulling, operation)) _pulling = null;
    }
  }

  static Future<void> _pullAllInternal() async {
    for (final table in syncTables) {
      try {
        await _pullTable(table.name, table.applyRemote);
      } catch (e) {
        debugPrint('Pull ${table.name} failed: $e');
      }
    }
  }

  static Future<void> _pullTable(
    String table,
    Future<void> Function(Map<String, dynamic> row) applyRemote,
  ) async {
    final uid = currentUserId;
    if (uid == null) return;
    final response = await _client!.from(table).select().eq('user_id', uid);
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
        'target_datetime': countdown.targetDateTime.toUtc().toIso8601String(),
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
        'workout_duration_minutes': record.workoutDurationMinutes,
        'note': record.note,
        'updated_at': record.updatedAt ?? nowStamp(),
      }, onConflict: 'user_id,record_date');
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

    Future<Map<String, String?>?> remoteStamps(
      String table, {
      String key = 'id',
    }) async {
      try {
        final rows = await _client!
            .from(table)
            .select('$key, updated_at')
            .eq('user_id', uid);
        return {
          for (final r in rows) r[key] as String: r['updated_at'] as String?,
        };
      } catch (e) {
        debugPrint('Fetch $table stamps failed: $e');
        return null;
      }
    }

    Future<Set<String>?> remoteIds(String table) async {
      try {
        final rows = await _client!.from(table).select('id').eq('user_id', uid);
        return {for (final row in rows) row['id'] as String};
      } catch (e) {
        debugPrint('Fetch $table ids failed: $e');
        return null;
      }
    }

    final todoStamps = await remoteStamps('todos');
    if (todoStamps != null) {
      for (final t in todos) {
        if (isLocalNewer(t.updatedAt, todoStamps[t.id])) {
          unawaited(pushTodo(t));
        }
      }
    }
    final habitStamps = await remoteStamps('habits');
    if (habitStamps != null) {
      for (final h in habits) {
        if (isLocalNewer(h.updatedAt, habitStamps[h.id])) {
          unawaited(pushHabit(h));
        }
      }
    }
    final countdownStamps = await remoteStamps('countdowns');
    if (countdownStamps != null) {
      for (final c in countdowns) {
        if (isLocalNewer(c.updatedAt, countdownStamps[c.id])) {
          unawaited(pushCountdown(c));
        }
      }
    }
    // Focus sessions are append-only. Retry the complete local inventory, but
    // only upload ids that are absent remotely so historical rows do not get
    // re-sent on every periodic sync.
    final remoteFocusIds = await remoteIds('focus_sessions');
    if (remoteFocusIds != null) {
      for (final session in sessions) {
        if (!remoteFocusIds.contains(session.id)) {
          unawaited(pushFocusSession(session));
        }
      }
    }
    final sleepStamps = await remoteStamps('sleep_records', key: 'record_date');
    if (sleepStamps != null) {
      for (final r in sleepRecords) {
        if (!sleepStamps.containsKey(r.recordDate) ||
            isLocalNewer(r.updatedAt, sleepStamps[r.recordDate])) {
          unawaited(pushSleepRecord(r));
        }
      }
    }
  }

  /// Push synced user settings (profile and late-night mode) to the cloud.
  /// A legacy device-local avatar path is transparently migrated to Storage
  /// before pushing — only Storage object paths ever leave the device.
  static Future<void> pushUserSettings({bool onlyDirty = false}) async {
    while (true) {
      final active = _pushingSettings;
      if (active != null) {
        await active;
        continue;
      }

      final operation = _pushUserSettingsInternal(onlyDirty: onlyDirty);
      _pushingSettings = operation;
      try {
        await operation;
      } finally {
        if (identical(_pushingSettings, operation)) _pushingSettings = null;
      }
      return;
    }
  }

  static Future<void> _pushUserSettingsInternal({
    required bool onlyDirty,
  }) async {
    if (_client == null) return;
    final uid = currentUserId;
    if (uid == null) return;
    try {
      final dirtyStates = <String, bool>{};
      for (final key in profileKeys) {
        dirtyStates[key] = await SettingsRepository.isSyncDirty(key);
      }

      final remoteUpdatedAt = <String, String?>{};
      final remoteValues = <String, String?>{};
      var remoteInventoryAvailable = true;
      final needsConflictInventory = outboxProtectedProfileKeys.any(
        (key) => dirtyStates[key] ?? false,
      );
      if (needsConflictInventory) {
        try {
          final remoteRows = await _client!
              .from('user_settings')
              .select('key, value, updated_at')
              .eq('user_id', uid);
          for (final row in remoteRows) {
            final key = row['key'] as String?;
            if (key != null) {
              remoteValues[key] = row['value'] as String?;
              remoteUpdatedAt[key] = row['updated_at'] as String?;
            }
          }
        } catch (e) {
          remoteInventoryAvailable = false;
          debugPrint('Read remote user setting timestamps failed: $e');
        }
      }

      final rows = <Map<String, Object?>>[];
      final completedSnapshots = <String, String>{};
      final sentValues = <String, String>{};
      final fallbackStamp = nowStamp();
      for (final key in profileKeys) {
        final isDirty = dirtyStates[key] ?? false;
        final localUpdatedAt = await SettingsRepository.getSyncUpdatedAt(key);
        final value = await SettingsRepository.get(key);
        if (value == null) continue;
        if (!shouldPushUserSetting(
          key: key,
          isDirty: isDirty,
          onlyDirty: onlyDirty,
          localValue: value,
          remoteValue: remoteValues[key],
          localUpdatedAt: localUpdatedAt,
          remoteUpdatedAt: remoteUpdatedAt[key],
          remoteInventoryAvailable: remoteInventoryAvailable,
        )) {
          continue;
        }
        final rowUpdatedAt = localUpdatedAt ?? fallbackStamp;
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
        rows.add({
          'key': key,
          'user_id': uid,
          'value': cloudValue,
          'updated_at': rowUpdatedAt,
        });
        if (localUpdatedAt != null) {
          completedSnapshots[key] = localUpdatedAt;
          sentValues[key] = cloudValue;
        }
      }
      if (rows.isNotEmpty) {
        // One PostgREST request applies mode + closing boundary together;
        // realtime may emit multiple rows, but its debounce then refreshes the
        // complete committed pair before rollover.
        final returnedRows = await _client!
            .from('user_settings')
            .upsert(rows)
            .select('key, value, updated_at');
        final returnedByKey = <String, Map<String, dynamic>>{};
        for (final row in returnedRows) {
          final key = row['key'] as String?;
          if (key != null) returnedByKey[key] = row;
        }
        for (final entry in completedSnapshots.entries) {
          final returned = returnedByKey[entry.key];
          if (returned == null ||
              !didServerAcceptUserSetting(
                sentValue: sentValues[entry.key]!,
                sentUpdatedAt: entry.value,
                returnedValue: returned['value'] as String?,
                returnedUpdatedAt: returned['updated_at'] as String?,
              )) {
            continue;
          }
          await SettingsRepository.markSyncComplete(
            entry.key,
            expectedUpdatedAt: entry.value,
          );
        }
      }
    } catch (e) {
      debugPrint('Push user settings failed: $e');
    }
  }

  /// Decides whether a local user setting should be uploaded. Logical-day
  /// settings additionally require a durable dirty marker and, when one is
  /// present, a client-side LWW comparison against the remote row. The
  /// closing boundary stays monotonic: a greater ISO date wins even when the
  /// local timestamp is older.
  static bool shouldPushUserSetting({
    required String key,
    required bool isDirty,
    required bool onlyDirty,
    String? localValue,
    String? remoteValue,
    String? localUpdatedAt,
    String? remoteUpdatedAt,
    bool remoteInventoryAvailable = true,
  }) {
    final requiresOutbox = outboxProtectedProfileKeys.contains(key);
    if (!isDirty && (onlyDirty || requiresOutbox)) return false;
    if (!requiresOutbox) return !onlyDirty || isDirty;
    if (!remoteInventoryAvailable || localUpdatedAt == null) return false;
    if (remoteUpdatedAt == null) return true;
    return isLocalNewer(localUpdatedAt, remoteUpdatedAt);
  }

  /// True when the server actually persisted the value and timestamp that
  /// were sent, so the local dirty marker can be cleared safely.
  static bool didServerAcceptUserSetting({
    required String sentValue,
    required String sentUpdatedAt,
    required String? returnedValue,
    required String? returnedUpdatedAt,
  }) {
    if (returnedValue != sentValue || returnedUpdatedAt == null) return false;
    if (returnedUpdatedAt == sentUpdatedAt) return true;
    final sent = DateTime.tryParse(sentUpdatedAt);
    final returned = DateTime.tryParse(returnedUpdatedAt);
    return sent != null &&
        returned != null &&
        sent.toUtc().isAtSameMomentAs(returned.toUtc());
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
    try {
      await _client!.from('todos').delete().eq('id', id);
    } catch (e) {
      debugPrint('Delete remote todo failed: $e');
    }
  }

  static Future<void> deleteRemoteHabit(String id) async {
    if (_client == null) return;
    try {
      await _client!.from('habits').delete().eq('id', id);
    } catch (e) {
      debugPrint('Delete remote habit failed: $e');
    }
  }

  static Future<void> deleteRemoteCountdown(String id) async {
    if (_client == null) return;
    try {
      await _client!.from('countdowns').delete().eq('id', id);
    } catch (e) {
      debugPrint('Delete remote countdown failed: $e');
    }
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
                    table.applyDelete!(oldRow).then(
                      (_) => _notifyRemoteChanged(),
                    ),
                  );
                }
              } else {
                final newRow = payload.newRecord;
                if (newRow.isNotEmpty) {
                  unawaited(
                    table
                        .applyRemote(newRow)
                        .then((_) => _notifyRemoteChanged()),
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
