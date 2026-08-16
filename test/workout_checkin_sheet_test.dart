import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/features/me/widgets/workout_checkin_sheet.dart';
import 'package:provider/provider.dart';

void main() {
  testWidgets('workout check-in saves duration and description', (
    tester,
  ) async {
    WorkoutCheckInResult? saved;
    final locale = AppLocaleProvider();
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: locale,
        child: MaterialApp(
          home: Scaffold(
            body: WorkoutCheckInSheet(
              initialDurationMinutes: 20,
              initialDescription: '旧记录',
              onSave: (result) => saved = result,
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('45 分钟'));
    await tester.enterText(
      find.byKey(const Key('workout-description')),
      '爬楼梯 20 层',
    );
    await tester.tap(find.byKey(const Key('save-workout-checkin')));

    expect(saved?.durationMinutes, 45);
    expect(saved?.description, '爬楼梯 20 层');
  });

  testWidgets('custom workout duration rejects values over one day', (
    tester,
  ) async {
    WorkoutCheckInResult? saved;
    await tester.pumpWidget(
      ChangeNotifierProvider(
        create: (_) => AppLocaleProvider(),
        child: MaterialApp(
          home: Scaffold(
            body: WorkoutCheckInSheet(onSave: (result) => saved = result),
          ),
        ),
      ),
    );

    await tester.enterText(
      find.byKey(const Key('custom-workout-duration')),
      '1441',
    );
    await tester.tap(find.byKey(const Key('save-workout-checkin')));
    await tester.pump();

    expect(saved, isNull);
    expect(find.text('请输入 1–1440 分钟'), findsOneWidget);
  });
}
