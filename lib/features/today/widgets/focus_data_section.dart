import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:goworkbro/providers/app_provider.dart';
import 'package:goworkbro/features/today/widgets/total_focus_card.dart';
import 'package:goworkbro/features/today/widgets/source_pie_card.dart';
import 'package:goworkbro/features/today/widgets/weekly_bar_card.dart';
import 'package:goworkbro/features/today/widgets/sessions_card.dart';

/// Left/data column of the Today screen.
///
/// Composes [TotalFocusCard], [SourcePieCard], [WeeklyBarCard] and
/// [SessionsCard] into a scrollable list.
class FocusDataSection extends StatelessWidget {
  const FocusDataSection({super.key, this.weeklySeconds = const []});

  final List<int> weeklySeconds;

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    final totalSeconds = provider.todayTotalFocusSeconds;
    final sessionCount = provider.todaySessionCount;
    final bySource = provider.todayFocusBySource;
    final sessions = provider.todaySessions;

    return ListView(
      children: [
        TotalFocusCard(totalSeconds: totalSeconds, sessionCount: sessionCount),
        const SizedBox(height: 16),
        SourcePieCard(bySource: bySource),
        const SizedBox(height: 16),
        WeeklyBarCard(
          weeklySeconds: weeklySeconds,
          endDate: provider.todayDate,
        ),
        const SizedBox(height: 16),
        SessionsCard(sessions: sessions),
      ],
    );
  }
}
