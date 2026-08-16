import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/core/database/app_database.dart';
import 'package:goworkbro/core/database/repositories/settings_repository.dart';
import 'package:goworkbro/core/database/repositories/sleep_repository.dart';
import 'package:goworkbro/core/database/repositories/todo_repository.dart';
import 'package:goworkbro/core/export/data_export_service.dart';
import 'package:goworkbro/models/models.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  late Directory tempDir;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('goworkbro_export_');
    AppDatabase.setDataDirForTesting(tempDir.path);
    SharedPreferences.setMockInitialValues({
      'custom_habit_units': <String>['组', '公里'],
      'supabase.auth.token': '[REDACTED]',
    });
  });

  tearDown(() async {
    await AppDatabase.closeForTesting();
    await tempDir.delete(recursive: true);
  });

  test('exports a versioned JSON snapshot of every local data table', () async {
    final avatar = File(
      '${tempDir.path}${Platform.pathSeparator}avatar.png',
    );
    await avatar.writeAsBytes([0x89, 0x50, 0x4E, 0x47]);
    await TodoRepository.insert(
      Todo.create(title: '导出测试', timingType: TimingType.none),
    );
    await SleepRepository.upsert(
      SleepRecord.create(
        recordDate: '2026-08-16',
        workoutDurationMinutes: 30,
        note: '爬楼梯',
      ),
    );
    await SettingsRepository.set('late_night_mode', 'true');
    await SettingsRepository.set('avatar_local_path', avatar.path);

    final json = await DataExportService.createJson(
      exportedAt: DateTime.utc(2026, 8, 16, 12, 34, 56),
    );
    final document = jsonDecode(json) as Map<String, dynamic>;
    final tables = document['tables'] as Map<String, dynamic>;
    final preferences = document['preferences'] as Map<String, dynamic>;
    final assets = document['assets'] as Map<String, dynamic>;
    final todos = (tables['todos'] as List<dynamic>)
        .cast<Map<String, dynamic>>();
    final sleepRecords = (tables['sleep_records'] as List<dynamic>)
        .cast<Map<String, dynamic>>();
    final settings = (tables['user_settings'] as List<dynamic>)
        .cast<Map<String, dynamic>>();

    expect(document['format'], 'goworkbro-data-export');
    expect(document['format_version'], 1);
    expect(document['database_schema_version'], AppDatabase.schemaVersion);
    expect(document['exported_at'], '2026-08-16T12:34:56.000Z');
    expect(preferences['custom_habit_units'], ['组', '公里']);
    expect(preferences.keys, {'custom_habit_units'});
    expect(json, isNot(contains('supabase.auth.token')));
    expect(assets['avatar'], {
      'file_name': 'avatar.png',
      'encoding': 'base64',
      'content': base64Encode([0x89, 0x50, 0x4E, 0x47]),
    });
    expect(tables.keys, {
      'todos',
      'habits',
      'focus_sessions',
      'countdowns',
      'sleep_records',
      'user_settings',
      'ustc_news_cache',
    });
    expect(todos.single['title'], '导出测试');
    expect(sleepRecords.single['workout_duration_minutes'], 30);
    expect(sleepRecords.single['note'], '爬楼梯');
    expect(
      settings.any(
        (row) => row['key'] == 'late_night_mode' && row['value'] == 'true',
      ),
      isTrue,
    );
  });

  test('delivers the portable document to a user-selected saver', () async {
    String? savedName;
    String? savedContent;
    final exportedAt = DateTime(2026, 8, 16, 12, 34, 56);

    final saved = await DataExportService.exportWith(
      now: exportedAt,
      saver: ({required suggestedName, required content}) async {
        savedName = suggestedName;
        savedContent = content;
        return true;
      },
    );

    expect(saved, isTrue);
    expect(savedName, 'GoWorkBro-20260816-123456.json');
    final savedDocument = jsonDecode(savedContent!) as Map<String, dynamic>;
    expect(savedDocument['format'], 'goworkbro-data-export');
  });
}
