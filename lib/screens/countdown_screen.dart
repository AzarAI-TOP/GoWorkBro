import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/models.dart';
import '../providers/app_provider.dart';
import '../widgets/countdown/countdown_card.dart';
import '../widgets/countdown/add_countdown_sheet.dart';

class CountdownScreen extends StatefulWidget {
  const CountdownScreen({super.key});

  @override
  State<CountdownScreen> createState() => _CountdownScreenState();
}

class _CountdownScreenState extends State<CountdownScreen> with WidgetsBindingObserver {
  Timer? _timer;
  bool _isScreenVisible = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _startTimer();
  }

  void _startTimer() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted && _isScreenVisible) setState(() {});
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Pause timer when app is backgrounded, resume when foregrounded
    if (state == AppLifecycleState.resumed) {
      _isScreenVisible = true;
      _startTimer();
    } else {
      _isScreenVisible = false;
      _timer?.cancel();
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    final theme = Theme.of(context);
    final countdowns = provider.countdowns;

    return Scaffold(
      appBar: AppBar(
        title: const Text('倒计时'),
      ),
      body: countdowns.isEmpty
          ? _buildEmptyState(theme)
          : ListView.builder(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 80),
              itemCount: countdowns.length,
              itemBuilder: (context, index) {
                final cd = countdowns[index];
                return CountdownCard(
                  countdown: cd,
                  onDelete: () => provider.deleteCountdown(cd.id),
                  onEdit: () => _showEditSheet(context, provider, cd),
                );
              },
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showAddSheet(context, provider),
        child: const Icon(Icons.add),
      ),
    );
  }

  Widget _buildEmptyState(ThemeData theme) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.hourglass_empty, size: 64, color: theme.colorScheme.primary.withValues(alpha: 0.5)),
          const SizedBox(height: 16),
          Text('还没有倒计时', style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
          Text('点击右下角 + 添加', style: theme.textTheme.bodyMedium),
        ],
      ),
    );
  }

  void _showAddSheet(BuildContext context, AppProvider provider) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => AddCountdownSheet(provider: provider),
    );
  }

  void _showEditSheet(BuildContext context, AppProvider provider, Countdown cd) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (context) => AddCountdownSheet(provider: provider, existing: cd),
    );
  }
}
