/// Date helpers shared across the app. All dates are local-time strings in
/// `yyyy-MM-dd` form unless stated otherwise.
library;

String _two(int n) => n.toString().padLeft(2, '0');

/// Today's date as `yyyy-MM-dd` (local time).
String dateKeyOf(DateTime date) =>
    '${date.year}-${_two(date.month)}-${_two(date.day)}';

/// Today's date as `yyyy-MM-dd` (local time).
String get todayDateKey => dateKeyOf(DateTime.now());

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
