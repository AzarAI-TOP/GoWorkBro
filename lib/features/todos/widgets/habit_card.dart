import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:provider/provider.dart';

/// Habit item card widget — extracted from todo_screen.dart
class HabitCard extends StatelessWidget {
  final Habit habit;
  final int index;
  final VoidCallback onIncrement;
  final VoidCallback onDecrement;
  final VoidCallback onLongPress;
  final ValueChanged<TapDownDetails> onSecondaryTapDown;

  const HabitCard({
    super.key,
    required this.habit,
    required this.index,
    required this.onIncrement,
    required this.onDecrement,
    required this.onLongPress,
    required this.onSecondaryTapDown,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final done = habit.isCompleted;
    final canDec = habit.currentCount > 0;
    final s = S.of(context.read<AppLocaleProvider>().locale);

    return Card(
      child: InkWell(
        onLongPress: () {
          HapticFeedback.mediumImpact();
          onLongPress();
        },
        onSecondaryTapDown: onSecondaryTapDown,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
          child: Row(
            children: [
              ReorderableDragStartListener(
                index: index,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 2),
                  child: Icon(
                    Icons.drag_indicator,
                    color: theme.hintColor,
                    size: 22,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      children: [
                        Icon(
                          Icons.repeat_rounded,
                          size: 16,
                          color: done ? cs.primary : theme.hintColor,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            habit.title,
                            style: theme.textTheme.titleMedium?.copyWith(
                              decoration: done
                                  ? TextDecoration.lineThrough
                                  : TextDecoration.none,
                              color: done
                                  ? theme.hintColor
                                  : theme.textTheme.titleMedium?.color,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          s.habitProgress(
                            habit.currentCount,
                            habit.targetCount,
                            habit.unit,
                          ),
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: done ? cs.primary : theme.hintColor,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(6),
                      child: LinearProgressIndicator(
                        value: habit.progress,
                        minHeight: 6,
                        backgroundColor: cs.primary.withValues(alpha: 0.12),
                        valueColor: AlwaysStoppedAnimation<Color>(
                          done ? const Color(0xFF50C878) : cs.primary,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              if (habit.targetCount == 1)
                _circleButton(
                  done ? Icons.check : Icons.check_rounded,
                  done ? const Color(0xFF50C878) : cs.primary,
                  done ? onDecrement : onIncrement,
                )
              else
                Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    _circleButton(
                      Icons.add,
                      cs.primary,
                      onIncrement,
                      enabled: !done,
                    ),
                    const SizedBox(height: 6),
                    _circleButton(
                      Icons.remove,
                      theme.hintColor,
                      onDecrement,
                      enabled: canDec,
                    ),
                  ],
                ),
            ],
          ),
        ),
      ),
    );
  }
}

// ============================================================
// Shared small widgets
// ============================================================
Widget _circleButton(
  IconData icon,
  Color color,
  VoidCallback onTap, {
  bool enabled = true,
}) {
  return GestureDetector(
    onTap: enabled
        ? () {
            HapticFeedback.lightImpact();
            onTap();
          }
        : null,
    child: Container(
      width: 30,
      height: 30,
      decoration: BoxDecoration(
        color: enabled ? color : color.withValues(alpha: 0.25),
        shape: BoxShape.circle,
      ),
      child: Icon(icon, color: Colors.white, size: 18),
    ),
  );
}
