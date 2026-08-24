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

  group('logicalDateKey', () {
    test('keeps activity before 4 AM on the previous day', () {
      expect(
        logicalDateKey(
          now: DateTime(2026, 8, 17, 2, 30),
          lateNightModeEnabled: true,
          lastRolloverDate: '2026-08-16',
        ),
        '2026-08-16',
      );
      expect(
        logicalDateKey(
          now: DateTime(2026, 8, 17, 3, 59),
          lateNightModeEnabled: true,
          lastRolloverDate: '2026-08-16',
        ),
        '2026-08-16',
      );
    });

    test('starts the new day at 04:00 sharp', () {
      for (final hour in [4, 5, 12, 23]) {
        expect(
          logicalDateKey(
            now: DateTime(2026, 8, 17, hour),
            lateNightModeEnabled: true,
            lastRolloverDate: '2026-08-16',
          ),
          '2026-08-17',
        );
      }
    });

    test('uses the calendar day when the mode is off', () {
      expect(
        logicalDateKey(
          now: DateTime(2026, 8, 17, 2),
          lateNightModeEnabled: false,
          lastRolloverDate: '2026-08-16',
        ),
        '2026-08-17',
      );
    });

    test('never moves a day backwards after rollover already happened', () {
      expect(
        logicalDateKey(
          now: DateTime(2026, 8, 17, 2),
          lateNightModeEnabled: true,
          lastRolloverDate: '2026-08-17',
        ),
        '2026-08-17',
      );
    });
  });

  group('sleepRecordDateKey', () {
    test('keeps after-midnight sleep and wake-up on the same calendar row', () {
      expect(sleepRecordDateKey(DateTime(2026, 8, 17, 1, 30)), '2026-08-17');
    });

    test('stores an evening sleep check-in on the following wake-up day', () {
      expect(sleepRecordDateKey(DateTime(2026, 8, 16, 23, 0)), '2026-08-17');
    });

    test('uses the wake-up calendar date directly', () {
      expect(wakeRecordDateKey(DateTime(2026, 8, 17, 8, 0)), '2026-08-17');
    });
  });

  group('resolveCheckInDateTime', () {
    test('keeps a selected earlier time on the current calendar date', () {
      expect(
        resolveCheckInDateTime(
          now: DateTime(2026, 8, 17, 15),
          hour: 1,
          minute: 0,
        ),
        DateTime(2026, 8, 17, 1),
      );
    });

    test('uses the previous day when the selected clock time is still ahead', () {
      expect(
        resolveCheckInDateTime(
          now: DateTime(2026, 8, 17, 8),
          hour: 23,
          minute: 0,
        ),
        DateTime(2026, 8, 16, 23),
      );
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
