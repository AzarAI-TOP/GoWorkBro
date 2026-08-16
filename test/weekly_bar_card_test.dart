import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/features/today/widgets/weekly_bar_card.dart';
import 'package:provider/provider.dart';

void main() {
  testWidgets('weekly labels end on the provider logical date', (tester) async {
    const logicalEndDate = '2026-08-16';
    final locale = AppLocaleProvider();
    final strings = S.of(AppLocale.zh);
    final endDate = DateTime.parse(logicalEndDate);
    final expectedLabels = [
      for (int daysAgo = 6; daysAgo >= 0; daysAgo--)
        strings.weekdayShort(endDate.subtract(Duration(days: daysAgo)).weekday),
    ];

    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: locale,
        child: const MaterialApp(
          home: Scaffold(
            body: SizedBox(
              width: 600,
              height: 320,
              child: WeeklyBarCard(
                weeklySeconds: [0, 0, 0, 0, 0, 0, 0],
                endDate: logicalEndDate,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    for (final label in expectedLabels) {
      expect(find.text(label), findsOneWidget);
    }
  });
}
