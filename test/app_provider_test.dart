import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/core/database/app_database.dart';
import 'package:goworkbro/core/database/repositories/focus_repository.dart';
import 'package:goworkbro/core/database/repositories/settings_repository.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/providers/app_provider.dart';

void main() {
  late Directory tempDir;
  late AppProvider provider;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('goworkbro_provider_');
    AppDatabase.setDataDirForTesting(tempDir.path);
    provider = AppProvider();
    await provider.init();
  });

  tearDown(() async {
    provider.dispose();
    await AppDatabase.closeForTesting();
    await tempDir.delete(recursive: true);
  });

  test('recordFocusSession assigns the provider logical day', () async {
    final now = DateTime.now();
    await provider.recordFocusSession(
      FocusSession(
        id: 'session-1',
        sourceType: 'todo',
        sourceTitle: '跨午夜任务',
        startTime: now.subtract(const Duration(minutes: 20)).toIso8601String(),
        endTime: now.toIso8601String(),
        durationSeconds: 1200,
        sessionDate: '1999-01-01',
      ),
    );

    expect(provider.todaySessions.single.sessionDate, provider.todayDate);
    expect(
      (await FocusRepository.getByDate(provider.todayDate)).single.id,
      'session-1',
    );
    expect(await FocusRepository.getByDate('1999-01-01'), isEmpty);
  });

  test(
    'sync inventory retains focus sessions outside the visible day',
    () async {
      final now = DateTime.now();
      await FocusRepository.insert(
        FocusSession(
          id: 'offline-previous-day',
          sourceType: 'todo',
          sourceTitle: '跨日离线专注',
          startTime: now
              .subtract(const Duration(minutes: 25))
              .toIso8601String(),
          endTime: now.toIso8601String(),
          durationSeconds: 1500,
          sessionDate: '2020-01-01',
        ),
      );

      await provider.refreshAll();

      expect(provider.todaySessions, isEmpty);
      expect(
        provider.syncSessionInventory.map((session) => session.id),
        contains('offline-previous-day'),
      );
    },
  );

  test('late-night mode persists as an app data setting', () async {
    expect(provider.lateNightModeEnabled, isFalse);

    await provider.setLateNightModeEnabled(true);

    expect(provider.lateNightModeEnabled, isTrue);
    expect(await SettingsRepository.get('late_night_mode'), 'true');
    expect(await SettingsRepository.isSyncDirty('late_night_mode'), isTrue);
  });

  test('refreshAll applies remotely-synced late-night settings', () async {
    expect(provider.lateNightModeEnabled, isFalse);
    await SettingsRepository.set('late_night_mode', 'true');

    await provider.refreshAll();

    expect(provider.lateNightModeEnabled, isTrue);
  });

  test(
    'sleep check-in persists the supplied logical-day closing boundary',
    () async {
      await provider.recordSleep(
        SleepRecord.create(recordDate: provider.todayDate, sleepTime: '23:00'),
        closesLogicalDayThrough: '2026-08-17',
      );

      expect(
        await SettingsRepository.get('late_night_closed_through'),
        '2026-08-17',
      );
    },
  );

  test('sleep check-in never moves the closing boundary backward', () async {
    await SettingsRepository.set('late_night_closed_through', '2026-08-18');
    await provider.refreshAll();

    await provider.recordSleep(
      SleepRecord.create(recordDate: '2026-08-17', sleepTime: '01:00'),
      closesLogicalDayThrough: '2026-08-17',
    );

    expect(
      await SettingsRepository.get('late_night_closed_through'),
      '2026-08-18',
    );
  });

  test(
    'remote closing marker triggers rollover before control returns',
    () async {
      provider.dispose();
      await SettingsRepository.set('late_night_mode', 'true');
      await SettingsRepository.set('late_night_closed_through', '');
      await SettingsRepository.set('last_rollover_date', '2026-08-16');
      provider = AppProvider(now: () => DateTime(2026, 8, 17, 2));
      await provider.init();
      expect(provider.todayDate, '2026-08-16');

      await SettingsRepository.set('late_night_closed_through', '2026-08-17');
      await provider.refreshAfterRemoteChangeForTesting();

      expect(provider.todayDate, '2026-08-17');
      expect(await SettingsRepository.get('last_rollover_date'), '2026-08-17');
    },
  );
}
