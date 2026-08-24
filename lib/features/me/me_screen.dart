import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:image_picker_android/image_picker_android.dart';
import 'package:image_picker_platform_interface/image_picker_platform_interface.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'package:goworkbro/core/config/supabase_config.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/features/me/widgets/settings_tab.dart';
import 'package:goworkbro/features/me/widgets/sleep_tab.dart';
import 'package:goworkbro/features/me/widgets/stats_tab.dart';
import 'package:goworkbro/providers/app_provider.dart';

class MeScreen extends StatefulWidget {
  const MeScreen({super.key});

  @override
  State<MeScreen> createState() => _MeScreenState();
}

class _MeScreenState extends State<MeScreen>
    with SingleTickerProviderStateMixin {
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
              children: const [
                SleepTab(),
                StatsTab(),
                SettingsTab(),
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
}
