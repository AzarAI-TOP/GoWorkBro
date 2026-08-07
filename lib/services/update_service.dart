import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:url_launcher/url_launcher.dart';
import 'package:package_info_plus/package_info_plus.dart';

/// Checks for app updates by comparing the current version with the latest
/// GitHub release. On Windows, can download and launch the MSI installer.
class UpdateService {
  static const String _owner = 'AzarAI-TOP';
  static const String _repo = 'GoWorkBro';

  /// Check for updates. Returns null if up-to-date, or an [UpdateInfo] if
  /// a newer version is available.
  static Future<UpdateInfo?> checkForUpdate() async {
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      final currentVersion = packageInfo.version; // e.g. "1.0.0"

      final res = await http.get(
        Uri.parse('https://api.github.com/repos/$_owner/$_repo/releases/latest'),
        headers: {
          'Accept': 'application/vnd.github.v3+json',
          // Use gh auth token if available to avoid rate limits
          if (Platform.environment.containsKey('GH_TOKEN'))
            'Authorization': 'Bearer ${Platform.environment['GH_TOKEN']}',
        },
      ).timeout(const Duration(seconds: 10));

      if (res.statusCode != 200) return null;

      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final latestTag = (data['tag_name'] as String?)?.replaceAll('v', '') ?? '';
      final releaseUrl = data['html_url'] as String? ?? '';
      final releaseNotes = data['body'] as String? ?? '';

      // Find download URLs for assets
      String? msiUrl;
      String? apkUrl;
      final assets = data['assets'] as List? ?? [];
      for (final asset in assets) {
        final name = (asset['name'] as String?) ?? '';
        final url = asset['browser_download_url'] as String?;
        if (name.endsWith('.msi')) msiUrl = url;
        if (name.endsWith('.apk')) apkUrl = url;
      }

      if (_compareVersions(latestTag, currentVersion) > 0) {
        return UpdateInfo(
          currentVersion: currentVersion,
          latestVersion: latestTag,
          releaseUrl: releaseUrl,
          releaseNotes: releaseNotes,
          msiUrl: msiUrl,
          apkUrl: apkUrl,
        );
      }
      return null;
    } catch (e) {
      debugPrint('Update check failed: $e');
      return null;
    }
  }

  /// Compare two version strings (e.g. "0.1.0" vs "0.2.0").
  /// Returns >0 if a > b, 0 if equal, <0 if a < b.
  static int _compareVersions(String a, String b) {
    final partsA = a.split('.').map((e) => int.tryParse(e) ?? 0).toList();
    final partsB = b.split('.').map((e) => int.tryParse(e) ?? 0).toList();
    for (var i = 0; i < 3; i++) {
      final va = i < partsA.length ? partsA[i] : 0;
      final vb = i < partsB.length ? partsB[i] : 0;
      if (va != vb) return va.compareTo(vb);
    }
    return 0;
  }

  /// Download and launch the MSI installer (Windows only).
  static Future<void> downloadAndInstall(BuildContext context, String msiUrl) async {
    if (!Platform.isWindows) {
      // On non-Windows, just open the release page
      await launchUrl(Uri.parse(msiUrl));
      return;
    }

    try {
      // Show progress dialog
      showDialog(
        // ignore: use_build_context_synchronously
        context: context,
        barrierDismissible: false,
        builder: (context) => const _DownloadDialog(),
      );

      // Download to temp
      final res = await http.get(Uri.parse(msiUrl));
      final tempDir = Directory.systemTemp;
      final filePath = '${tempDir.path}\\GoWorkBro-Update.msi';
      final file = File(filePath);
      await file.writeAsBytes(res.bodyBytes);

      // Close dialog
      // ignore: use_build_context_synchronously
      Navigator.of(context).pop();

      // Launch the MSI installer
      await Process.start('msiexec', ['/i', filePath], mode: ProcessStartMode.detached);
    } catch (e) {
      // ignore: use_build_context_synchronously
      if (Navigator.of(context).canPop()) Navigator.of(context).pop();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('下载失败: $e')),
      );
    }
  }

  /// Show update available dialog.
  static void showUpdateDialog(BuildContext context, UpdateInfo info) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('发现新版本'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('当前版本: v${info.currentVersion}'),
            Text('最新版本: v${info.latestVersion}'),
            if (info.releaseNotes.isNotEmpty) ...[
              const SizedBox(height: 12),
              const Text('更新内容:', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 4),
              Flexible(
                child: SingleChildScrollView(
                  child: Text(info.releaseNotes),
                ),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('稍后再说'),
          ),
          if (info.msiUrl != null)
            FilledButton(
              onPressed: () {
                Navigator.pop(context);
                downloadAndInstall(context, info.msiUrl!);
              },
              child: const Text('下载并安装'),
            )
          else
            FilledButton(
              onPressed: () {
                Navigator.pop(context);
                launchUrl(Uri.parse(info.releaseUrl));
              },
              child: const Text('前往下载'),
            ),
        ],
      ),
    );
  }
}

class UpdateInfo {
  final String currentVersion;
  final String latestVersion;
  final String releaseUrl;
  final String releaseNotes;
  final String? msiUrl;
  final String? apkUrl;

  UpdateInfo({
    required this.currentVersion,
    required this.latestVersion,
    required this.releaseUrl,
    required this.releaseNotes,
    this.msiUrl,
    this.apkUrl,
  });
}

class _DownloadDialog extends StatelessWidget {
  const _DownloadDialog();

  @override
  Widget build(BuildContext context) {
    return const AlertDialog(
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          CircularProgressIndicator(),
          SizedBox(height: 16),
          Text('正在下载更新...'),
        ],
      ),
    );
  }
}
