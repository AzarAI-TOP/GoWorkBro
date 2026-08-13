import 'dart:io';

import 'package:supabase_flutter/supabase_flutter.dart';

import '../database/repositories/countdown_repository.dart';
import '../database/repositories/focus_repository.dart';
import '../database/repositories/habit_repository.dart';
import '../database/repositories/settings_repository.dart';
import '../database/repositories/sleep_repository.dart';
import '../database/repositories/todo_repository.dart';
import 'avatar_sync.dart';

/// Profile keys synced between local SQLite and cloud `user_settings`.
/// Other settings (counters, first_used_date, …) stay device-local.
const profileKeys = ['user_name', 'avatar_path'];

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
    applyDelete: (oldRow) => TodoRepository.deleteById(oldRow['id'] as String),
  ),
  SyncTable(
    name: 'habits',
    applyRemote: HabitRepository.upsertFromRemote,
    applyDelete: (oldRow) => HabitRepository.deleteById(oldRow['id'] as String),
  ),
  SyncTable(
    name: 'countdowns',
    applyRemote: CountdownRepository.upsertFromRemote,
    applyDelete: (oldRow) =>
        CountdownRepository.deleteById(oldRow['id'] as String),
  ),
  SyncTable(
    name: 'sleep_records',
    applyRemote: SleepRepository.upsertFromRemote,
    applyDelete: (oldRow) =>
        SleepRepository.deleteById(oldRow['id'] as String),
  ),
  SyncTable(
    name: 'focus_sessions',
    event: PostgresChangeEvent.insert,
    applyRemote: FocusRepository.insertIfNotExists,
    applyDelete: (oldRow) =>
        FocusRepository.deleteById(oldRow['id'] as String),
  ),
  // user_settings carries many device-local keys; only the profile keys
  // (user_name / avatar_path) participate in sync. The avatar value is a
  // Storage object path — it is downloaded into the local profile cache on
  // apply so the UI can render the file.
  SyncTable(
    name: 'user_settings',
    applyRemote: (row) async {
      final key = row['key'] as String?;
      if (key == null || !profileKeys.contains(key)) return;
      final value = row['value'] as String?;

      if (key == 'user_name') {
        if (value != null && value.isNotEmpty) {
          await SettingsRepository.set(key, value);
        }
        return;
      }

      // key == 'avatar_path': the synced value must be a Storage object path.
      if (value == null || !AvatarSync.isStoragePath(value)) {
        // Cloud avatar removed (or a legacy local-path value pushed by an
        // older version) — clear the local cache; a foreign device path
        // cannot be rendered anyway.
        await SettingsRepository.delete('avatar_local_path');
        await SettingsRepository.delete('avatar_path');
        return;
      }
      await SettingsRepository.set('avatar_path', value);
      try {
        final local = await AvatarSync.download(
          Supabase.instance.client,
          value,
        );
        await SettingsRepository.set('avatar_local_path', local);
      } catch (_) {
        // Offline or bucket missing — the storage path is kept and the file
        // is fetched on the next successful pull.
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
      }
    },
  ),
];
