import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import '../models/models.dart';
import '../providers/app_provider.dart';
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

    return Scaffold(
      body: Column(
        children: [
          _buildProfileHeader(context, provider, theme),
          TabBar(
            controller: _tabController,
            tabs: const [
              Tab(text: '打卡'),
              Tab(text: '统计'),
              Tab(text: '设置'),
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
                    'ID: ${provider.userName == 'AzarAI' ? 'AzarAI' : provider.userName}',
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
    final today = provider.todayDate;
    final todayRecord = provider.sleepRecords.where((r) => r.recordDate == today).toList();

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Today's check-in card
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('今日打卡', style: theme.textTheme.titleLarge),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: _buildCheckInButton(
                        context,
                        theme,
                        label: '起床',
                        icon: Icons.wb_sunny_outlined,
                        time: todayRecord.isNotEmpty ? todayRecord.first.wakeTime : null,
                        onTap: () => _recordTime(context, provider, 'wake'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _buildCheckInButton(
                        context,
                        theme,
                        label: '睡觉',
                        icon: Icons.bedtime_outlined,
                        time: todayRecord.isNotEmpty ? todayRecord.first.sleepTime : null,
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
        Text('打卡记录', style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        if (provider.sleepRecords.isEmpty)
          Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Text('暂无打卡记录', style: theme.textTheme.bodyMedium),
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
          padding: const EdgeInsets.all(20),
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
            Icon(icon, size: 32, color: hasRecord ? theme.colorScheme.primary : theme.colorScheme.onSurfaceVariant),
            const SizedBox(height: 8),
            Text(label, style: theme.textTheme.bodyMedium),
            const SizedBox(height: 4),
            Text(
              hasRecord ? _formatTime(time) : '未打卡',
              style: TextStyle(
                fontSize: 13,
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
          '起床 ${_formatTime(record.wakeTime)}  ·  睡觉 ${_formatTime(record.sleepTime)}',
          style: const TextStyle(fontSize: 12),
        ),
      ),
    );
  }

  void _recordTime(BuildContext context, AppProvider provider, String type) async {
    final now = TimeOfDay.now();
    final picked = await showTimePicker(
      context: context,
      initialTime: now,
      helpText: type == 'wake' ? '选择起床时间' : '选择睡觉时间',
    );
    if (picked == null) return;

    final timeStr = '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
    final today = provider.todayDate;
    final existing = provider.sleepRecords.where((r) => r.recordDate == today).toList();

    SleepRecord record;
    if (existing.isNotEmpty) {
      record = type == 'wake'
          ? existing.first.copyWith(wakeTime: timeStr)
          : existing.first.copyWith(sleepTime: timeStr);
    } else {
      record = SleepRecord.create(
        recordDate: today,
        wakeTime: type == 'wake' ? timeStr : null,
        sleepTime: type == 'sleep' ? timeStr : null,
      );
    }
    await provider.recordSleep(record);
  }

  String _formatTime(String? time) {
    return time ?? '—';
  }

  void _showAvatarPicker(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('更换头像'),
        content: const Text('头像功能开发中，敬请期待 🚀'),
        actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('好的'))],
      ),
    );
  }

  void _showEditNameDialog(BuildContext context, AppProvider provider) {
    final controller = TextEditingController(text: provider.userName);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('编辑昵称'),
        content: TextField(
          controller: controller,
          decoration: const InputDecoration(hintText: '输入昵称'),
          autofocus: true,
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
          TextButton(
            onPressed: () {
              if (controller.text.trim().isNotEmpty) {
                provider.setUserName(controller.text.trim());
              }
              Navigator.pop(context);
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
  }

  // ============ Stats Tab ============

  Widget _buildStatsTab(BuildContext context, AppProvider provider, ThemeData theme) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _buildStatCard(
          theme,
          title: '今日专注',
          value: _formatDuration(provider.todayTotalFocusSeconds),
          icon: Icons.timer_outlined,
          color: AppTheme.chartColors[0],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: '今日番茄数',
          value: '${provider.todaySessionCount} 个',
          icon: Icons.local_fire_department_outlined,
          color: AppTheme.chartColors[3],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: '待办完成',
          value: '${provider.todos.where((t) => t.isCompleted).length} / ${provider.todos.length}',
          icon: Icons.check_circle_outline,
          color: AppTheme.chartColors[2],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: '习惯完成',
          value: '${provider.habits.where((h) => h.isCompleted).length} / ${provider.habits.length}',
          icon: Icons.repeat_outlined,
          color: AppTheme.chartColors[1],
        ),
        const SizedBox(height: 12),
        _buildStatCard(
          theme,
          title: '活跃倒计时',
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
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Cloud sync status
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
                    Text('云端同步', style: theme.textTheme.titleMedium),
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
                      SyncService.isInitialized ? '已连接 Supabase' : '未连接',
                      style: theme.textTheme.bodyMedium,
                    ),
                  ],
                ),
                if (SyncService.isInitialized) ...[
                  const SizedBox(height: 8),
                  Text(
                    '用户: ${Supabase.instance.client.auth.currentUser?.email ?? "未知"}',
                    style: theme.textTheme.bodySmall,
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        // Local API connection status
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.dns_outlined, size: 20, color: theme.colorScheme.primary),
                    const SizedBox(width: 8),
                    Text('本地后端', style: theme.textTheme.titleMedium),
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
                        color: provider.apiConnected ? Colors.green : Colors.grey,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      provider.apiConnected ? '已连接' : '未连接',
                      style: theme.textTheme.bodyMedium,
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text('API 地址: ${provider.apiBaseUrl}', style: theme.textTheme.bodySmall),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: () => _showApiUrlDialog(context, provider),
                  icon: const Icon(Icons.edit, size: 18),
                  label: const Text('修改 API 地址'),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        // Logout
        Card(
          child: ListTile(
            leading: const Icon(Icons.logout, color: Colors.redAccent),
            title: const Text('退出登录', style: TextStyle(color: Colors.redAccent)),
            subtitle: const Text('退出后数据保留在本地，重新登录可同步'),
            trailing: const Icon(Icons.chevron_right, color: Colors.redAccent),
            onTap: () => _showLogoutConfirm(context),
          ),
        ),
        const SizedBox(height: 16),
        // Check for updates
        Card(
          child: ListTile(
            leading: const Icon(Icons.system_update),
            title: const Text('检查更新'),
            subtitle: const Text('检查是否有新版本可用'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _checkForUpdate(context),
          ),
        ),
        const SizedBox(height: 16),
        // App info
        Card(
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.info_outline),
                title: const Text('关于 GoWorkBro'),
                subtitle: const Text('版本 1.0.0'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () {
                  showAboutDialog(
                    context: context,
                    applicationName: 'GoWorkBro',
                    applicationVersion: '1.0.0',
                    applicationLegalese: '© 2026 AzarAI',
                  );
                },
              ),
              const Divider(height: 1, indent: 16),
              ListTile(
                leading: const Icon(Icons.code),
                title: const Text('开源许可'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () {
                  showLicensePage(
                    context: context,
                    applicationName: 'GoWorkBro',
                    applicationVersion: '1.0.0',
                  );
                },
              ),
            ],
          ),
        ),
      ],
    );
  }

  void _showApiUrlDialog(BuildContext context, AppProvider provider) {
    final controller = TextEditingController(text: provider.apiBaseUrl);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('API 地址'),
        content: TextField(
          controller: controller,
          decoration: const InputDecoration(hintText: 'http://localhost:8765'),
          autofocus: true,
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
          TextButton(
            onPressed: () {
              provider.setApiBaseUrl(controller.text.trim());
              Navigator.pop(context);
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
  }

  void _showLogoutConfirm(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('退出登录'),
        content: const Text('确定退出登录？本地数据会保留，重新登录后将从云端同步。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              Supabase.instance.client.auth.signOut();
            },
            style: TextButton.styleFrom(foregroundColor: Colors.redAccent),
            child: const Text('退出'),
          ),
        ],
      ),
    );
  }

  Future<void> _checkForUpdate(BuildContext context) async {
    // Show loading
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const AlertDialog(
        content: Row(
          children: [
            CircularProgressIndicator(),
            SizedBox(width: 16),
            Text('正在检查更新...'),
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
        const SnackBar(content: Text('当前已是最新版本')),
      );
    }
  }
}
