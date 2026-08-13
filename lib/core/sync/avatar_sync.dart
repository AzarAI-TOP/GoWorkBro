import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

/// Avatar file sync via Supabase Storage.
///
/// The cloud `user_settings.avatar_path` value holds a **storage object
/// path** (`<uid>/avatar.jpg` inside the `avatars` bucket) — never a local
/// file path, because a Windows path is meaningless on the phone and vice
/// versa. The local file cache lives under the app support directory and is
/// keyed as `avatar_local_path` (device-local, never synced).
/// What a pulled `user_settings.avatar_path` value should do locally.
enum AvatarApplyAction {
  /// Cloud removed the avatar (null value) — clear the local cache.
  clear,

  /// Legacy device-local path pushed by an old client — meaningless here
  /// but harmless: keep the local avatar untouched (issue #12).
  ignore,

  /// Valid Storage object path — download and apply it.
  apply,
}

abstract final class AvatarSync {
  static const String bucket = 'avatars';

  static String storagePathFor(String uid, String localPath) {
    final ext = p.extension(localPath);
    return '$uid/avatar${ext.isEmpty ? '.jpg' : ext}';
  }

  /// True when [value] looks like a storage object path (`<uuid>/avatar.ext`),
  /// as opposed to a legacy local file path stored by older versions.
  static bool isStoragePath(String? value) {
    if (value == null || value.isEmpty) return false;
    return RegExp(r'^[0-9a-fA-F-]{36}/avatar\.\w+$').hasMatch(value);
  }

  /// Decides how a pulled `avatar_path` value lands on this device.
  ///
  /// Only a Storage object path is applied. Legacy local paths from v1.1.0
  /// clients are ignored instead of wiping the local avatar — the startup
  /// push migrates/self-heals the row.
  static AvatarApplyAction applyDecision(String? value) {
    if (value == null) return AvatarApplyAction.clear;
    if (isStoragePath(value)) return AvatarApplyAction.apply;
    return AvatarApplyAction.ignore;
  }

  static String _contentType(String path) =>
      p.extension(path).toLowerCase() == '.png' ? 'image/png' : 'image/jpeg';

  /// Upload the local avatar file to Storage and return the object path.
  /// Throws when the bucket is missing or the file cannot be read — callers
  /// degrade to a device-local avatar.
  static Future<String> upload(
    SupabaseClient client,
    String uid,
    String localPath,
  ) async {
    final bytes = await File(localPath).readAsBytes();
    final storagePath = storagePathFor(uid, localPath);
    await client.storage.from(bucket).uploadBinary(
          storagePath,
          bytes,
          fileOptions: FileOptions(
            upsert: true,
            contentType: _contentType(storagePath),
          ),
        );
    return storagePath;
  }

  /// Download the avatar object into the local profile cache directory and
  /// return the local file path. Throws on network/storage errors.
  static Future<String> download(
    SupabaseClient client,
    String storagePath,
  ) async {
    final bytes = await client.storage.from(bucket).download(storagePath);
    final support = await getApplicationSupportDirectory();
    final profileDir = Directory(p.join(support.path, 'GoWorkBro', 'profile'));
    await profileDir.create(recursive: true);
    final dest = p.join(profileDir.path, p.basename(storagePath));
    await File(dest).writeAsBytes(bytes, flush: true);
    return dest;
  }

  /// Remove the avatar object from Storage (best-effort).
  static Future<void> remove(SupabaseClient client, String storagePath) async {
    try {
      await client.storage.from(bucket).remove([storagePath]);
    } catch (e) {
      debugPrint('Avatar storage remove failed: $e');
    }
  }
}
