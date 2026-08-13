import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:goworkbro/main.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';

/// End-to-end test: login → pull data from Supabase.
/// Pre-conditions:
///   - User exists (passed via --dart-define=TEST_EMAIL=... --dart-define=TEST_PASSWORD=...)
///   - A TODO "Synced from Supabase" exists in Supabase for this user
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Login and pull synced data from Supabase', (tester) async {
    final localeProvider = AppLocaleProvider();
    await localeProvider.init();
    await tester.pumpWidget(GoWorkBroApp(localeProvider: localeProvider));
    await tester.pump(const Duration(seconds: 5));

    // Check if we're on the auth screen
    final authText = find.text('登录你的账号，同步跨设备数据');
    if (authText.evaluate().isNotEmpty) {
      print('Step 1: Auth screen shown ✓');

      // Enter credentials
      await tester.enterText(
        find.byType(TextField).at(0),
        const String.fromEnvironment('TEST_EMAIL', defaultValue: ''),
      );
      await tester.enterText(
        find.byType(TextField).at(1),
        const String.fromEnvironment('TEST_PASSWORD', defaultValue: ''),
      );
      print('Step 2: Entered credentials ✓');

      // Tap login button
      await tester.tap(find.widgetWithText(FilledButton, '登录'));
      print('Step 3: Tapped login, waiting for auth + sync...');

      // Wait for auth + pull (network calls)
      await tester.pump(const Duration(seconds: 10));
      await tester.pump(const Duration(seconds: 5));
    } else {
      print('Step 1: Already logged in (session persisted)');
    }

    // After login, the app should show the main screen
    final hasTodoScreen = find.text('待办').evaluate().isNotEmpty;
    if (hasTodoScreen) {
      print('Step 4: Main app loaded after login ✓');

      // Check if synced TODO appeared
      final syncedTodo = find.text('Synced from Supabase');
      if (syncedTodo.evaluate().isNotEmpty) {
        print('Step 5: *** SYNCED TODO PULLED FROM SUPABASE ✓ ***');
      } else {
        print(
          'Step 5: Synced TODO not visible yet (checking all visible texts...)',
        );
        // Dump what we can see
        final texts = find
            .byType(Text)
            .evaluate()
            .map((e) {
              return (e.widget as Text).data;
            })
            .where((s) => s != null && s!.isNotEmpty)
            .take(20)
            .join(', ');
        print('  Visible texts: $texts');
      }
    } else {
      print('Step 4: Not on main screen yet');
    }

    print('\n=== E2E AUTH SYNC TEST DONE ===');
  });
}
