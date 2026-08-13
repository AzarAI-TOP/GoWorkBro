import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../models/models.dart';
import '../../services/app_locale.dart';
import 'package:provider/provider.dart';

/// TODO item card widget — no circle icon (tap to toggle complete)
class TodoCard extends StatelessWidget {
  final Todo todo;
  final int index;
  final VoidCallback onToggle;
  final VoidCallback onLongPress;
  final ValueChanged<TapDownDetails> onSecondaryTapDown;
  final bool showDragHandle;

  const TodoCard({
    super.key,
    required this.todo,
    required this.index,
    required this.onToggle,
    required this.onLongPress,
    required this.onSecondaryTapDown,
    this.showDragHandle = true,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final isDone = todo.isCompleted;
    final s = S.of(context.read<AppLocaleProvider>().locale);

    Color timingColor(TimingType t) {
      switch (t) {
        case TimingType.forward:
          return const Color(0xFF4A90D9);
        case TimingType.backward:
          return cs.primary;
        case TimingType.none:
          return theme.hintColor;
      }
    }

    return Card(
      child: InkWell(
        onTap: () {
          HapticFeedback.lightImpact();
          onToggle();
        },
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
              if (showDragHandle)
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
              if (showDragHandle) const SizedBox(width: 6),
              // No circle icon — just tap the card to toggle
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      todo.title,
                      style: theme.textTheme.titleMedium?.copyWith(
                        decoration: isDone
                            ? TextDecoration.lineThrough
                            : TextDecoration.none,
                        decorationThickness: 2,
                        color: isDone
                            ? theme.hintColor
                            : theme.textTheme.titleMedium?.color,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Wrap(
                      spacing: 6,
                      runSpacing: 4,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        _infoChip(
                          context,
                          s.timingLabel(todo.timingType.value),
                          timingColor(todo.timingType),
                        ),
                        if (todo.timingType == TimingType.backward)
                          _infoChip(
                            context,
                            '${todo.durationMinutes}min',
                            cs.primary,
                          ),
                        if (todo.keepTomorrow)
                          _infoChip(
                            context,
                            s.keepTomorrow,
                            const Color(0xFF50C878),
                          ),
                      ],
                    ),
                  ],
                ),
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
Widget _infoChip(BuildContext context, String text, Color color) {
  return Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
    decoration: BoxDecoration(
      color: color.withValues(alpha: 0.12),
      borderRadius: BorderRadius.circular(8),
    ),
    child: Text(
      text,
      style: Theme.of(context).textTheme.bodySmall?.copyWith(
        color: color,
        fontSize: 11,
        fontWeight: FontWeight.w500,
      ),
    ),
  );
}
