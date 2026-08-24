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

  test('sleep check-in records the row without touching the day bucket',
      () async {
    final before = provider.todayDate;
    await provider.recordSleep(
      SleepRecord.create(recordDate: provider.todayDate, sleepTime: '23:00'),
    );

    expect(provider.todayDate, before);
    expect(provider.sleepRecords, isNotEmpty);
  });

  test('late-night day rolls over at the 04:00 boundary', () async {
    provider.dispose();
    await SettingsRepository.set('late_night_mode', 'true');
    await SettingsRepository.set('last_rollover_date', '2026-08-16');
    provider = AppProvider(now: () => DateTime(2026, 8, 17, 3, 59));
    await provider.init();

    expect(provider.todayDate, '2026-08-16');
    expect(provider.isLateNightCarryoverActive, isTrue);

    // The boundary is a fixed wall-clock rule: once 04:00 passes, the
    // provider's periodic rollover moves the day forward.
    provider.dispose();
    provider = AppProvider(now: () => DateTime(2026, 8, 17, 4, 0));
    await provider.init();

    expect(provider.todayDate, '2026-08-17');
    expect(provider.isLateNightCarryoverActive, isFalse);
  });

  test('toggling mode near the boundary never flaps the rolled-over day',
      () async {
    provider.dispose();
    await SettingsRepository.set('late_night_mode', 'true');
    await SettingsRepository.set('last_rollover_date', '2026-08-16');
    provider = AppProvider(now: () => DateTime(2026, 8, 17, 2));
    await provider.init();
    expect(provider.todayDate, '2026-08-16');

    // Turn the mode off at 02:00 — the day advances to the calendar date.
    await provider.setLateNightModeEnabled(false);
    expect(provider.todayDate, '2026-08-17');

    // Turning it back on must not drag the day backwards: rollover for
    // Aug 17 has already run (deleted/reset data).
    await provider.setLateNightModeEnabled(true);
    expect(provider.todayDate, '2026-08-17');
  });
}
