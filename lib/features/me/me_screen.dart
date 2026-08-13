import 'dart:async';
import 'dart:io';

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/providers/app_provider.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/core/sync/sync_service.dart';
import 'package:goworkbro/core/config/supabase_config.dart';
import 'package:goworkbro/core/utils/sleep_chart_utils.dart';
import 'package:goworkbro/services/update_service.dart';
import 'package:goworkbro/core/theme/app_theme.dart';

class MeScreen extends StatefulWidget {
  const MeScreen({super.key});

  @override
  State<MeScreen> createState() => _MeScreenState();
}

class _MeScreenState extends State<MeScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  String _version = '—';

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _loadVersion();
  }

  Future<void> _loadVersion() async {
    final info = await PackageInfo.fromPlatform();
    if (mounted) setState(() => _version = info.version);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    context.watch<AppLocaleProvider>();
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

  Widget _buildProfileHeader(
    BuildContext context,
    AppProvider provider,
    ThemeData theme,
  ) {
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
              child:
                  provider.avatarPath != null &&
                      File(provider.avatarPath!).existsSync()
                  ? ClipOval(
                      child: Image.file(
                        File(provider.avatarPath!),
                        width: 72,
                        height: 72,
                        fit: BoxFit.cover,
                      ),
                    )
                  : const Icon(Icons.person, size: 36, color: Colors.white),
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
                      Icon(
                        Icons.edit,
                        size: 16,
                        color: theme.colorScheme.primary,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 4),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    'ID: ${_profileId(provider)}',
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

  String _profileId(AppProvider provider) {
    if (isSupabaseConfigured) {
      final remoteId = Supabase.instance.client.auth.currentUser?.id;
      if (remoteId != null) return remoteId;
    }
    return provider.deviceId;
  }

  // ============ Sleep / Check-in Tab ============

  Widget _buildSleepTab(
    BuildContext context,
    AppProvider provider,
    ThemeData theme,
  ) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final today = provider.todayDate;
    final todayRecord = provider.sleepRecords
        .where((r) => r.recordDate == today)
        .toList();
    final wakeTime = todayRecord.isNotEmpty ? todayRecord.first.wakeTime : null;
    final workoutTime = todayRecord.isNotEmpty
        ? todayRecord.first.workoutTime
        : null;
    final tomorrowDate = _dateKey(DateTime.now().add(const Duration(days: 1)));
    final tomorrowRecord = provider.sleepRecords
        .where((r) => r.recordDate == tomorrowDate)
        .toList();
    final sleepTime =
        todayRecord.isNotEmpty && todayRecord.first.sleepTime != null
        ? todayRecord.first.sleepTime
        : tomorrowRecord.isNotEmpty
        ? tomorrowRecord.first.sleepTime
        : null;

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
        if (provider.sleepRecords.isNotEmpty) ...[
          Text(s.sleepTrends, style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
          _SleepCharts(records: provider.sleepRecords, strings: s),
          const SizedBox(height: 16),
        ],
        // Sleep history
        Text(s.checkInHistory, style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        if (provider.sleepRecords.isEmpty)
          Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Text(
                s.noCheckInRecords,
                style: theme.textTheme.bodyMedium,
              ),
            ),
          )
        else
          ...provider.sleepRecords
              .take(10)
              .map((r) => _buildSleepRecordCard(r, theme)),
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
              Icon(
                icon,
                size: 28,
                color: hasRecord
                    ? theme.colorScheme.primary
                    : theme.colorScheme.onSurfaceVariant,
              ),
              const SizedBox(height: 8),
              Text(label, style: theme.textTheme.bodyMedium),
              const SizedBox(height: 4),
              Text(
                hasRecord
                    ? _formatTime(time)
                    : S
                          .of(context.read<AppLocaleProvider>().locale)
                          .notCheckedIn,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: hasRecord ? FontWeight.w600 : FontWeight.normal,
                  color: hasRecord
                      ? theme.colorScheme.primary
                      : theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSleepRecordCard(SleepRecord record, ThemeData theme) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
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
        title: Text(
          record.recordDate,
          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
        ),
        subtitle: Text(
          s.sleepRecordSummary(
            _formatTime(record.wakeTime),
            _formatTime(record.workoutTime),
            _formatTime(record.sleepTime),
          ),
          style: const TextStyle(fontSize: 12),
        ),
      ),
    );
  }

  void _recordTime(
    BuildContext context,
    AppProvider provider,
    String type,
  ) async {
    final now = TimeOfDay.now();
    final helpText = S
        .of(context.read<AppLocaleProvider>().locale)
        .selectCheckInTime(type);
    final picked = await showTimePicker(
      context: context,
      initialTime: now,
      helpText: helpText,
    );
    if (picked == null) return;

    final timeStr =
        '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
    final recordDate = type == 'sleep' && picked.hour >= 12
        ? _dateKey(DateTime.now().add(const Duration(days: 1)))
        : provider.todayDate;
    final existing = provider.sleepRecords
        .where((r) => r.recordDate == recordDate)
        .toList();

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
        recordDate: recordDate,
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

  String _dateKey(DateTime date) =>
      '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';

  Future<void> _showAvatarPicker(BuildContext context) async {
    final provider = context.read<AppProvider>();
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final action = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: Text(s.choosePhoto),
              onTap: () => Navigator.pop(context, 'choose'),
            ),
            if (provider.avatarPath != null)
              ListTile(
                leading: const Icon(
                  Icons.delete_outline,
                  color: Colors.redAccent,
                ),
                title: Text(
                  s.removeAvatar,
                  style: const TextStyle(color: Colors.redAccent),
                ),
                onTap: () => Navigator.pop(context, 'remove'),
              ),
          ],
        ),
      ),
    );
    if (!mounted || action == null) return;
    if (action == 'remove') {
      final old = provider.avatarPath;
      await provider.setAvatarPath(null);
      if (old != null) await File(old).delete().catchError((_) => File(old));
      return;
    }

    final picked = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      imageQuality: 88,
      maxWidth: 1024,
      maxHeight: 1024,
    );
    if (picked == null) return;
    final support = await getApplicationSupportDirectory();
    final profileDir = Directory(p.join(support.path, 'GoWorkBro', 'profile'));
    await profileDir.create(recursive: true);
    final extension = p.extension(picked.path).isEmpty
        ? '.jpg'
        : p.extension(picked.path);
    final destination = p.join(profileDir.path, 'avatar$extension');
    await File(picked.path).copy(destination);
    final oldAvatar = provider.avatarPath;
    await provider.setAvatarPath(destination);
    if (oldAvatar != null && oldAvatar != destination) {
      final oldFile = File(oldAvatar);
      if (await oldFile.exists()) await oldFile.delete();
    }
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
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(s.cancel),
          ),
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

  Widget _buildStatsTab(
    BuildContext context,
    AppProvider provider,
    ThemeData theme,
  ) {
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

  // ============ Settings Tab ============

  Widget _buildSettingsTab(
    BuildContext context,
    AppProvider provider,
    ThemeData theme,
  ) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final localeProvider = context.watch<AppLocaleProvider>();
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
                    Icon(
                      Icons.language,
                      size: 20,
                      color: theme.colorScheme.primary,
                    ),
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
                  // Keep width stable (same rationale as theme selector).
                  showSelectedIcon: false,
                  selected: {localeProvider.locale},
                  onSelectionChanged: (set) =>
                      localeProvider.setLocale(set.first),
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
                    Icon(
                      Icons.palette_outlined,
                      size: 20,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 8),
                    Text(s.theme, style: theme.textTheme.titleMedium),
                  ],
                ),
                const SizedBox(height: 12),
                SegmentedButton<ThemeMode>(
                  segments: [
                    ButtonSegment(
                      value: ThemeMode.light,
                      label: SizedBox(
                        width: 72,
                        child: Text(
                          s.lightMode,
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ),
                    ButtonSegment(
                      value: ThemeMode.dark,
                      label: SizedBox(
                        width: 72,
                        child: Text(
                          s.darkMode,
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ),
                    ButtonSegment(
                      value: ThemeMode.system,
                      label: SizedBox(
                        width: 72,
                        child: Text(
                          s.systemMode,
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ),
                  ],
                  // The default check icon widens the selected segment, and
                  // M3 stretches all segments to the widest one — which made
                  // the whole control jump when switching options.
                  showSelectedIcon: false,
                  selected: {localeProvider.themeMode},
                  onSelectionChanged: (set) =>
                      localeProvider.setThemeMode(set.first),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),

        // ---- Font selector ----
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.font_download_outlined,
                      size: 20,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 8),
                    Text(s.font, style: theme.textTheme.titleMedium),
                  ],
                ),
                const SizedBox(height: 12),
                SegmentedButton<String>(
                  segments: [
                    ButtonSegment(
                      value: AppTheme.defaultFontFamily,
                      label: SizedBox(
                        width: 80,
                        child: Text(
                          s.fontSystem,
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ),
                    const ButtonSegment(
                      value: 'LXGWWenKai',
                      label: SizedBox(
                        width: 80,
                        child: Text('霞鹜文楷', textAlign: TextAlign.center),
                      ),
                    ),
                    const ButtonSegment(
                      value: 'NotoSansSC',
                      label: SizedBox(
                        width: 80,
                        child: Text('思源黑体', textAlign: TextAlign.center),
                      ),
                    ),
                  ],
                  showSelectedIcon: false,
                  selected: {localeProvider.fontFamily},
                  onSelectionChanged: (set) =>
                      localeProvider.setFontFamily(set.first),
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
                    Icon(
                      Icons.cloud_done_outlined,
                      size: 20,
                      color: SyncService.isInitialized
                          ? Colors.green
                          : Colors.grey,
                    ),
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
                        color: SyncService.isInitialized
                            ? Colors.green
                            : Colors.grey,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      SyncService.isInitialized
                          ? s.connectedToSupabase
                          : s.notConnected,
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
            subtitle: Text(s.checkUpdateSubtitle),
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
            subtitle: Text('${s.version} $_version'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              showAboutDialog(
                context: context,
                applicationName: 'GoWorkBro',
                applicationVersion: _version,
                applicationLegalese: '© 2026 AzarAI',
                applicationIcon: Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(
                    Icons.check_circle,
                    color: Colors.white,
                    size: 28,
                  ),
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
            title: Text(
              s.logout,
              style: const TextStyle(color: Colors.redAccent),
            ),
            subtitle: Text(s.signOutSubtitle),
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
            label: Text(
              s.deleteData,
              style: const TextStyle(color: Colors.redAccent),
            ),
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
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(s.cancel),
          ),
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

  void _showDeleteDataConfirm(
    BuildContext context,
    AppProvider provider,
  ) async {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.deleteData),
        content: Text(s.deleteDataConfirm),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(s.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.redAccent),
            child: Text(s.delete),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;
    await provider.deleteAllData();
    if (!context.mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(s.deleteDataSuccess)));
  }

  Future<void> _checkForUpdate(BuildContext context) async {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    // Show loading
    unawaited(
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
      ),
    );

    final update = await UpdateService.checkForUpdate();

    if (!context.mounted) return;
    Navigator.of(context).pop(); // Close loading dialog

    if (update != null) {
      UpdateService.showUpdateDialog(context, update);
    } else {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(s.latestVersion)));
    }
  }
}

class _SleepCharts extends StatelessWidget {
  const _SleepCharts({required this.records, required this.strings});

  final List<SleepRecord> records;
  final S strings;

  @override
  Widget build(BuildContext context) {
    final byDate = {for (final record in records) record.recordDate: record};
    final today = DateTime.now();
    final dates = [
      for (var i = 6; i >= 0; i--) today.subtract(Duration(days: i)),
    ];
    final ordered = [for (final date in dates) byDate[_dateKey(date)]];

    final wake = <FlSpot>[];
    final bedtime = <FlSpot>[];
    final duration = <FlSpot>[];
    for (var i = 0; i < ordered.length; i++) {
      final record = ordered[i];
      final wakeHours = _hours(record?.wakeTime);
      final sleepHours = _hours(record?.sleepTime);
      if (wakeHours != null) wake.add(FlSpot(i.toDouble(), wakeHours));
      if (sleepHours != null) {
        bedtime.add(
          FlSpot(i.toDouble(), sleepHours < 12 ? sleepHours + 24 : sleepHours),
        );
      }
      if (wakeHours != null && sleepHours != null) {
        final value = overnightDurationHours(sleepHours, wakeHours);
        duration.add(FlSpot(i.toDouble(), value));
      }
    }

    return Column(
      children: [
        _SleepLineChart(
          title: strings.wakeTime,
          averageLabel: strings.average,
          spots: wake,
          dates: dates,
        ),
        const SizedBox(height: 12),
        _SleepLineChart(
          title: strings.bedtime,
          averageLabel: strings.average,
          spots: bedtime,
          dates: dates,
        ),
        const SizedBox(height: 12),
        _SleepLineChart(
          title: strings.sleepDuration,
          averageLabel: strings.average,
          spots: duration,
          dates: dates,
          isDuration: true,
        ),
      ],
    );
  }

  static String _dateKey(DateTime date) =>
      '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';

  static double? _hours(String? value) {
    if (value == null) return null;
    final parts = value.split(':');
    if (parts.length < 2) return null;
    final h = int.tryParse(parts[0]);
    final m = int.tryParse(parts[1]);
    if (h == null || m == null) return null;
    return h + m / 60;
  }
}

class _SleepLineChart extends StatelessWidget {
  const _SleepLineChart({
    required this.title,
    required this.averageLabel,
    required this.spots,
    required this.dates,
    this.isDuration = false,
  });

  final String title;
  final String averageLabel;
  final List<FlSpot> spots;
  final List<DateTime> dates;

  /// When true, spot values are hours (e.g. 7.5) and the tooltip shows
  /// "7h 30m"; otherwise they are clock times and show as HH:MM.
  final bool isDuration;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final average = spots.isEmpty
        ? 0.0
        : spots.fold<double>(0, (sum, spot) => sum + spot.y) / spots.length;
    final ys = spots.map((spot) => spot.y).toList();
    final minY = ys.isEmpty
        ? 0.0
        : (ys.reduce((a, b) => a < b ? a : b) - 1).clamp(0, 30);
    final maxY = ys.isEmpty
        ? 24.0
        : (ys.reduce((a, b) => a > b ? a : b) + 1).clamp(1, 30);

    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(title, style: theme.textTheme.titleSmall),
                if (spots.isNotEmpty)
                  Text(
                    '$averageLabel ${_formatHours(average)}',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: Colors.redAccent,
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 150,
              child: spots.isEmpty
                  ? Center(child: Text('—', style: theme.textTheme.bodyLarge))
                  : LineChart(
                      LineChartData(
                        minX: 0,
                        maxX: 6,
                        minY: minY.toDouble(),
                        maxY: maxY.toDouble(),
                        gridData: const FlGridData(show: false),
                        borderData: FlBorderData(show: false),
                        lineTouchData: LineTouchData(
                          enabled: true,
                          touchTooltipData: LineTouchTooltipData(
                            // Keep the bubble above the dot (fl_chart
                            // default) and force it to stay inside the
                            // chart so it never overlaps the card header.
                            tooltipMargin: 20,
                            fitInsideVertically: true,
                            fitInsideHorizontally: true,
                            getTooltipColor: (spot) =>
                                theme.colorScheme.inverseSurface,
                            getTooltipItems: (touchedSpots) => [
                              for (final spot in touchedSpots)
                                LineTooltipItem(
                                  isDuration
                                      ? _formatDuration(spot.y)
                                      : _formatHours(spot.y),
                                  TextStyle(
                                    color: theme
                                        .colorScheme
                                        .onInverseSurface,
                                    fontWeight: FontWeight.w600,
                                    fontSize: 12,
                                  ),
                                ),
                            ],
                          ),
                        ),
                        extraLinesData: ExtraLinesData(
                          horizontalLines: [
                            HorizontalLine(
                              y: average,
                              color: Colors.redAccent,
                              strokeWidth: 1.5,
                              dashArray: [6, 4],
                            ),
                          ],
                        ),
                        titlesData: FlTitlesData(
                          topTitles: const AxisTitles(
                            sideTitles: SideTitles(showTitles: false),
                          ),
                          rightTitles: const AxisTitles(
                            sideTitles: SideTitles(showTitles: false),
                          ),
                          leftTitles: AxisTitles(
                            sideTitles: SideTitles(
                              showTitles: true,
                              reservedSize: 38,
                              getTitlesWidget: (value, meta) => Text(
                                _formatHours(value),
                                style: theme.textTheme.bodySmall?.copyWith(
                                  fontSize: 9,
                                ),
                              ),
                            ),
                          ),
                          bottomTitles: AxisTitles(
                            sideTitles: SideTitles(
                              showTitles: true,
                              interval: 1,
                              getTitlesWidget: (value, meta) {
                                final index = value.round();
                                if (index < 0 || index >= dates.length) {
                                  return const SizedBox.shrink();
                                }
                                final date = dates[index];
                                return Padding(
                                  padding: const EdgeInsets.only(top: 4),
                                  child: Text(
                                    '${date.month}/${date.day}',
                                    style: theme.textTheme.bodySmall?.copyWith(
                                      fontSize: 9,
                                    ),
                                  ),
                                );
                              },
                            ),
                          ),
                        ),
                        lineBarsData: [
                          for (final run in splitContiguousSleepSpots(spots))
                            LineChartBarData(
                              spots: run,
                              isCurved: true,
                              color: const Color(0xFFFF8A3D),
                              barWidth: 3,
                              dotData: const FlDotData(show: true),
                              belowBarData: BarAreaData(
                                show: true,
                                color: const Color(
                                  0xFFFF8A3D,
                                ).withValues(alpha: 0.12),
                              ),
                            ),
                        ],
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  static String _formatHours(double value) {
    final normalized = value >= 24 ? value - 24 : value;
    final hours = normalized.floor();
    final minutes = ((normalized - hours) * 60).round();
    return '${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}';
  }

  /// Format a duration in hours (e.g. 7.5) as "7h 30m".
  static String _formatDuration(double value) {
    final totalMinutes = (value * 60).round();
    final hours = totalMinutes ~/ 60;
    final minutes = totalMinutes % 60;
    if (hours == 0) return '${minutes}m';
    if (minutes == 0) return '${hours}h';
    return '${hours}h ${minutes}m';
  }
}
