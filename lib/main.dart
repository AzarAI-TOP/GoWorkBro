import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:window_manager/window_manager.dart';

import 'package:goworkbro/core/config/supabase_config.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/services/error_log_service.dart';
import 'package:goworkbro/services/tray_service.dart';
import 'package:goworkbro/app/app.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Uncaught errors must leave a trace on disk — release builds otherwise
  // swallow them silently.
  ErrorLogService.install();

  // Must be initialized before window_manager on Windows
  if (!kIsWeb && defaultTargetPlatform == TargetPlatform.windows) {
    await windowManager.ensureInitialized();
    await windowManager.waitUntilReadyToShow(
      const WindowOptions(
        size: Size(1200, 800),
        minimumSize: Size(400, 600),
        title: 'GoWorkBro',
        titleBarStyle: TitleBarStyle.normal,
        center: true,
      ),
      () async {
        await windowManager.show();
        await windowManager.focus();
      },
    );
    // Prevent default close — we hide to tray instead
    await windowManager.setPreventClose(true);
    await TrayService().init();
  }

  // Initialize Supabase before running app (if configured)
  if (isSupabaseConfigured) {
    await Supabase.initialize(
      url: supabaseUrl,
      publishableKey: supabaseAnonKey,
      debug: kDebugMode,
    );
  }

  // Load persisted locale/theme/font before first paint
  final localeProvider = AppLocaleProvider();
  await localeProvider.init();

  runApp(GoWorkBroApp(localeProvider: localeProvider));
}
