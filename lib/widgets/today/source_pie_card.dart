import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';

import '../../theme/app_theme.dart';

/// Pie chart card showing today's focus time allocation by source.
class SourcePieCard extends StatelessWidget {
  const SourcePieCard({super.key, required this.bySource});

  final Map<String, int> bySource;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    final entries = bySource.entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    final total = entries.fold<int>(0, (s, e) => s + e.value);

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
              '时间分配',
              style: theme.textTheme.titleMedium?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 16),
            if (entries.isEmpty || total == 0)
              _emptyHint(context, '今天还没有专注数据')
            else
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(
                    width: 140,
                    height: 140,
                    child: PieChart(
                      PieChartData(
                        sectionsSpace: 2,
                        centerSpaceRadius: 36,
                        sections: _buildSections(entries, total),
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(child: _legend(context, entries, total)),
                ],
              ),
          ],
        ),
      ),
    );
  }

  List<PieChartSectionData> _buildSections(
    List<MapEntry<String, int>> entries,
    int total,
  ) {
    final colors = AppTheme.chartColors;
    return List<PieChartSectionData>.generate(entries.length, (i) {
      final e = entries[i];
      final pct = total == 0 ? 0.0 : (e.value / total) * 100;
      return PieChartSectionData(
        color: colors[i % colors.length],
        value: e.value.toDouble(),
        title: pct >= 8 ? '${pct.toStringAsFixed(0)}%' : '',
        radius: 36,
        titleStyle: const TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: Colors.white,
        ),
      );
    });
  }

  Widget _legend(
    BuildContext context,
    List<MapEntry<String, int>> entries,
    int total,
  ) {
    final theme = Theme.of(context);
    final colors = AppTheme.chartColors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (int i = 0; i < entries.length; i++)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Row(
              children: [
                Container(
                  width: 10,
                  height: 10,
                  decoration: BoxDecoration(
                    color: colors[i % colors.length],
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    entries[i].key,
                    style: theme.textTheme.bodySmall,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Text(
                  _formatShort(entries[i].value),
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }

  String _formatShort(int seconds) {
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    if (h > 0) return '${h}h${m > 0 ? '${m}m' : ''}';
    return '${m}m';
  }

  Widget _emptyHint(BuildContext context, String text) {
    return SizedBox(
      height: 140,
      child: Center(
        child: Text(
          text,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
        ),
      ),
    );
  }
}
