import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:goworkbro/main.dart';
import 'package:goworkbro/screens/auth_screen.dart';
import 'package:goworkbro/services/app_locale.dart';
import 'package:provider/provider.dart';

/// Auth screen widget test — tests the AuthScreen in isolation.
/// Does not require Supabase to be initialized.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  Widget authApp({AppLocale locale = AppLocale.zh}) =>
      ChangeNotifierProvider<AppLocaleProvider>.value(
        value: AppLocaleProvider.forTesting(locale: locale),
        child: const MaterialApp(home: AuthScreen()),
      );

  testWidgets('AuthScreen renders correctly', (tester) async {
    await tester.pumpWidget(authApp());
    await tester.pump();

    // Should see login UI elements
    expect(find.text('登录'), findsAtLeast(1));
    expect(find.text('登录你的账号，同步跨设备数据'), findsOneWidget);
    expect(find.byType(TextField), findsNWidgets(2));
    expect(find.text('没有账号？点击注册'), findsOneWidget);
    print('AuthScreen renders correctly ✓');
  });

  testWidgets('Toggle to register mode', (tester) async {
    await tester.pumpWidget(authApp());
    await tester.pump();

    await tester.tap(find.text('没有账号？点击注册'));
    await tester.pump();

    expect(find.text('注册'), findsAtLeast(1));
    expect(find.textContaining('创建新账号'), findsOneWidget);
    expect(find.text('已有账号？点击登录'), findsOneWidget);
    print('Toggle to register mode ✓');

    // Toggle back
    await tester.tap(find.text('已有账号？点击登录'));
    await tester.pump();
    expect(find.text('没有账号？点击注册'), findsOneWidget);
    print('Toggle back to login mode ✓');
  });

  testWidgets('Empty field validation', (tester) async {
    await tester.pumpWidget(authApp());
    await tester.pump();

    await tester.tap(find.widgetWithText(FilledButton, '登录'));
    await tester.pump();

    expect(find.text('请输入邮箱和密码'), findsOneWidget);
    print('Empty field validation ✓');
  });

  testWidgets('Short password validation', (tester) async {
    await tester.pumpWidget(authApp());
    await tester.pump();

    await tester.enterText(find.byType(TextField).at(0), 'test@example.com');
    await tester.enterText(find.byType(TextField).at(1), '123');
    await tester.tap(find.widgetWithText(FilledButton, '登录'));
    await tester.pump();

    expect(find.text('密码至少 6 位'), findsOneWidget);
    print('Short password validation ✓');
  });

  testWidgets('AuthScreen is fully localized in English', (tester) async {
    await tester.pumpWidget(authApp(locale: AppLocale.en));
    await tester.pump();

    expect(find.text('Sign In'), findsAtLeast(1));
    expect(find.text('Sign in to sync data across devices'), findsOneWidget);
    expect(find.text('Email'), findsOneWidget);
    expect(find.text('Password'), findsOneWidget);
    expect(find.text('No account? Register'), findsOneWidget);
    expect(find.textContaining(RegExp(r'[\u4e00-\u9fff]')), findsNothing);

    await tester.tap(find.widgetWithText(FilledButton, 'Sign In'));
    await tester.pump();
    expect(find.text('Enter your email and password'), findsOneWidget);
  });
}
