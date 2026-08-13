import 'package:fl_chart/fl_chart.dart';

/// Splits observations into contiguous date runs so a chart never draws a
/// misleading line through a day with no sleep record.
List<List<FlSpot>> splitContiguousSleepSpots(List<FlSpot> spots) {
  if (spots.isEmpty) return const [];
  final sorted = [...spots]..sort((a, b) => a.x.compareTo(b.x));
  final runs = <List<FlSpot>>[];
  var current = <FlSpot>[sorted.first];
  for (final spot in sorted.skip(1)) {
    if ((spot.x - current.last.x).abs() <= 1.001) {
      current.add(spot);
    } else {
      runs.add(current);
      current = <FlSpot>[spot];
    }
  }
  runs.add(current);
  return runs;
}

/// Calculates overnight sleep duration in hours. Both values use a 24-hour
/// clock; crossing midnight is handled by wrapping into the next day.
double overnightDurationHours(double bedtime, double wakeTime) {
  var duration = wakeTime - bedtime;
  if (duration < 0) duration += 24;
  return duration;
}
