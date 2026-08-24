import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart' as file_picker;
import 'package:file_selector/file_selector.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:provider/provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'package:goworkbro/core/export/data_export_service.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/core/sync/sync_service.dart';
import 'package:goworkbro/core/theme/app_theme.dart';
import 'package:goworkbro/providers/app_provider.dart';
import 'package:goworkbro/services/update_service.dart';

/// Settings tab: language/theme/font, late-night mode, sync status,
/// export, updates, logout and data deletion.
class SettingsTab extends StatefulWidget {
  const SettingsTab({super.key});

  @override
  State<SettingsTab> createState() => _SettingsTabState();
}

class _SettingsTabState extends State<SettingsTab> {
  String _version = '—';
  bool _isExporting = false;

  @override
  void initState() {
    super.initState();
    _loadVersion();
  }

  Future<void> _loadVersion() async {
    final info = await PackageInfo.fromPlatform();
    if (mounted) setState(() => _version = info.version);
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    final theme = Theme.of(context);
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final localeProvider = context.watch<AppLocaleProvider>();
    return ListView(
      key: const Key('me-settings-list'),
      padding: const EdgeInsets.all(16),
      children: [
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
    Navigator.of(context).pop();

    if (update != null) {
      UpdateService.showUpdateDialog(context, update);
    } else {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(s.latestVersion)));
    }
  }
}
