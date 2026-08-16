import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/features/todos/todo_screen.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/providers/app_provider.dart';
import 'package:provider/provider.dart';

class _FakeAppProvider extends AppProvider {
  _FakeAppProvider({required this.fakeTodos});

  final List<Todo> fakeTodos;

  @override
  List<Todo> get todos => fakeTodos;

  @override
  List<Habit> get habits => const [];
}

void main() {
  testWidgets('completed items begin with a visual section divider', (
    tester,
  ) async {
    final provider = _FakeAppProvider(
      fakeTodos: [
        Todo.create(title: '待完成', timingType: TimingType.none),
        Todo.create(title: '已完成', timingType: TimingType.none).copyWith(
          isCompleted: true,
          completedDate: DateTime.now().toIso8601String(),
        ),
      ],
    );
    final locale = AppLocaleProvider();
    addTearDown(provider.dispose);

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<AppProvider>.value(value: provider),
          ChangeNotifierProvider.value(value: locale),
        ],
        child: const MaterialApp(home: TodoScreen()),
      ),
    );

    expect(find.byKey(const Key('completed-section-divider')), findsOneWidget);
    expect(find.text('已完成 · 1'), findsOneWidget);
  });
}
