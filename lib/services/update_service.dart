import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:url_launcher/url_launcher.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:provider/provider.dart';

import 'package:goworkbro/core/l10n/app_locale.dart';

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
      final currentVersion = packageInfo.version;

      final res = await http
          .get(
            Uri.parse(
              'https://api.github.com/repos/$_owner/$_repo/releases/latest',
            ),
            headers: {
              'Accept': 'application/vnd.github.v3+json',
              // Use gh auth token if available to avoid rate limits
              if (Platform.environment.containsKey('GH_TOKEN'))
                'Authorization': 'Bearer ${Platform.environment['GH_TOKEN']}',
            },
          )
          .timeout(const Duration(seconds: 10));

      if (res.statusCode != 200) return null;

      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final latestTag =
          (data['tag_name'] as String?)?.replaceAll('v', '') ?? '';
      final releaseUrl = data['html_url'] as String? ?? '';
      final releaseNotes = data['body'] as String? ?? '';

      // Find download URLs for assets
      String? msiUrl;
      String? apkUrl;
      final assets = data['assets'] as List? ?? [];
      for (final asset in assets) {
        final assetMap = asset as Map<String, dynamic>;
        final name = (assetMap['name'] as String?) ?? '';
        final url = assetMap['browser_download_url'] as String?;
        if (name.endsWith('.msi') ||
            (name.endsWith('.exe') && name.contains('Setup'))) {
          msiUrl = url;
        }
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
  ///
  /// Integrity: every release ships a SHA-256 sidecar (`<asset>.sha256`)
  /// next to the installer; the download is streamed to disk while hashing
  /// and refused unless it matches. A release without a sidecar (legacy or
  /// tampered) is rejected — an unverified executable must never run.
  static Future<void> downloadAndInstall(
    BuildContext context,
    String msiUrl,
  ) async {
    if (!Platform.isWindows) {
      // On non-Windows, just open the release page
      await launchUrl(Uri.parse(msiUrl));
      return;
    }

    try {
      // Show progress dialog
      unawaited(
        showDialog(
          context: context,
          barrierDismissible: false,
          builder: (context) => const _DownloadDialog(),
        ),
      );

      final expectedDigest = await _fetchSha256Sidecar(msiUrl);

      final tempDir = Directory.systemTemp;
      final isExe = Uri.parse(msiUrl).path.toLowerCase().endsWith('.exe');
      final filePath =
          '${tempDir.path}\\GoWorkBro-Update.${isExe ? 'exe' : 'msi'}';
      final file = File(filePath);

      // Stream to disk (no full installer in memory) while hashing.
      final client = http.Client();
      try {
        final response = await client
            .send(http.Request('GET', Uri.parse(msiUrl)))
            .timeout(const Duration(seconds: 30));
        if (response.statusCode != 200) {
          throw Exception('HTTP ${response.statusCode}');
        }
        final digestSink = _DigestCollector();
        final hashInput = sha256.startChunkedConversion(digestSink);
        final sink = file.openWrite();
        try {
          await for (final chunk in response.stream) {
            sink.add(chunk);
            hashInput.add(chunk);
          }
          await sink.flush();
        } finally {
          await sink.close();
          hashInput.close();
        }
        final actualDigest = digestSink.value.toString();
        if (actualDigest != expectedDigest) {
          await file.delete();
          throw Exception('SHA-256 mismatch');
        }
      } finally {
        client.close();
      }

      if (!context.mounted) return;
      // Close dialog
      Navigator.of(context).pop();

      // Launch the verified installer
      if (isExe) {
        await Process.start(filePath, const [], mode: ProcessStartMode.detached);
      } else {
        await Process.start(
          'msiexec',
          ['/i', filePath],
          mode: ProcessStartMode.detached,
        );
      }
    } catch (e) {
      if (!context.mounted) return;
      final s = S.of(context.read<AppLocaleProvider>().locale);
      if (Navigator.of(context).canPop()) Navigator.of(context).pop();
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(s.downloadFailed(e))));
    }
  }

  /// Fetches the `<assetUrl>.sha256` sidecar published with each release and
  /// validates its shape. Missing/malformed sidecar = refused download.
  static Future<String> _fetchSha256Sidecar(String assetUrl) async {
    final res = await http
        .get(Uri.parse('$assetUrl.sha256'))
        .timeout(const Duration(seconds: 15));
    if (res.statusCode != 200) {
      throw Exception('SHA-256 checksum file missing (HTTP ${res.statusCode})');
    }
    final digest = res.body.trim().split(RegExp(r'\s+')).first.toLowerCase();
    if (!RegExp(r'^[0-9a-f]{64}$').hasMatch(digest)) {
      throw Exception('SHA-256 checksum file malformed');
    }
    return digest;
  }

  /// Show update available dialog.
  static void showUpdateDialog(BuildContext context, UpdateInfo info) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(s.newVersionAvailable),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(s.currentVersion(info.currentVersion)),
            Text(s.latestVersionValue(info.latestVersion)),
            if (info.releaseNotes.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text(
                s.releaseNotes,
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 4),
              Flexible(
                child: SingleChildScrollView(child: Text(info.releaseNotes)),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(s.later),
          ),
          if (info.msiUrl != null)
            FilledButton(
              onPressed: () {
                Navigator.pop(context);
                downloadAndInstall(context, info.msiUrl!);
              },
              child: Text(s.downloadAndInstall),
            )
          else
            FilledButton(
              onPressed: () {
                Navigator.pop(context);
                launchUrl(Uri.parse(info.releaseUrl));
              },
              child: Text(s.goToDownload),
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

/// Captures the single [Digest] emitted by a chunked hash conversion.
class _DigestCollector implements Sink<Digest> {
  Digest? value;

  @override
  void add(Digest data) => value = data;

  @override
  void close() {}
}

class _DownloadDialog extends StatelessWidget {
  const _DownloadDialog();

  @override
  Widget build(BuildContext context) {
    final s = S.of(context.watch<AppLocaleProvider>().locale);
    return AlertDialog(
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const CircularProgressIndicator(),
          const SizedBox(height: 16),
          Text(s.downloadingUpdate),
        ],
      ),
    );
  }
}
