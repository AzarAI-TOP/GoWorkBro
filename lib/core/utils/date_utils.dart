/// Date helpers shared across the app. All dates are local-time strings in
/// `yyyy-MM-dd` form unless stated otherwise.
library;

String _two(int n) => n.toString().padLeft(2, '0');

/// Today's date as `yyyy-MM-dd` (local time).
String dateKeyOf(DateTime date) =>
    '${date.year}-${_two(date.month)}-${_two(date.day)}';

/// Today's date as `yyyy-MM-dd` (local time).
String get todayDateKey => dateKeyOf(DateTime.now());

const int lateNightCutoffHour = 12;

/// Returns the date bucket used by daily app data.
///
/// In late-night mode, activity after midnight stays on the previous day
/// until sleep is checked in. The carry-over stops at [carryoverCutoffHour]
/// as a safety net when a sleep check-in is missed. A day that has already
/// rolled over is never moved backwards because rollover deletes/resets data.
String logicalDateKey({
  required DateTime now,
  required bool lateNightModeEnabled,
  required bool sleepCheckedInForCalendarDate,
  required String lastRolloverDate,
  int carryoverCutoffHour = lateNightCutoffHour,
}) {
  final calendarDate = dateKeyOf(now);
  final canCarryOver =
      lateNightModeEnabled &&
      now.hour < carryoverCutoffHour &&
      !sleepCheckedInForCalendarDate &&
      lastRolloverDate != calendarDate;
  return canCarryOver
      ? dateKeyOf(now.subtract(const Duration(days: 1)))
      : calendarDate;
}

/// Date row used for a sleep check-in and its ensuing wake-up.
///
/// A check-in before noon belongs to the current calendar row; one from noon
/// onward belongs to the following row. This keeps sleep and wake times paired
/// even when activity before an after-midnight sleep belongs to the prior
/// logical day.
String sleepRecordDateKey(
  DateTime now, {
  int cutoffHour = lateNightCutoffHour,
}) => dateKeyOf(now.hour < cutoffHour ? now : now.add(const Duration(days: 1)));

/// Row used by a wake-up check-in. Unlike daily activity, wake-up belongs to
/// its wall-clock date even while late-night activity is still carried over.
String wakeRecordDateKey(DateTime now) => dateKeyOf(now);

/// Resolves a time-only check-in to its most recent local occurrence.
///
/// Time pickers do not carry a date. A selected clock time later than [now]
/// therefore refers to yesterday; otherwise it refers to today. This lets a
/// user backfill last night's sleep without accidentally pre-closing the next
/// logical day.
DateTime resolveCheckInDateTime({
  required DateTime now,
  required int hour,
  required int minute,
}) {
  var resolved = DateTime(now.year, now.month, now.day, hour, minute);
  if (resolved.isAfter(now)) {
    resolved = resolved.subtract(const Duration(days: 1));
  }
  return resolved;
}

/// Parses `HH:mm` into decimal hours (e.g. "23:29" -> 23.4833…).
double? hoursFromTime(String? value) {
  if (value == null) return null;
  final parts = value.split(':');
  if (parts.length < 2) return null;
  final h = int.tryParse(parts[0]);
  final m = int.tryParse(parts[1]);
  if (h == null || m == null) return null;
  return h + m / 60;
}

/// Converts decimal hours to `HH:mm` (values >= 24 wrap into the next day,
/// e.g. bedtime "01:30" stored as 25.5 renders as "01:30").
String formatHours(double value) {
  final normalized = value >= 24 ? value - 24 : value;
  final totalMinutes = (normalized * 60).round();
  final hours = totalMinutes ~/ 60;
  final minutes = totalMinutes % 60;
  return '${_two(hours)}:${_two(minutes)}';
}

/// Formats a duration in hours (e.g. 7.5) as "7h 30m".
String formatDurationHours(double value) {
  final totalMinutes = (value * 60).round();
  final hours = totalMinutes ~/ 60;
  final minutes = totalMinutes % 60;
  if (hours == 0) return '${minutes}m';
  if (minutes == 0) return '${hours}h';
  return '${hours}h ${minutes}m';
}

/// Formats seconds as `HH:MM:SS` or `MM:SS`.
String formatSeconds(int seconds) {
  final h = seconds ~/ 3600;
  final m = (seconds % 3600) ~/ 60;
  final s = seconds % 60;
  if (h > 0) return '${_two(h)}:${_two(m)}:${_two(s)}';
  return '${_two(m)}:${_two(s)}';
}
