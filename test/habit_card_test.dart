import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/services/app_locale.dart';

import 'package:goworkbro/widgets/todo/habit_card.dart';
import 'package:provider/provider.dart';

void main() {
  testWidgets('single-target habit renders a single completion action', (
    tester,
  ) async {
    final locale = AppLocaleProvider();
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: locale,
        child: MaterialApp(
          home: Scaffold(
            body: HabitCard(
              habit: Habit(
                id: 'h1',
                title: 'Read',
                targetCount: 1,
                unit: '次',
                sortOrder: 0,
                createdDate: '2026-08-13',
              ),
              index: 0,
              onIncrement: () {},
              onDecrement: () {},
              onLongPress: () {},
              onSecondaryTapDown: (_) {},
            ),
          ),
        ),
      ),
    );

    expect(find.byIcon(Icons.check_rounded), findsOneWidget);
    expect(find.byIcon(Icons.add), findsNothing);
    expect(find.byIcon(Icons.remove), findsNothing);
  });

  testWidgets('English locale shows translated persisted unit', (tester) async {
    final locale = AppLocaleProvider.forTesting(locale: AppLocale.en);
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: locale,
        child: MaterialApp(
          home: Scaffold(
            body: HabitCard(
              habit: Habit(
                id: 'h1',
                title: 'Read',
                targetCount: 3,
                unit: '页',
                sortOrder: 0,
                createdDate: '2026-08-13',
              ),
              index: 0,
              onIncrement: () {},
              onDecrement: () {},
              onLongPress: () {},
              onSecondaryTapDown: (_) {},
            ),
          ),
        ),
      ),
    );

    expect(find.text('Daily 0/3 pages'), findsOneWidget);
    expect(find.textContaining('页'), findsNothing);
  });
}
