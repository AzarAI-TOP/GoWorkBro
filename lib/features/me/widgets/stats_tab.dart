import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/core/theme/app_theme.dart';
import 'package:goworkbro/providers/app_provider.dart';

/// Stats tab: lifetime and today's aggregated counters.
class StatsTab extends StatelessWidget {
  const StatsTab({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    final theme = Theme.of(context);
    final s = S.of(context.read<AppLocaleProvider>().locale);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(s.lifetimeStats, style: theme.textTheme.titleLarge),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.totalFocus,
          value: _formatDuration(provider.lifetimeFocusSeconds),
          icon: Icons.all_inclusive,
          color: AppTheme.chartColors[0],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.totalSessions,
          value: '${provider.lifetimeSessionCount}',
          icon: Icons.local_fire_department_outlined,
          color: AppTheme.chartColors[3],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.totalTodos,
          value: '${provider.lifetimeTodosCompleted}',
          icon: Icons.task_alt,
          color: AppTheme.chartColors[2],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.totalHabits,
          value: '${provider.lifetimeHabitsCompleted}',
          icon: Icons.repeat_rounded,
          color: AppTheme.chartColors[1],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.usingSince,
          value: provider.firstUsedDate.split('T').first,
          icon: Icons.calendar_month_outlined,
          color: AppTheme.chartColors[4],
        ),
        const SizedBox(height: 24),
        Divider(color: theme.dividerColor),
        const SizedBox(height: 16),
        Text(s.today, style: theme.textTheme.titleLarge),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.todayFocus,
          value: _formatDuration(provider.todayTotalFocusSeconds),
          icon: Icons.timer_outlined,
          color: AppTheme.chartColors[0],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.pomodoroCount,
          value: s.count(provider.todaySessionCount),
          icon: Icons.local_fire_department_outlined,
          color: AppTheme.chartColors[3],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.todoCompleted,
          value:
              '${provider.todos.where((t) => t.isCompleted).length} / ${provider.todos.length}',
          icon: Icons.check_circle_outline,
          color: AppTheme.chartColors[2],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.habitCompleted,
          value:
              '${provider.habits.where((h) => h.isCompleted).length} / ${provider.habits.length}',
          icon: Icons.repeat_outlined,
          color: AppTheme.chartColors[1],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.activeCountdowns,
          value: s.count(provider.countdowns.length),
          icon: Icons.hourglass_empty,
          color: AppTheme.chartColors[4],
        ),
      ],
    );
  }

  Widget _buildStatCard(
    ThemeData theme, {
    required String title,
    required String value,
    required IconData icon,
    required Color color,
  }) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(icon, color: color, size: 24),
            ),
            const SizedBox(width: 16),
            Expanded(child: Text(title, style: theme.textTheme.bodyMedium)),
            Text(
              value,
              style: theme.textTheme.titleLarge?.copyWith(color: color),
            ),
          ],
        ),
      ),
    );
  }

  String _formatDuration(int seconds) {
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    if (h > 0) return '${h}h ${m}m';
    return '${m}m';
  }
}
