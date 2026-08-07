import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/models.dart';
import '../../services/app_locale.dart';
import '../../theme/app_theme.dart';

class CountdownCard extends StatelessWidget {
  final Countdown countdown;
  final VoidCallback onDelete;
  final VoidCallback onEdit;

  const CountdownCard({
    super.key,
    required this.countdown,
    required this.onDelete,
    required this.onEdit,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = S.of(context.watch<AppLocaleProvider>().locale);
    final color = AppTheme.chartColors[countdown.colorIndex % AppTheme.chartColors.length];
    final remaining = countdown.remaining;
    final expired = countdown.isExpired;

    // Progress ring: shows REMAINING proportion (full = lots of time, empty = almost done)
    final totalDuration = countdown.targetDateTime.difference(DateTime.parse(countdown.createdDate));
    double remainingRatio = 0;
    if (totalDuration.inSeconds > 0) {
      remainingRatio = (remaining.inSeconds / totalDuration.inSeconds).clamp(0.0, 1.0);
    }

    final days = remaining.isNegative ? 0 : remaining.inDays;
    final hours = remaining.isNegative ? 0 : remaining.inHours % 24;
    final minutes = remaining.isNegative ? 0 : remaining.inMinutes % 60;
    final seconds = remaining.isNegative ? 0 : remaining.inSeconds % 60;

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: onEdit,
        onLongPress: () => _showDeleteConfirm(context, onDelete),
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              // Progress ring (remaining)
              SizedBox(
                width: 72, height: 72,
                child: Stack(
                  children: [
                    CircularProgressIndicator(
                      value: expired ? 0.0 : remainingRatio,
                      strokeWidth: 4,
                      backgroundColor: color.withValues(alpha: 0.15),
                      valueColor: AlwaysStoppedAnimation(expired ? theme.colorScheme.onSurfaceVariant : color),
                    ),
                    Center(
                      child: expired
                          ? Icon(Icons.check, color: theme.colorScheme.onSurfaceVariant, size: 28)
                          : Icon(Icons.hourglass_top, color: color, size: 28),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 20),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      countdown.title,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        decoration: expired ? TextDecoration.lineThrough : TextDecoration.none,
                        color: expired ? theme.colorScheme.onSurfaceVariant : theme.colorScheme.onSurface,
                      ),
                    ),
                    const SizedBox(height: 8),
                    if (expired)
                      Text(s.countdownFinished, style: TextStyle(fontSize: 14, color: theme.colorScheme.onSurfaceVariant, fontWeight: FontWeight.w500))
                    else ...[
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.baseline,
                        textBaseline: TextBaseline.alphabetic,
                        children: [
                          if (days > 0) ...[
                            _buildTimeUnit(theme, days, s.days),
                            const SizedBox(width: 8),
                          ],
                          _buildTimeUnit(theme, hours, s.hours),
                          const SizedBox(width: 8),
                          _buildTimeUnit(theme, minutes, s.mins),
                          const SizedBox(width: 8),
                          _buildTimeUnit(theme, seconds, s.secs),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text(
                        '目标：${_formatDateTime(countdown.targetDateTime)}',
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  ],
                ),
              ),
              Container(
                width: 4,
                height: 60,
                decoration: BoxDecoration(
                  color: color,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTimeUnit(ThemeData theme, int value, String unit) {
    return RichText(
      text: TextSpan(
        children: [
          TextSpan(
            text: value.toString().padLeft(2, '0'),
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w700,
              color: theme.colorScheme.onSurface,
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
          TextSpan(
            text: unit,
            style: TextStyle(fontSize: 12, color: theme.colorScheme.onSurfaceVariant),
          ),
        ],
      ),
    );
  }

  String _formatDateTime(DateTime dt) {
    return '${dt.month}/${dt.day} ${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }

  void _showDeleteConfirm(BuildContext context, VoidCallback onDelete) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.deleteCountdownTitle),
        content: Text('确认删除「${countdown.title}」？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: Text(s.cancel)),
          TextButton(
            onPressed: () { Navigator.pop(context); onDelete(); },
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: Text(s.delete),
          ),
        ],
      ),
    );
  }
}
