import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

/// Last-resort error sink: appends uncaught framework and platform errors to
/// a small rolling file so release builds leave *something* to debug from.
///
/// Purely best-effort — a failing log write must never mask the original
/// error, so every failure is swallowed.
abstract final class ErrorLogService {
  static const _maxLogBytes = 512 * 1024;
  static File? _logFile;

  /// Installs the global handlers. Call once from main().
  static void install() {
    FlutterError.onError = (details) {
      FlutterError.presentError(details);
      write(details.toString());
    };
    PlatformDispatcher.instance.onError = (error, stack) {
      write('$error\n$stack');
      return true;
    };
  }

  static void write(String message) {
    unawaited(_writeInternal(message));
  }

  static Future<void> _writeInternal(String message) async {
    try {
      final log = await _resolveLogFile();
      if (await log.length() > _maxLogBytes) {
        final rotated = File('${log.path}.old');
        if (await rotated.exists()) await rotated.delete();
        await log.rename(rotated.path);
      }
      final stamp = DateTime.now().toIso8601String();
      await log.writeAsString(
        '[$stamp]\n$message\n\n',
        mode: FileMode.append,
      );
    } catch (_) {
      // Nowhere left to report a failure of the reporter itself.
    }
  }

  static Future<File> _resolveLogFile() async {
    final cached = _logFile;
    if (cached != null) return cached;
    final dir = await getApplicationDocumentsDirectory();
    final logsDir = Directory(p.join(dir.path, 'logs'));
    if (!await logsDir.exists()) await logsDir.create(recursive: true);
    return _logFile = File(p.join(logsDir.path, 'error.log'));
  }
}
