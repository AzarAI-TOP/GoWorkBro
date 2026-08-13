import 'package:supabase_flutter/supabase_flutter.dart';

import '../database/repositories/countdown_repository.dart';
import '../database/repositories/focus_repository.dart';
import '../database/repositories/habit_repository.dart';
import '../database/repositories/settings_repository.dart';
import '../database/repositories/sleep_repository.dart';
import '../database/repositories/todo_repository.dart';

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
    this.event = PostgresChangeEvent.all,
  });

  final String name;
  final PostgresChangeEvent event;

  /// Applies one remote row to the local database (upsert/insert-if-missing).
  final Future<void> Function(Map<String, dynamic> row) applyRemote;
}

/// All tables participating in cloud sync, in pull order.
final List<SyncTable> syncTables = [
  SyncTable(
    name: 'todos',
    applyRemote: TodoRepository.upsertFromRemote,
  ),
  SyncTable(
    name: 'habits',
    applyRemote: HabitRepository.upsertFromRemote,
  ),
  SyncTable(
    name: 'countdowns',
    applyRemote: CountdownRepository.upsertFromRemote,
  ),
  SyncTable(
    name: 'sleep_records',
    applyRemote: SleepRepository.upsertFromRemote,
  ),
  SyncTable(
    name: 'focus_sessions',
    event: PostgresChangeEvent.insert,
    applyRemote: FocusRepository.insertIfNotExists,
  ),
  // user_settings carries many device-local keys; only the profile keys
  // (user_name / avatar_path) participate in sync.
  SyncTable(
    name: 'user_settings',
    applyRemote: (row) async {
      final key = row['key'] as String?;
      if (key == null || !profileKeys.contains(key)) return;
      final value = row['value'] as String?;
      if (value != null) {
        await SettingsRepository.set(key, value);
      }
    },
  ),
];
