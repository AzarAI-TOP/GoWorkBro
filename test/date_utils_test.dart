import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/core/utils/date_utils.dart';

void main() {
  group('dateKeyOf', () {
    test('pads month and day to two digits', () {
      expect(dateKeyOf(DateTime(2026, 8, 3)), '2026-08-03');
      expect(dateKeyOf(DateTime(2026, 12, 25)), '2026-12-25');
    });

    test('todayDateKey matches dateKeyOf(now)', () {
      final now = DateTime.now();
      expect(todayDateKey, dateKeyOf(now));
    });
  });

  group('hoursFromTime', () {
    test('parses HH:mm to decimal hours', () {
      expect(hoursFromTime('23:29'), closeTo(23.483333, 0.0001));
      expect(hoursFromTime('07:30'), 7.5);
    });

    test('returns null for null/garbage', () {
      expect(hoursFromTime(null), isNull);
      expect(hoursFromTime('abc'), isNull);
      expect(hoursFromTime('12'), isNull);
    });
  });

  group('formatHours', () {
    test('renders HH:mm', () {
      expect(formatHours(7.5), '07:30');
      expect(formatHours(23.483333333333334), '23:29');
    });

    test('wraps values >= 24 into the next day', () {
      expect(formatHours(25.5), '01:30');
      expect(formatHours(24.0), '00:00');
    });

    test('rounds minutes to nearest', () {
      expect(formatHours(7.999), '08:00');
    });
  });

  group('formatDurationHours', () {
    test('formats hours and minutes', () {
      expect(formatDurationHours(7.5), '7h 30m');
      expect(formatDurationHours(0.5), '30m');
      expect(formatDurationHours(8), '8h');
      expect(formatDurationHours(0), '0m');
    });

    test('rounds to nearest minute', () {
      expect(formatDurationHours(7.999), '8h');
      expect(formatDurationHours(7.49), '7h 29m');
    });
  });

  group('formatSeconds', () {
    test('formats MM:SS and HH:MM:SS', () {
      expect(formatSeconds(65), '01:05');
      expect(formatSeconds(3661), '01:01:01');
      expect(formatSeconds(0), '00:00');
    });
  });
}
