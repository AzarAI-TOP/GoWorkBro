import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../services/app_locale.dart';

/// Big number card showing today's total focus time and session count.
class TotalFocusCard extends StatelessWidget {
  const TotalFocusCard({
    super.key,
    required this.totalSeconds,
    required this.sessionCount,
  });

  final int totalSeconds;
  final int sessionCount;

  String _formatDuration(int seconds) {
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    if (h == 0) return '${m}m';
    if (m == 0) return '${h}h';
    return '${h}h ${m}m';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final s = S.of(context.watch<AppLocaleProvider>().locale);

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
              s.todayFocus,
              style: theme.textTheme.titleMedium?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              _formatDuration(totalSeconds),
              style: theme.textTheme.displayMedium?.copyWith(
                fontWeight: FontWeight.w700,
                color: colorScheme.primary,
                height: 1.1,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              '$sessionCount ${s.pomodoroCount}',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
