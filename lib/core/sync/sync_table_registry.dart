import 'dart:io';

import 'package:supabase_flutter/supabase_flutter.dart';

import '../database/repositories/countdown_repository.dart';
import '../database/repositories/focus_repository.dart';
import '../database/repositories/habit_repository.dart';
import '../database/repositories/pending_deletes_repository.dart';
import '../database/repositories/settings_repository.dart';
import '../database/repositories/sleep_repository.dart';
import '../database/repositories/todo_repository.dart';
import 'avatar_sync.dart';

/// User-facing settings synced between local SQLite and cloud `user_settings`.
/// Other settings (counters, first_used_date, …) stay device-local.
const profileKeys = [
  'user_name',
  'avatar_path',
  'late_night_mode',
];

const outboxProtectedProfileKeys = {
  'late_night_mode',
};

/// Declarative description of one cloud table: how it is pulled on startup
/// and how its realtime events land in the local database.
///
/// The SyncService loops over this list instead of hand-writing one
/// pull/realtime block per table.
class SyncTable {
  const SyncTable({
    required this.name,
    required this.applyRemote,
    this.applyDelete,
    this.event = PostgresChangeEvent.all,
  });

  final String name;
  final PostgresChangeEvent event;

  /// Applies one remote row to the local database (upsert/insert-if-missing).
  final Future<void> Function(Map<String, dynamic> row) applyRemote;

  /// Applies a remote DELETE (the payload's oldRecord) to the local database.
  final Future<void> Function(Map<String, dynamic> oldRow)? applyDelete;
}

/// All tables participating in cloud sync, in pull order.
final List<SyncTable> syncTables = [
  SyncTable(
    name: 'todos',
    applyRemote: TodoRepository.upsertFromRemote,
    applyDelete: (oldRow) async {
      final id = oldRow['id'] as String;
      await TodoRepository.deleteById(id);
      // The realtime DELETE confirms our own pending delete (its echo) or
      // another device's delete — either way the tombstone is obsolete.
      await PendingDeletesRepository.clear('todos', id);
    },
  ),
  SyncTable(
    name: 'habits',
    applyRemote: HabitRepository.upsertFromRemote,
    applyDelete: (oldRow) async {
      final id = oldRow['id'] as String;
      await HabitRepository.deleteById(id);
      await PendingDeletesRepository.clear('habits', id);
    },
  ),
  SyncTable(
    name: 'countdowns',
    applyRemote: CountdownRepository.upsertFromRemote,
    applyDelete: (oldRow) async {
      final id = oldRow['id'] as String;
      await CountdownRepository.deleteById(id);
      await PendingDeletesRepository.clear('countdowns', id);
    },
  ),
  SyncTable(
    name: 'sleep_records',
    applyRemote: SleepRepository.upsertFromRemote,
    applyDelete: (oldRow) => SleepRepository.deleteById(oldRow['id'] as String),
  ),
  SyncTable(
    name: 'focus_sessions',
    event: PostgresChangeEvent.insert,
    applyRemote: FocusRepository.insertIfNotExists,
    applyDelete: (oldRow) => FocusRepository.deleteById(oldRow['id'] as String),
  ),
  // user_settings carries many device-local keys; only user-facing settings
  // (profile and late-night mode) participate in sync. The avatar value is a
  // Storage object path — it is downloaded into the local profile cache on
  // apply so the UI can render the file.
  SyncTable(
    name: 'user_settings',
    applyRemote: (row) async {
      final key = row['key'] as String?;
      if (key == null || !profileKeys.contains(key)) return;
      final value = row['value'] as String?;
      final updatedAt = row['updated_at'] as String?;

      if (key == 'user_name') {
        if (value != null && value.isNotEmpty) {
          await SettingsRepository.set(key, value);
        }
        return;
      }

      if (key == 'late_night_mode') {
        if (value == 'true' || value == 'false') {
          await SettingsRepository.applySyncedRemote(
            key: key,
            value: value!,
            updatedAt: updatedAt,
          );
        }
        return;
      }

      // key == 'avatar_path': only a Storage object path is applied. A
      // non-Storage value is a legacy device-local path pushed by an old
      // client — ignore it and keep the local avatar (issue #12). Cloud
      // removal arrives as a realtime DELETE (applyDelete below), and a
      // null value is treated as an explicit removal.
      switch (AvatarSync.applyDecision(value)) {
        case AvatarApplyAction.clear:
          await SettingsRepository.delete('avatar_local_path');
          await SettingsRepository.delete('avatar_path');
        case AvatarApplyAction.ignore:
          break;
        case AvatarApplyAction.apply:
          // applyDecision only returns `apply` for a valid Storage path, so
          // the value is non-null here.
          final path = value!;
          await SettingsRepository.set('avatar_path', path);
          try {
            final local = await AvatarSync.download(
              Supabase.instance.client,
              path,
            );
            await SettingsRepository.set('avatar_local_path', local);
          } catch (_) {
            // Offline or bucket missing — the storage path is kept and the
            // file is fetched on the next successful pull.
          }
      }
    },
    applyDelete: (oldRow) async {
      final key = oldRow['key'] as String?;
      if (key == 'avatar_path') {
        final localPath = await SettingsRepository.get('avatar_local_path');
        await SettingsRepository.delete('avatar_local_path');
        await SettingsRepository.delete('avatar_path');
        if (localPath != null) {
          final file = File(localPath);
          if (await file.exists()) {
            await file.delete().catchError((_) => file);
          }
        }
      } else if (key == 'late_night_mode') {
        if (!await SettingsRepository.isSyncDirty(key!)) {
          await SettingsRepository.deleteSyncedState(key);
        }
      }
    },
  ),
];
