import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';

/// 7-day bar chart card showing focus time per day.
class WeeklyBarCard extends StatelessWidget {
  const WeeklyBarCard({super.key, required this.weeklySeconds});

  final List<int> weeklySeconds;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    // Convert seconds to hours; fallback to zeros if no data yet
    final hours = List<double>.generate(7, (i) {
      if (i < weeklySeconds.length) return weeklySeconds[i] / 3600.0;
      return 0.0;
    });

    final maxY = (hours.fold<double>(0, (a, b) => a > b ? a : b) * 1.2)
        .clamp(1.0, double.infinity);

    final labels = _last7DayLabels();

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: colorScheme.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '最近 7 天',
              style: theme.textTheme.titleMedium?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              height: 180,
              child: BarChart(
                BarChartData(
                  alignment: BarChartAlignment.spaceAround,
                  maxY: maxY,
                  barTouchData: BarTouchData(
                    touchTooltipData: BarTouchTooltipData(
                      getTooltipItem: (group, gAmt, rod, rAmt) {
                        return BarTooltipItem(
                          '${rod.toY.toStringAsFixed(1)}h',
                          TextStyle(
                            color: colorScheme.onInverseSurface,
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                        );
                      },
                    ),
                  ),
                  titlesData: FlTitlesData(
                    show: true,
                    topTitles: const AxisTitles(
                      sideTitles: SideTitles(showTitles: false),
                    ),
                    rightTitles: const AxisTitles(
                      sideTitles: SideTitles(showTitles: false),
                    ),
                    leftTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        reservedSize: 28,
                        interval: _niceInterval(maxY),
                        getTitlesWidget: (value, meta) =>
                            _leftTitle(value, theme),
                      ),
                    ),
                    bottomTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        reservedSize: 24,
                        getTitlesWidget: (value, meta) =>
                            _bottomTitle(value, labels, theme),
                      ),
                    ),
                  ),
                  gridData: FlGridData(
                    show: true,
                    drawVerticalLine: false,
                    horizontalInterval: _niceInterval(maxY),
                    getDrawingHorizontalLine: (value) => FlLine(
                      color: colorScheme.outlineVariant.withValues(alpha: 0.5),
                      strokeWidth: 1,
                    ),
                  ),
                  borderData: FlBorderData(show: false),
                  barGroups: List<BarChartGroupData>.generate(hours.length, (i) {
                    final isToday = i == hours.length - 1;
                    return BarChartGroupData(
                      x: i,
                      barRods: [
                        BarChartRodData(
                          toY: hours[i],
                          width: 18,
                          borderRadius: const BorderRadius.vertical(
                            top: Radius.circular(6),
                          ),
                          color: isToday
                              ? colorScheme.primary
                              : colorScheme.primary.withValues(alpha: 0.4),
                        ),
                      ],
                    );
                  }),
                ),
              ),
            ),
            const SizedBox(height: 4),
          ],
        ),
      ),
    );
  }

  double _niceInterval(double maxY) {
    if (maxY <= 2) return 0.5;
    if (maxY <= 4) return 1;
    if (maxY <= 8) return 2;
    return (maxY / 4).roundToDouble();
  }

  Widget _leftTitle(double value, ThemeData theme) {
    return Padding(
      padding: const EdgeInsets.only(right: 4),
      child: Text(
        '${value.toStringAsFixed(value % 1 == 0 ? 0 : 1)}h',
        style: theme.textTheme.labelSmall?.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }

  Widget _bottomTitle(double value, List<String> labels, ThemeData theme) {
    final i = value.toInt();
    if (i < 0 || i >= labels.length) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Text(
        labels[i],
        style: theme.textTheme.labelSmall?.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }

  List<String> _last7DayLabels() {
    final now = DateTime.now();
    final labels = <String>[];
    const week = ['日', '一', '二', '三', '四', '五', '六'];
    for (int i = 6; i >= 0; i--) {
      final d = now.subtract(Duration(days: i));
      labels.add(week[d.weekday % 7]);
    }
    return labels;
  }
}
