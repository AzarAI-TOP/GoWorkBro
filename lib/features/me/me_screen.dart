import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart' as file_picker;
import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:image_picker_android/image_picker_android.dart';
import 'package:image_picker_platform_interface/image_picker_platform_interface.dart';
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
import 'package:goworkbro/core/export/data_export_service.dart';
import 'package:goworkbro/services/update_service.dart';
import 'package:goworkbro/core/theme/app_theme.dart';
import 'package:goworkbro/core/utils/date_utils.dart';
import 'package:goworkbro/features/me/widgets/sleep_charts_section.dart';
import 'package:goworkbro/features/me/widgets/workout_checkin_sheet.dart';

class MeScreen extends StatefulWidget {
  const MeScreen({super.key});

  @override
  State<MeScreen> createState() => _MeScreenState();
}

class _MeScreenState extends State<MeScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  String _version = '—';
  bool _isExporting = false;

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
            key: const ValueKey('profile_avatar'),
            onTap: () => _showAvatarDialog(context),
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
    SleepRecord? recordFor(String date) => provider.sleepRecords
        .where((record) => record.recordDate == date)
        .firstOrNull;

    final wakeRecord = recordFor(provider.calendarDate);
    final workoutRecord = recordFor(provider.todayDate);
    final sleepRecord = recordFor(sleepRecordDateKey(DateTime.now()));
    final calendarSleepRecord = recordFor(provider.calendarDate);
    final wakeTime = wakeRecord?.wakeTime;
    final workoutValue = workoutRecord == null
        ? null
        : workoutRecord.workoutDurationMinutes != null
        ? s.minutes(workoutRecord.workoutDurationMinutes!)
        : workoutRecord.workoutTime != null
        ? s.legacyWorkoutRecord
        : null;
    final sleepTime = sleepRecord?.sleepTime ?? calendarSleepRecord?.sleepTime;

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
                        value: wakeTime,
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
                        value: workoutValue,
                        onTap: () => _recordWorkout(context, provider),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _buildCheckInButton(
                        context,
                        theme,
                        label: s.sleep,
                        icon: Icons.bedtime_outlined,
                        value: sleepTime,
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
          SleepChartsSection(records: provider.sleepRecords, strings: s),
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
    required String? value,
    required VoidCallback onTap,
  }) {
    final hasRecord = value != null;
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
                    ? value
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
    final workoutSummary = record.workoutDurationMinutes != null
        ? s.workoutRecordSummary(record.workoutDurationMinutes!, record.note)
        : record.workoutTime != null
        ? s.legacyWorkoutAt(record.workoutTime!)
        : '—';
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
            workoutSummary,
            _formatTime(record.sleepTime),
          ),
          style: const TextStyle(fontSize: 12),
        ),
      ),
    );
  }

  Future<void> _recordWorkout(
    BuildContext context,
    AppProvider provider,
  ) async {
    final initialRecordDate = provider.todayDate;
    final initialRecord = provider.sleepRecords
        .where((record) => record.recordDate == initialRecordDate)
        .firstOrNull;
    final result = await showModalBottomSheet<WorkoutCheckInResult>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => WorkoutCheckInSheet(
        initialDurationMinutes: initialRecord?.workoutDurationMinutes,
        initialDescription: initialRecord?.note,
        onSave: (value) => Navigator.pop(sheetContext, value),
      ),
    );
    if (result == null || !mounted) return;

    // Resolve the logical date at save time, then patch only workout-owned
    // columns so a rollover or remote sleep/wake update while the sheet was
    // open cannot be overwritten by the stale opening snapshot.
    await provider.recordWorkout(
      recordDate: provider.todayDate,
      durationMinutes: result.durationMinutes,
      description: result.description,
    );
  }

  void _recordTime(
    BuildContext context,
    AppProvider provider,
    String type,
  ) async {
    final pickerOpenedAt = DateTime.now();
    final helpText = S
        .of(context.read<AppLocaleProvider>().locale)
        .selectCheckInTime(type);
    final picked = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(pickerOpenedAt),
      helpText: helpText,
    );
    if (picked == null) return;

    final timeStr =
        '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
    final selectedAt = resolveCheckInDateTime(
      now: DateTime.now(),
      hour: picked.hour,
      minute: picked.minute,
    );
    final recordDate = switch (type) {
      'sleep' => sleepRecordDateKey(selectedAt),
      'wake' => wakeRecordDateKey(selectedAt),
      _ => provider.todayDate,
    };
    final existing = provider.sleepRecords
        .where((r) => r.recordDate == recordDate)
        .toList();

    SleepRecord record;
    if (existing.isNotEmpty) {
      switch (type) {
        case 'wake':
          record = existing.first.copyWith(wakeTime: timeStr);
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
        sleepTime: type == 'sleep' ? timeStr : null,
      );
    }
    await provider.recordSleep(
      record,
      closesLogicalDayThrough: type == 'sleep' ? recordDate : null,
    );
  }

  String _formatTime(String? time) {
    return time ?? '—';
  }

  Future<void> _showAvatarDialog(BuildContext context) async {
    final provider = context.read<AppProvider>();
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final theme = Theme.of(context);
    final avatar = provider.avatarPath;
    final hasAvatar = avatar != null && File(avatar).existsSync();

    final change = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 140,
              height: 140,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  colors: [
                    theme.colorScheme.primary,
                    theme.colorScheme.secondary,
                  ],
                ),
              ),
              child: hasAvatar
                  ? ClipOval(
                      child: Image.file(
                        File(avatar),
                        width: 140,
                        height: 140,
                        fit: BoxFit.cover,
                      ),
                    )
                  : const Icon(Icons.person, size: 64, color: Colors.white),
            ),
            const SizedBox(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                FilledButton(
                  onPressed: () => Navigator.pop(dialogContext, true),
                  child: Text(s.changeAvatar),
                ),
                const SizedBox(width: 12),
                TextButton(
                  onPressed: () => Navigator.pop(dialogContext, false),
                  child: Text(s.cancel),
                ),
              ],
            ),
          ],
        ),
      ),
    );
    if (change != true || !mounted) return;
    await _pickAndSetAvatar();
  }

  Future<void> _pickAndSetAvatar() async {
    final provider = context.read<AppProvider>();
    // Android 13+: prefer the system Photo Picker (full Photos/Albums view).
    // The default (false) uses the legacy GET_CONTENT dialog, which on many
    // devices only surfaces recent photos (issue #12). No-op on desktop.
    final platform = ImagePickerPlatform.instance;
    if (platform is ImagePickerAndroid) {
      platform.useAndroidPhotoPicker = true;
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
      key: const Key('me-settings-list'),
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

        // ---- Late-night logical day ----
        Card(
          child: SwitchListTile(
            key: const Key('late-night-mode-switch'),
            secondary: Icon(
              Icons.nightlight_round,
              color: provider.lateNightModeEnabled
                  ? theme.colorScheme.primary
                  : theme.colorScheme.onSurfaceVariant,
            ),
            title: Text(s.lateNightMode),
            subtitle: Text(
              provider.lateNightModeEnabled
                  ? s.lateNightModeActive(provider.todayDate)
                  : s.lateNightModeDescription,
            ),
            value: provider.lateNightModeEnabled,
            onChanged: provider.setLateNightModeEnabled,
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

        // ---- Portable data export ----
        Card(
          child: ListTile(
            key: const Key('export-all-data'),
            leading: const Icon(Icons.download_outlined),
            title: Text(s.exportAllData),
            subtitle: Text(s.exportAllDataSubtitle),
            trailing: _isExporting
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.chevron_right),
            onTap: _isExporting ? null : () => _exportAllData(context),
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

  Future<void> _exportAllData(BuildContext context) async {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    setState(() => _isExporting = true);
    try {
      final saved = await DataExportService.exportWith(
        saver: ({required suggestedName, required content}) async {
          final bytes = Uint8List.fromList(utf8.encode(content));
          if (Platform.isAndroid) {
            final path = await file_picker.FilePicker.saveFile(
              dialogTitle: s.exportAllData,
              fileName: suggestedName,
              type: file_picker.FileType.custom,
              allowedExtensions: const ['json'],
              bytes: bytes,
            );
            return path != null;
          }

          final location = await getSaveLocation(
            suggestedName: suggestedName,
            acceptedTypeGroups: const [
              XTypeGroup(
                label: 'JSON',
                extensions: ['json'],
                mimeTypes: ['application/json'],
              ),
            ],
          );
          if (location == null) return false;
          final file = XFile.fromData(
            bytes,
            mimeType: 'application/json',
            name: suggestedName,
          );
          await file.saveTo(location.path);
          return true;
        },
      );
      if (saved && mounted) {
        ScaffoldMessenger.of(
          this.context,
        ).showSnackBar(SnackBar(content: Text(s.exportAllDataSuccess)));
      }
    } catch (error) {
      debugPrint('Data export failed: $error');
      if (mounted) {
        ScaffoldMessenger.of(
          this.context,
        ).showSnackBar(SnackBar(content: Text(s.exportAllDataFailed)));
      }
    } finally {
      if (mounted) setState(() => _isExporting = false);
    }
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
