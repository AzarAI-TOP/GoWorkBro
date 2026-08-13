import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/core/utils/date_utils.dart';
import 'package:goworkbro/core/utils/sleep_chart_utils.dart';
import 'package:goworkbro/models/models.dart';

/// "近七天睡眠趋势" — three line charts (wake / bedtime / duration) for the
/// last seven days, one card each. Pure presentation: the caller passes the
/// sleep records and the localizer.
class SleepChartsSection extends StatelessWidget {
  const SleepChartsSection({
    super.key,
    required this.records,
    required this.strings,
  });

  final List<SleepRecord> records;
  final S strings;

  @override
  Widget build(BuildContext context) {
    final byDate = {for (final record in records) record.recordDate: record};
    final today = DateTime.now();
    final dates = [
      for (var i = 6; i >= 0; i--) today.subtract(Duration(days: i)),
    ];
    final ordered = [for (final date in dates) byDate[dateKeyOf(date)]];

    final wake = <FlSpot>[];
    final bedtime = <FlSpot>[];
    final duration = <FlSpot>[];
    for (var i = 0; i < ordered.length; i++) {
      final record = ordered[i];
      final wakeHours = hoursFromTime(record?.wakeTime);
      final sleepHours = hoursFromTime(record?.sleepTime);
      if (wakeHours != null) wake.add(FlSpot(i.toDouble(), wakeHours));
      if (sleepHours != null) {
        bedtime.add(
          FlSpot(i.toDouble(), sleepHours < 12 ? sleepHours + 24 : sleepHours),
        );
      }
      if (wakeHours != null && sleepHours != null) {
        final value = overnightDurationHours(sleepHours, wakeHours);
        duration.add(FlSpot(i.toDouble(), value));
      }
    }

    return Column(
      children: [
        _SleepLineChart(
          title: strings.wakeTime,
          averageLabel: strings.average,
          spots: wake,
          dates: dates,
        ),
        const SizedBox(height: 12),
        _SleepLineChart(
          title: strings.bedtime,
          averageLabel: strings.average,
          spots: bedtime,
          dates: dates,
        ),
        const SizedBox(height: 12),
        _SleepLineChart(
          title: strings.sleepDuration,
          averageLabel: strings.average,
          spots: duration,
          dates: dates,
          isDuration: true,
        ),
      ],
    );
  }
}

class _SleepLineChart extends StatelessWidget {
  const _SleepLineChart({
    required this.title,
    required this.averageLabel,
    required this.spots,
    required this.dates,
    this.isDuration = false,
  });

  final String title;
  final String averageLabel;
  final List<FlSpot> spots;
  final List<DateTime> dates;

  /// When true, spot values are hours (e.g. 7.5) and the tooltip shows
  /// "7h 30m"; otherwise they are clock times and show as HH:MM.
  final bool isDuration;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final average = spots.isEmpty
        ? 0.0
        : spots.fold<double>(0, (sum, spot) => sum + spot.y) / spots.length;
    final ys = spots.map((spot) => spot.y).toList();
    final minY = ys.isEmpty
        ? 0.0
        : (ys.reduce((a, b) => a < b ? a : b) - 1).clamp(0, 30);
    final maxY = ys.isEmpty
        ? 24.0
        : (ys.reduce((a, b) => a > b ? a : b) + 1).clamp(1, 30);

    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(title, style: theme.textTheme.titleSmall),
                if (spots.isNotEmpty)
                  Text(
                    '$averageLabel ${formatHours(average)}',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: Colors.redAccent,
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 150,
              child: spots.isEmpty
                  ? Center(child: Text('—', style: theme.textTheme.bodyLarge))
                  : LineChart(
                      LineChartData(
                        minX: 0,
                        maxX: 6,
                        minY: minY.toDouble(),
                        maxY: maxY.toDouble(),
                        gridData: const FlGridData(show: false),
                        borderData: FlBorderData(show: false),
                        lineTouchData: LineTouchData(
                          enabled: true,
                          touchTooltipData: LineTouchTooltipData(
                            // Keep the bubble above the dot (fl_chart
                            // default) and force it to stay inside the
                            // chart so it never overlaps the card header.
                            tooltipMargin: 20,
                            fitInsideVertically: true,
                            fitInsideHorizontally: true,
                            getTooltipColor: (spot) =>
                                theme.colorScheme.inverseSurface,
                            getTooltipItems: (touchedSpots) => [
                              for (final spot in touchedSpots)
                                LineTooltipItem(
                                  isDuration
                                      ? formatDurationHours(spot.y)
                                      : formatHours(spot.y),
                                  TextStyle(
                                    color: theme
                                        .colorScheme
                                        .onInverseSurface,
                                    fontWeight: FontWeight.w600,
                                    fontSize: 12,
                                  ),
                                ),
                            ],
                          ),
                        ),
                        extraLinesData: ExtraLinesData(
                          horizontalLines: [
                            HorizontalLine(
                              y: average,
                              color: Colors.redAccent,
                              strokeWidth: 1.5,
                              dashArray: [6, 4],
                            ),
                          ],
                        ),
                        titlesData: FlTitlesData(
                          topTitles: const AxisTitles(
                            sideTitles: SideTitles(showTitles: false),
                          ),
                          rightTitles: const AxisTitles(
                            sideTitles: SideTitles(showTitles: false),
                          ),
                          leftTitles: AxisTitles(
                            sideTitles: SideTitles(
                              showTitles: true,
                              reservedSize: 38,
                              getTitlesWidget: (value, meta) => Text(
                                formatHours(value),
                                style: theme.textTheme.bodySmall?.copyWith(
                                  fontSize: 9,
                                ),
                              ),
                            ),
                          ),
                          bottomTitles: AxisTitles(
                            sideTitles: SideTitles(
                              showTitles: true,
                              interval: 1,
                              getTitlesWidget: (value, meta) {
                                final index = value.round();
                                if (index < 0 || index >= dates.length) {
                                  return const SizedBox.shrink();
                                }
                                final date = dates[index];
                                return Padding(
                                  padding: const EdgeInsets.only(top: 4),
                                  child: Text(
                                    '${date.month}/${date.day}',
                                    style: theme.textTheme.bodySmall
                                        ?.copyWith(fontSize: 9),
                                  ),
                                );
                              },
                            ),
                          ),
                        ),
                        lineBarsData: [
                          for (final run in splitContiguousSleepSpots(spots))
                            LineChartBarData(
                              spots: run,
                              isCurved: true,
                              color: const Color(0xFFFF8A3D),
                              barWidth: 3,
                              dotData: const FlDotData(show: true),
                              belowBarData: BarAreaData(
                                show: true,
                                color: const Color(
                                  0xFFFF8A3D,
                                ).withValues(alpha: 0.12),
                              ),
                            ),
                        ],
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
