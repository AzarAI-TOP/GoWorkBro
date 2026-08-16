import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/features/countdowns/widgets/countdown_card.dart';
import 'package:goworkbro/models/models.dart';
import 'package:provider/provider.dart';

void main() {
  testWidgets('long press offers edit without removing delete access', (
    tester,
  ) async {
    var editCount = 0;
    var deleteCount = 0;
    final locale = AppLocaleProvider();
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: locale,
        child: MaterialApp(
          home: Scaffold(
            body: CountdownCard(
              countdown: Countdown(
                id: 'countdown-1',
                title: '项目截止',
                targetDateTime: DateTime.now().add(const Duration(days: 2)),
                createdDate: DateTime.now().toIso8601String(),
              ),
              onEdit: () => editCount++,
              onDelete: () => deleteCount++,
            ),
          ),
        ),
      ),
    );

    await tester.longPress(find.text('项目截止'));
    await tester.pumpAndSettle();

    expect(find.text('编辑'), findsOneWidget);
    expect(find.text('删除'), findsOneWidget);
    await tester.tap(find.text('编辑'));
    await tester.pumpAndSettle();

    expect(editCount, 1);
    expect(deleteCount, 0);
  });
}
