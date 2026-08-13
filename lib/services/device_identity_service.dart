import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:uuid/uuid.dart';

import 'database_service.dart';

/// Resolves a stable, privacy-preserving identifier for offline profiles.
/// Platform IDs are persisted on first use so upgrades never change identity.
class DeviceIdentityService {
  static Future<String> getOrCreateDeviceId() async {
    final saved = await DatabaseService.getSetting('offline_device_id');
    if (saved != null && saved.isNotEmpty) {
      if (saved.startsWith('GWB-')) return saved;
      return _persistDerivedId(saved);
    }

    String? id;
    final info = DeviceInfoPlugin();
    try {
      if (Platform.isWindows) {
        id = (await info.windowsInfo).deviceId;
      } else if (Platform.isAndroid) {
        id = (await info.androidInfo).id;
      } else if (Platform.isIOS) {
        id = (await info.iosInfo).identifierForVendor;
      } else if (Platform.isLinux) {
        id = (await info.linuxInfo).machineId;
      } else if (Platform.isMacOS) {
        id = (await info.macOsInfo).systemGUID;
      }
    } catch (_) {
      // Fall through to a persisted UUID when platform APIs are unavailable.
    }

    final source = (id == null || id.trim().isEmpty)
        ? const Uuid().v4()
        : id.trim();
    return _persistDerivedId(source);
  }

  static Future<String> _persistDerivedId(String source) async {
    final digest = sha256.convert(
      utf8.encode('GoWorkBro/offline-device/$source'),
    );
    final displayId = 'GWB-${digest.toString().substring(0, 12).toUpperCase()}';
    await DatabaseService.setSetting('offline_device_id', displayId);
    return displayId;
  }
}
