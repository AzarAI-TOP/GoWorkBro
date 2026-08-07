import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import '../models/models.dart';
import '../providers/app_provider.dart';
import '../services/app_locale.dart';
import '../services/sync_service.dart';
import '../services/update_service.dart';
import '../theme/app_theme.dart';

class MeScreen extends StatefulWidget {
  const MeScreen({super.key});

  @override
  State<MeScreen> createState() => _MeScreenState();
}

class _MeScreenState extends State<MeScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    final theme = Theme.of(context);
    final s = S.of(context.read<AppLocaleProvider>().locale);

    return Scaffold(
      body: Column(
        children: [
          _buildProfileHeader(context, provider, theme),
          TabBar(
            controller: _tabController,
            tabs: [
              Tab(text: s.checkIn),
              Tab(text: s.stats),
              Tab(text: s.settings),
            ],
            labelColor: theme.colorScheme.primary,
            unselectedLabelColor: theme.colorScheme.onSurfaceVariant,
            indicatorColor: theme.colorScheme.primary,
            dividerColor: Colors.transparent,
          ),
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [
                _buildSleepTab(context, provider, theme),
                _buildStatsTab(context, provider, theme),
                _buildSettingsTab(context, provider, theme),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildProfileHeader(BuildContext context, AppProvider provider, ThemeData theme) {
    return Container(
      padding: const EdgeInsets.fromLTRB(24, 48, 24, 24),
      child: Row(
        children: [
          GestureDetector(
            onTap: () => _showAvatarPicker(context),
            child: Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  colors: [
                    theme.colorScheme.primary,
                    theme.colorScheme.secondary,
                  ],
                ),
              ),
              child: const Icon(
                Icons.person,
                size: 36,
                color: Colors.white,
              ),
            ),
          ),
          const SizedBox(width: 20),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                GestureDetector(
                  onTap: () => _showEditNameDialog(context, provider),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        provider.userName,
                        style: theme.textTheme.headlineMedium,
                      ),
                      const SizedBox(width: 8),
                      Icon(Icons.edit, size: 16, color: theme.colorScheme.primary),
                    ],
                  ),
                ),
                const SizedBox(height: 4),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                      'ID: ${provider.userName}',
                    style: TextStyle(
                      fontSize: 12,
                      color: theme.colorScheme.primary,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ============ Sleep / Check-in Tab ============

  Widget _buildSleepTab(BuildContext context, AppProvider provider, ThemeData theme) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final today = provider.todayDate;
    final todayRecord = provider.sleepRecords.where((r) => r.recordDate == today).toList();
    final wakeTime = todayRecord.isNotEmpty ? todayRecord.first.wakeTime : null;
    final workoutTime = todayRecord.isNotEmpty ? todayRecord.first.workoutTime : null;
    final sleepTime = todayRecord.isNotEmpty ? todayRecord.first.sleepTime : null;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Today's check-in card — 3 buttons in a row
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(s.todayCheckIn, style: theme.textTheme.titleLarge),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: _buildCheckInButton(
                        context,
                        theme,
                        label: s.wakeUp,
                        icon: Icons.wb_sunny_outlined,
                        time: wakeTime,
                        onTap: () => _recordTime(context, provider, 'wake'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildCheckInButton(
                        context,
                        theme,
                        label: s.workout,
                        icon: Icons.fitness_center_outlined,
                        time: workoutTime,
                        onTap: () => _recordTime(context, provider, 'workout'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildCheckInButton(
                        context,
                        theme,
                        label: s.sleep,
                        icon: Icons.bedtime_outlined,
                        time: sleepTime,
                        onTap: () => _recordTime(context, provider, 'sleep'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        // Sleep history
        Text(s.checkInHistory, style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        if (provider.sleepRecords.isEmpty)
          Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Text(s.noCheckInRecords, style: theme.textTheme.bodyMedium),
            ),
          )
        else
          ...provider.sleepRecords.take(10).map((r) => _buildSleepRecordCard(r, theme)),
      ],
    );
  }

  Widget _buildCheckInButton(
    BuildContext context,
    ThemeData theme, {
    required String label,
    required IconData icon,
    required String? time,
    required VoidCallback onTap,
  }) {
    final hasRecord = time != null;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 8),
          decoration: BoxDecoration(
            color: hasRecord
                ? theme.colorScheme.primary.withValues(alpha: 0.08)
                : theme.colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: hasRecord
                  ? theme.colorScheme.primary.withValues(alpha: 0.3)
                  : Colors.transparent,
            ),
          ),
          child: Column(
            children: [
              Icon(icon, size: 28, color: hasRecord ? theme.colorScheme.primary : theme.colorScheme.onSurfaceVariant),
              const SizedBox(height: 8),
              Text(label, style: theme.textTheme.bodyMedium),
              const SizedBox(height: 4),
              Text(
                hasRecord ? _formatTime(time) : S.of(context.read<AppLocaleProvider>().locale).notCheckedIn,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: hasRecord ? FontWeight.w600 : FontWeight.normal,
                  color: hasRecord ? theme.colorScheme.primary : theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSleepRecordCard(SleepRecord record, ThemeData theme) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: theme.colorScheme.primary.withValues(alpha: 0.1),
          ),
          child: const Icon(Icons.calendar_today, size: 20),
        ),
        title: Text(record.recordDate, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
        subtitle: Text(
          '起床 ${_formatTime(record.wakeTime)}  ·  健身 ${_formatTime(record.workoutTime)}  ·  睡觉 ${_formatTime(record.sleepTime)}',
          style: const TextStyle(fontSize: 12),
        ),
      ),
    );
  }

  void _recordTime(BuildContext context, AppProvider provider, String type) async {
    final now = TimeOfDay.now();
    final helpText = switch (type) {
      'wake' => '选择起床时间',
      'workout' => '选择健身时间',
      'sleep' => '选择睡觉时间',
      _ => '选择时间',
    };
    final picked = await showTimePicker(
      context: context,
      initialTime: now,
      helpText: helpText,
    );
    if (picked == null) return;

    final timeStr = '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
    final today = provider.todayDate;
    final existing = provider.sleepRecords.where((r) => r.recordDate == today).toList();

    SleepRecord record;
    if (existing.isNotEmpty) {
      switch (type) {
        case 'wake':
          record = existing.first.copyWith(wakeTime: timeStr);
          break;
        case 'workout':
          record = existing.first.copyWith(workoutTime: timeStr);
          break;
        case 'sleep':
          record = existing.first.copyWith(sleepTime: timeStr);
          break;
        default:
          record = existing.first;
      }
    } else {
      record = SleepRecord.create(
        recordDate: today,
        wakeTime: type == 'wake' ? timeStr : null,
        workoutTime: type == 'workout' ? timeStr : null,
        sleepTime: type == 'sleep' ? timeStr : null,
      );
    }
    await provider.recordSleep(record);
  }

  String _formatTime(String? time) {
    return time ?? '—';
  }

  void _showAvatarPicker(BuildContext context) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.avatarUpload),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.1),
              ),
              child: Icon(Icons.cloud_upload_outlined, size: 36, color: Theme.of(context).colorScheme.primary),
            ),
            const SizedBox(height: 16),
            const Text('头像上传功能开发中，敬请期待 🚀'),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('好的')),
        ],
      ),
    );
  }

  void _showEditNameDialog(BuildContext context, AppProvider provider) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final controller = TextEditingController(text: provider.userName);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.editName),
        content: TextField(
          controller: controller,
          decoration: InputDecoration(hintText: s.nameHint),
          autofocus: true,
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: Text(s.cancel)),
          TextButton(
            onPressed: () {
              if (controller.text.trim().isNotEmpty) {
                provider.setUserName(controller.text.trim());
              }
              Navigator.pop(context);
            },
            child: Text(s.save),
          ),
        ],
      ),
    );
  }

  // ============ Stats Tab ============

  Widget _buildStatsTab(BuildContext context, AppProvider provider, ThemeData theme) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
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
          value: '${provider.todaySessionCount} 个',
          icon: Icons.local_fire_department_outlined,
          color: AppTheme.chartColors[3],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.todoCompleted,
          value: '${provider.todos.where((t) => t.isCompleted).length} / ${provider.todos.length}',
          icon: Icons.check_circle_outline,
          color: AppTheme.chartColors[2],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.habitCompleted,
          value: '${provider.habits.where((h) => h.isCompleted).length} / ${provider.habits.length}',
          icon: Icons.repeat_outlined,
          color: AppTheme.chartColors[1],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: s.activeCountdowns,
          value: '${provider.countdowns.length} 个',
          icon: Icons.hourglass_empty,
          color: AppTheme.chartColors[4],
        ),
      ],
    );
  }

  Widget _buildStatCard(ThemeData theme, {
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
            Expanded(
              child: Text(title, style: theme.textTheme.bodyMedium),
            ),
            Text(value, style: theme.textTheme.titleLarge?.copyWith(color: color)),
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

  // ============ Settings Tab ============

  Widget _buildSettingsTab(BuildContext context, AppProvider provider, ThemeData theme) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // ---- Language selector ----
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.language, size: 20, color: theme.colorScheme.primary),
                    const SizedBox(width: 8),
                    Text(s.language, style: theme.textTheme.titleMedium),
                  ],
                ),
                const SizedBox(height: 12),
                SegmentedButton<AppLocale>(
                  segments: [
                    ButtonSegment(value: AppLocale.zh, label: Text(s.chinese)),
                    ButtonSegment(value: AppLocale.en, label: Text(s.english)),
                  ],
                  selected: {context.read<AppLocaleProvider>().locale},
                  onSelectionChanged: (set) => context.read<AppLocaleProvider>().setLocale(set.first),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),

        // ---- Theme mode selector ----
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.palette_outlined, size: 20, color: theme.colorScheme.primary),
                    const SizedBox(width: 8),
                    Text(s.theme, style: theme.textTheme.titleMedium),
                  ],
                ),
                const SizedBox(height: 12),
                SegmentedButton<ThemeMode>(
                  segments: [
                    ButtonSegment(value: ThemeMode.light, label: Text(s.lightMode)),
                    ButtonSegment(value: ThemeMode.dark, label: Text(s.darkMode)),
                    ButtonSegment(value: ThemeMode.system, label: Text(s.systemMode)),
                  ],
                  selected: {context.read<AppLocaleProvider>().themeMode},
                  onSelectionChanged: (set) => context.read<AppLocaleProvider>().setThemeMode(set.first),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),

        // ---- Cloud sync status ----
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.cloud_done_outlined, size: 20,
                        color: SyncService.isInitialized ? Colors.green : Colors.grey),
                    const SizedBox(width: 8),
                    Text(s.cloudSync, style: theme.textTheme.titleMedium),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Container(
                      width: 8,
                      height: 8,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: SyncService.isInitialized ? Colors.green : Colors.grey,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      SyncService.isInitialized ? s.connectedToSupabase : s.notConnected,
                      style: theme.textTheme.bodyMedium,
                    ),
                  ],
                ),
                if (SyncService.isInitialized) ...[
                  const SizedBox(height: 8),
                  Text(
                    '${s.user}: ${Supabase.instance.client.auth.currentUser?.email ?? s.unknown}',
                    style: theme.textTheme.bodySmall,
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),

        // ---- Check for updates ----
        Card(
          child: ListTile(
            leading: const Icon(Icons.system_update),
            title: Text(s.checkUpdate),
            subtitle: const Text('检查是否有新版本可用'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _checkForUpdate(context),
          ),
        ),
        const SizedBox(height: 16),

        // ---- About (merged with licenses) ----
        Card(
          child: ListTile(
            leading: const Icon(Icons.info_outline),
            title: Text(s.aboutGoWorkBro),
            subtitle: Text('${s.version} 1.0.0'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              showAboutDialog(
                context: context,
                applicationName: 'GoWorkBro',
                applicationVersion: '1.0.0',
                applicationLegalese: '© 2026 AzarAI',
                applicationIcon: Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.check_circle, color: Colors.white, size: 28),
                ),
              );
            },
          ),
        ),
        const SizedBox(height: 16),

        // ---- Logout ----
        Card(
          child: ListTile(
            leading: const Icon(Icons.logout, color: Colors.redAccent),
            title: Text(s.logout, style: const TextStyle(color: Colors.redAccent)),
            subtitle: const Text('退出后数据保留在本地，重新登录可同步'),
            trailing: const Icon(Icons.chevron_right, color: Colors.redAccent),
            onTap: () => _showLogoutConfirm(context),
          ),
        ),
        const SizedBox(height: 16),

        // ---- Delete all data (red, bottom) ----
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            onPressed: () => _showDeleteDataConfirm(context, provider),
            icon: const Icon(Icons.delete_forever, color: Colors.redAccent),
            label: Text(s.deleteData, style: const TextStyle(color: Colors.redAccent)),
            style: OutlinedButton.styleFrom(
              foregroundColor: Colors.redAccent,
              side: const BorderSide(color: Colors.redAccent),
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
          ),
        ),
        const SizedBox(height: 24),
      ],
    );
  }

  void _showLogoutConfirm(BuildContext context) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.logout),
        content: Text(s.logoutConfirm),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: Text(s.cancel)),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              Supabase.instance.client.auth.signOut();
            },
            style: TextButton.styleFrom(foregroundColor: Colors.redAccent),
            child: Text(s.signOut),
          ),
        ],
      ),
    );
  }

  void _showDeleteDataConfirm(BuildContext context, AppProvider provider) async {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.deleteData),
        content: Text(s.deleteDataConfirm),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(s.cancel)),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.redAccent),
            child: Text(s.delete),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await provider.deleteAllData();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(s.deleteDataSuccess)),
    );
  }

  Future<void> _checkForUpdate(BuildContext context) async {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    // Show loading
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        content: Row(
          children: [
            const CircularProgressIndicator(),
            const SizedBox(width: 16),
            Text(s.checkingUpdate),
          ],
        ),
      ),
    );

    final update = await UpdateService.checkForUpdate();

    if (!mounted) return;
    Navigator.of(context).pop(); // Close loading dialog

    if (update != null) {
      UpdateService.showUpdateDialog(context, update);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(s.latestVersion)),
      );
    }
  }
}
