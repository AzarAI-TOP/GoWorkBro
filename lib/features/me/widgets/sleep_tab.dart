import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:goworkbro/core/domain/check_in_type.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/core/utils/date_utils.dart';
import 'package:goworkbro/features/me/widgets/sleep_charts_section.dart';
import 'package:goworkbro/features/me/widgets/workout_checkin_sheet.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/providers/app_provider.dart';

/// Check-in tab: sleep/wake/workout buttons, sleep trends and history.
class SleepTab extends StatefulWidget {
  const SleepTab({super.key});

  @override
  State<SleepTab> createState() => _SleepTabState();
}

class _SleepTabState extends State<SleepTab> {
  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    context.watch<AppLocaleProvider>();
    final theme = Theme.of(context);
    final s = S.of(context.read<AppLocaleProvider>().locale);
    SleepRecord? recordFor(String date) => provider.sleepRecords
        .where((record) => record.recordDate == date)
        .firstOrNull;

    final wakeRecord = recordFor(provider.calendarDate);
    final workoutRecord = recordFor(provider.todayDate);
    final sleepRecord = recordFor(sleepRecordDateKey(DateTime.now()));
    final calendarSleepRecord = recordFor(provider.calendarDate);
    final wakeTime = wakeRecord?.wakeTime;
    final workoutValue = workoutRecord == null
        ? null
        : workoutRecord.workoutDurationMinutes != null
        ? s.minutes(workoutRecord.workoutDurationMinutes!)
        : workoutRecord.workoutTime != null
        ? s.legacyWorkoutRecord
        : null;
    final sleepTime = sleepRecord?.sleepTime ?? calendarSleepRecord?.sleepTime;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(s.todayCheckIn, style: theme.textTheme.titleLarge),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: _buildCheckInButton(
                        context,
                        theme,
                        label: s.wakeUp,
                        icon: Icons.wb_sunny_outlined,
                        value: wakeTime,
                        onTap: () =>
                          _recordTime(context, provider, CheckInType.wake),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildCheckInButton(
                        context,
                        theme,
                        label: s.workout,
                        icon: Icons.fitness_center_outlined,
                        value: workoutValue,
                        onTap: () => _recordWorkout(context, provider),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildCheckInButton(
                        context,
                        theme,
                        label: s.sleep,
                        icon: Icons.bedtime_outlined,
                        value: sleepTime,
                        onTap: () =>
                            _recordTime(context, provider, CheckInType.sleep),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        if (provider.sleepRecords.isNotEmpty) ...[
          Text(s.sleepTrends, style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
          SleepChartsSection(records: provider.sleepRecords, strings: s),
          const SizedBox(height: 16),
        ],
        Text(s.checkInHistory, style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        if (provider.sleepRecords.isEmpty)
          Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Text(
                s.noCheckInRecords,
                style: theme.textTheme.bodyMedium,
              ),
            ),
          )
        else
          ...provider.sleepRecords
              .take(10)
              .map((r) => _buildSleepRecordCard(context, provider, r, theme)),
      ],
    );
  }

