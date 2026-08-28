import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'package:goworkbro/app/app.dart';
import 'package:goworkbro/core/config/supabase_config.dart';
import 'package:goworkbro/core/database/app_database.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';

/// End-to-end test: login → pull data from Supabase.
///
/// Pre-conditions (all via --dart-define, otherwise the whole suite skips):
///   - SUPABASE_URL / SUPABASE_ANON_KEY — the target project
///   - TEST_EMAIL / TEST_PASSWORD — an existing user
///   - A TODO titled "Synced from Supabase" seeded in Supabase for this user
///
/// Safety: the database is redirected to a throwaway directory BEFORE the
/// app boots, so this test can never touch the real Documents/goworkbro.db.
///
/// Run:
///   flutter test integration_test/e2e_sync_test.dart -d windows \
///     --dart-define=SUPABASE_URL=... --dart-define=SUPABASE_ANON_KEY=... \
///     --dart-define=TEST_EMAIL=... --dart-define=TEST_PASSWORD=...
void main() {
  final testEmail = const String.fromEnvironment('TEST_EMAIL');
  final testPassword = const String.fromEnvironment('TEST_PASSWORD');
  final credentialsProvided =
      isSupabaseConfigured && testEmail.isNotEmpty && testPassword.isNotEmpty;

  if (!credentialsProvided) {
    print(
      'SKIP: e2e sync test requires SUPABASE_URL/SUPABASE_ANON_KEY and '
      'TEST_EMAIL/TEST_PASSWORD dart-defines.',
    );
    return;
  }

  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    final tempDir = await Directory.systemTemp.createTemp('goworkbro_e2e_');
    AppDatabase.setDataDirForTesting(tempDir.path);
    await Supabase.initialize(
      url: supabaseUrl,
      publishableKey: supabaseAnonKey,
    );
  });

  testWidgets('Login and pull synced data from Supabase', (tester) async {
    final localeProvider = AppLocaleProvider();
    await localeProvider.init();
    await tester.pumpWidget(GoWorkBroApp(localeProvider: localeProvider));
    await tester.pump(const Duration(seconds: 2));

    // Login if the auth screen is showing (a persisted session from an
    // earlier run would skip straight to the app).
    final authPrompt = find.text('登录你的账号，同步跨设备数据');
    expect(authPrompt, findsAny, reason: 'Expected the auth screen first');

    await tester.enterText(find.byType(TextField).at(0), testEmail);
    await tester.enterText(find.byType(TextField).at(1), testPassword);
    await tester.tap(find.widgetWithText(FilledButton, '登录'));

    // Auth + initial pull are real network calls; pump in slices instead of
    // pumpAndSettle (the countdown screen's 1s ticker would never settle).
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(seconds: 2));
    }

    // ASSERT: the main app is visible after login.
    expect(
      find.text('待办'),
      findsAtLeast(1),
      reason: 'Main app did not load after login',
    );

    // ASSERT: the seeded cloud todo arrived through the sync pipeline.
    expect(
      find.text('Synced from Supabase'),
      findsAtLeast(1),
      reason: 'Cloud todo was not pulled from Supabase',
    );
  });
}
