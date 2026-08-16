import 'dart:convert';
import 'dart:io';

import 'package:goworkbro/core/database/app_database.dart';
import 'package:path/path.dart' as p;
import 'package:shared_preferences/shared_preferences.dart';

typedef ExportSaver =
    Future<bool> Function({
      required String suggestedName,
      required String content,
    });

/// Creates a portable, versioned snapshot of every local SQLite data table.
abstract final class DataExportService {
  static const int formatVersion = 1;

  static const Map<String, String> _tables = {
    'todos': 'sort_order ASC, id ASC',
    'habits': 'sort_order ASC, id ASC',
    'focus_sessions': 'start_time ASC, id ASC',
    'countdowns': 'target_datetime ASC, id ASC',
    'sleep_records': 'record_date ASC, id ASC',
    'user_settings': 'key ASC',
    'ustc_news_cache': 'date ASC',
  };

  static Future<String> createJson({DateTime? exportedAt}) async {
    final db = await AppDatabase.database;
    final tables = await db.transaction((txn) async {
      final snapshot = <String, List<Map<String, Object?>>>{};
      for (final entry in _tables.entries) {
        snapshot[entry.key] = await txn.query(entry.key, orderBy: entry.value);
      }
      return snapshot;
    });
    final preferencesStore = await SharedPreferences.getInstance();
    final preferences = <String, Object?>{
      // Export app-owned user data only. Plugin-owned preferences may include
      // authentication sessions and must never enter a portable backup.
      'custom_habit_units':
          preferencesStore.getStringList('custom_habit_units') ?? const [],
    };

    final assets = await _collectAssets(tables);
    final document = <String, Object?>{
      'format': 'goworkbro-data-export',
      'format_version': formatVersion,
      'database_schema_version': AppDatabase.schemaVersion,
      'exported_at': (exportedAt ?? DateTime.now()).toUtc().toIso8601String(),
      'tables': tables,
      'preferences': preferences,
      'assets': assets,
    };
    return const JsonEncoder.withIndent('  ').convert(document);
  }

  static Future<Map<String, Object?>> _collectAssets(
    Map<String, List<Map<String, Object?>>> tables,
  ) async {
    final settings = tables['user_settings'] ?? const [];

    String? valueFor(String key) {
      for (final row in settings) {
        if (row['key'] == key) return row['value'] as String?;
      }
      return null;
    }

    // avatar_local_path is the app-owned cache copied into Application
    // Support. avatar_path is accepted only for legacy absolute local paths;
    // modern values there are Supabase Storage object keys, not files.
    final localPath = valueFor('avatar_local_path');
    final legacyPath = valueFor('avatar_path');
    final candidate = localPath ??
        (legacyPath != null && p.isAbsolute(legacyPath) ? legacyPath : null);
    if (candidate == null) return const {};

    final avatar = File(candidate);
    if (!await avatar.exists()) return const {};
    return {
      'avatar': {
        'file_name': p.basename(candidate),
        'encoding': 'base64',
        'content': base64Encode(await avatar.readAsBytes()),
      },
    };
  }

  static String suggestedFileName(DateTime now) {
    String two(int value) => value.toString().padLeft(2, '0');
    return 'GoWorkBro-${now.year}${two(now.month)}${two(now.day)}-'
        '${two(now.hour)}${two(now.minute)}${two(now.second)}.json';
  }

  static Future<bool> exportWith({
    required ExportSaver saver,
    DateTime? now,
  }) async {
    final timestamp = now ?? DateTime.now();
    final content = await createJson(exportedAt: timestamp);
    return saver(suggestedName: suggestedFileName(timestamp), content: content);
  }
}