  Widget _buildCheckInButton(
    BuildContext context,
    ThemeData theme, {
    required String label,
    required IconData icon,
    required String? value,
    required VoidCallback onTap,
  }) {
    final hasRecord = value != null;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 8),
          decoration: BoxDecoration(
            color: hasRecord
                ? theme.colorScheme.primary.withValues(alpha: 0.08)
                : theme.colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: hasRecord
                  ? theme.colorScheme.primary.withValues(alpha: 0.3)
                  : Colors.transparent,
            ),
          ),
          child: Column(
            children: [
              Icon(
                icon,
                size: 28,
                color: hasRecord
                    ? theme.colorScheme.primary
                    : theme.colorScheme.onSurfaceVariant,
              ),
              const SizedBox(height: 8),
              Text(label, style: theme.textTheme.bodyMedium),
              const SizedBox(height: 4),
              Text(
                hasRecord
                    ? value
                    : S
                          .of(context.read<AppLocaleProvider>().locale)
                          .notCheckedIn,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: hasRecord ? FontWeight.w600 : FontWeight.normal,
                  color: hasRecord
                      ? theme.colorScheme.primary
                      : theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSleepRecordCard(
    BuildContext context,
    AppProvider provider,
    SleepRecord record,
    ThemeData theme,
  ) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final workoutSummary = record.workoutDurationMinutes != null
        ? s.workoutRecordSummary(record.workoutDurationMinutes!, record.note)
        : record.workoutTime != null
        ? s.legacyWorkoutAt(record.workoutTime!)
        : '—';
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        onTap: () => _editHistoryRecord(provider, record),
        leading: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: theme.colorScheme.primary.withValues(alpha: 0.1),
          ),
          child: const Icon(Icons.calendar_today, size: 20),
        ),
        title: Text(
          record.recordDate,
          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
        ),
        subtitle: Text(
          s.sleepRecordSummary(
            _formatTime(record.wakeTime),
            workoutSummary,
            _formatTime(record.sleepTime),
          ),
          style: const TextStyle(fontSize: 12),
        ),
      ),
    );
  }

  /// Backfill or correct a historical check-in row for stats.
  Future<void> _editHistoryRecord(
    AppProvider provider,
    SleepRecord record,
  ) async {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final choice = await showModalBottomSheet<CheckInType>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  record.recordDate,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.bedtime_outlined),
              title: Text(s.sleep),
              onTap: () => Navigator.pop(sheetContext, CheckInType.sleep),
            ),
            ListTile(
              leading: const Icon(Icons.wb_sunny_outlined),
              title: Text(s.wakeUp),
              onTap: () => Navigator.pop(sheetContext, CheckInType.wake),
            ),
          ],
        ),
      ),
    );
    if (choice == null || !mounted) return;
    await _recordTime(
      context,
      provider,
      choice,
      anchorDate: record.recordDate,
    );
  }

  Future<void> _recordWorkout(
    BuildContext context,
    AppProvider provider,
  ) async {
    final initialRecordDate = provider.todayDate;
    final initialRecord = provider.sleepRecords
        .where((record) => record.recordDate == initialRecordDate)
        .firstOrNull;
    final result = await showModalBottomSheet<WorkoutCheckInResult>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => WorkoutCheckInSheet(
        initialDurationMinutes: initialRecord?.workoutDurationMinutes,
        initialDescription: initialRecord?.note,
        onSave: (value) => Navigator.pop(sheetContext, value),
      ),
    );
    if (result == null || !mounted) return;

    // Resolve the logical date at save time, then patch only workout-owned
    // columns so a rollover or remote sleep/wake update while the sheet was
    // open cannot be overwritten by the stale opening snapshot.
    await provider.recordWorkout(
      recordDate: provider.todayDate,
      durationMinutes: result.durationMinutes,
      description: result.description,
    );
  }

  Future<void> _recordTime(
    BuildContext context,
    AppProvider provider,
    CheckInType type, {
    String? anchorDate,
  }) async {
    final pickerOpenedAt = DateTime.now();
    final helpText = S
        .of(context.read<AppLocaleProvider>().locale)
        .selectCheckInTime(type);
    final picked = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(pickerOpenedAt),
      helpText: helpText,
    );
    if (picked == null) return;

    final timeStr =
        '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
    final selectedAt = resolveCheckInDateTime(
      now: DateTime.now(),
      hour: picked.hour,
      minute: picked.minute,
    );
    final recordDate = anchorDate ??
        switch (type) {
          CheckInType.sleep => sleepRecordDateKey(selectedAt),
          CheckInType.wake => wakeRecordDateKey(selectedAt),
          CheckInType.workout => provider.todayDate,
        };
    final existing = provider.sleepRecords
        .where((r) => r.recordDate == recordDate)
        .toList();

    SleepRecord record;
    if (existing.isNotEmpty) {
      switch (type) {
        case CheckInType.wake:
          record = existing.first.copyWith(wakeTime: timeStr);
        case CheckInType.sleep:
          record = existing.first.copyWith(sleepTime: timeStr);
        case CheckInType.workout:
          record = existing.first;
      }
    } else {
      record = SleepRecord.create(
        recordDate: recordDate,
        wakeTime: type == CheckInType.wake ? timeStr : null,
        sleepTime: type == CheckInType.sleep ? timeStr : null,
      );
    }
    await provider.recordSleep(record);
  }

  String _formatTime(String? time) {
    return time ?? '—';
  }
}
