import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:goworkbro/main.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/providers/app_provider.dart';
import 'package:provider/provider.dart';

/// Comprehensive integration test simulating real user flows.
/// Run with: flutter test integration_test/user_flow_test.dart -d windows
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  /// Helper: get the AppProvider from the running app
  AppProvider getProvider(WidgetTester tester) {
    final ctx = tester.element(find.byType(MaterialApp));
    return Provider.of<AppProvider>(ctx, listen: false);
  }

  /// Helper: navigate to a tab by label
  Future<void> navigateTo(WidgetTester tester, String label) async {
    final finder = find.text(label);
    if (finder.evaluate().isNotEmpty) {
      // On desktop the label appears in the sidebar; on mobile in the bottom nav.
      // Tap the last occurrence to avoid the AppBar title.
      await tester.tap(finder.last);
      await tester.pumpAndSettle(const Duration(seconds: 1));
    }
  }

  group('Cycle 1 — Basic User Flow', () {
    testWidgets('Add TODO, add habit, record focus, check Today and Me', (tester) async {
      await tester.pumpWidget(const GoWorkBroApp());
      await tester.pumpAndSettle(const Duration(seconds: 5));

      final provider = getProvider(tester);
      await provider.init();
      await tester.pumpAndSettle();

      // Clear any existing data for a clean test
      for (final t in List.of(provider.todos)) {
        await provider.deleteTodo(t.id);
      }
      for (final h in List.of(provider.habits)) {
        await provider.deleteHabit(h.id);
      }
      await tester.pumpAndSettle();

      // Step 1: On Todo screen
      expect(find.text('待办'), findsAtLeast(1));
      print('Step 1: On Todo screen ✓');

      // Step 2: Open add sheet
      await tester.tap(find.byType(FloatingActionButton));
      await tester.pumpAndSettle();
      expect(find.text('新建待办'), findsOneWidget);
      print('Step 2: Add sheet opened ✓');

      // Step 3: Choose TODO
      await tester.tap(find.text('新建待办'));
      await tester.pumpAndSettle();

      // Step 4: Enter title
      await tester.enterText(find.byType(TextField).first, '复习线性代数');
      await tester.pumpAndSettle();

      // Step 5: Select "倒向计时" — use RadioListTile specifically
      final radioTiles = find.byType(RadioListTile<TimingType>);
      expect(radioTiles, findsNWidgets(3));
      await tester.tap(radioTiles.at(1)); // 0=forward, 1=backward, 2=none
      await tester.pumpAndSettle();

      // Step 6: Select 40min
      await tester.tap(find.text('40min'));
      await tester.pumpAndSettle();

      // Step 7: Save
      await tester.tap(find.text('保存'));
      await tester.pumpAndSettle();
      expect(find.text('复习线性代数'), findsOneWidget);
      print('Step 3-7: TODO with 40min backward timer saved ✓');

      // Step 8: Add habit
      await tester.tap(find.byType(FloatingActionButton));
      await tester.pumpAndSettle();
      await tester.tap(find.text('新建习惯'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).first, '做俯卧撑');
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).at(1), '3');
      await tester.pumpAndSettle();
      await tester.tap(find.text('保存'));
      await tester.pumpAndSettle();
      expect(find.text('做俯卧撑'), findsOneWidget);
      print('Step 8: Habit saved ✓');

      // Step 9: Record focus session
      await provider.recordFocusSession(FocusSession.create(
        sourceType: 'todo',
        sourceTitle: '复习线性代数',
        durationSeconds: 40 * 60,
      ));
      await tester.pumpAndSettle();
      print('Step 9: Focus session recorded ✓');

      // Step 10: Navigate to Today
      await navigateTo(tester, '今天');
      expect(find.text('今日专注'), findsAtLeast(1));
      print('Step 10: Today shows focus stats ✓');

      // Step 11: Navigate to Me
      await navigateTo(tester, '我的');
      expect(find.text('AzarAI'), findsAtLeast(1));
      expect(find.text('打卡'), findsOneWidget);
      expect(find.text('统计'), findsOneWidget);
      expect(find.text('设置'), findsOneWidget);
      print('Step 11: Me screen OK ✓');

      // Step 12: Stats tab
      await tester.tap(find.text('统计'));
      await tester.pumpAndSettle();
      expect(find.text('今日专注'), findsAtLeast(1));
      expect(find.text('今日番茄数'), findsOneWidget);
      print('Step 12: Stats tab shows data ✓');

      print('\n=== CYCLE 1 PASSED ===');
    });

    testWidgets('Add countdown, verify, delete', (tester) async {
      await tester.pumpWidget(const GoWorkBroApp());
      await tester.pumpAndSettle(const Duration(seconds: 5));

      final provider = getProvider(tester);
      await provider.init();
      await tester.pumpAndSettle();

      await navigateTo(tester, '倒计时');

      await tester.tap(find.byType(FloatingActionButton));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).first, '期末考试');
      await tester.pumpAndSettle();
      await tester.tap(find.text('创建'));
      await tester.pumpAndSettle();
      expect(find.text('期末考试'), findsOneWidget);
      print('Countdown created ✓');

      await tester.longPress(find.text('期末考试'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('删除'));
      await tester.pumpAndSettle();
      expect(find.text('还没有倒计时'), findsOneWidget);
      print('Countdown deleted ✓');

      print('\n=== COUNTDOWN TEST PASSED ===');
    });
  });

  group('Cycle 2 — Edge Cases', () {
    testWidgets('Empty title validation', (tester) async {
      await tester.pumpWidget(const GoWorkBroApp());
      await tester.pumpAndSettle(const Duration(seconds: 5));

      final provider = getProvider(tester);
      await provider.init();
      await tester.pumpAndSettle();

      await tester.tap(find.byType(FloatingActionButton));
      await tester.pumpAndSettle();
      await tester.tap(find.text('新建待办'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('保存'));
      await tester.pumpAndSettle();
      expect(find.text('请输入标题'), findsOneWidget);
      print('Empty title validation ✓');

      await tester.enterText(find.byType(TextField).first, '有标题的待办');
      await tester.pumpAndSettle();
      await tester.tap(find.text('保存'));
      await tester.pumpAndSettle();
      expect(find.text('有标题的待办'), findsOneWidget);
      print('Save with title works ✓');
    });

    testWidgets('Habit increment/decrement via provider', (tester) async {
      await tester.pumpWidget(const GoWorkBroApp());
      await tester.pumpAndSettle(const Duration(seconds: 5));

      final provider = getProvider(tester);
      await provider.init();

      // Clear and add a habit
      for (final h in List.of(provider.habits)) {
        await provider.deleteHabit(h.id);
      }
      await provider.addHabit(Habit.create(title: '喝水', targetCount: 3, unit: '杯'));
      await tester.pumpAndSettle();

      expect(find.text('喝水'), findsOneWidget);

      // Increment via provider (simulating button taps)
      await provider.incrementHabit(provider.habits.first);
      await tester.pumpAndSettle();
      await provider.incrementHabit(provider.habits.first);
      await tester.pumpAndSettle();
      await provider.incrementHabit(provider.habits.first);
      await tester.pumpAndSettle();

      expect(provider.habits.first.isCompleted, true);
      expect(provider.habits.first.currentCount, 3);
      print('Habit increment to completion ✓');

      // Decrement
      await provider.decrementHabit(provider.habits.first);
      await tester.pumpAndSettle();
      expect(provider.habits.first.currentCount, 2);
      expect(provider.habits.first.isCompleted, false);
      print('Habit decrement ✓');
    });

    testWidgets('Sleep check-in', (tester) async {
      await tester.pumpWidget(const GoWorkBroApp());
      await tester.pumpAndSettle(const Duration(seconds: 5));

      final provider = getProvider(tester);
      await provider.init();
      await tester.pumpAndSettle();

      await navigateTo(tester, '我的');

      expect(find.text('起床'), findsOneWidget);
      expect(find.text('睡觉'), findsOneWidget);
      print('Sleep check-in UI present ✓');

      // Record sleep directly via provider
      await provider.recordSleep(SleepRecord.create(
        recordDate: provider.todayDate,
        wakeTime: '07:30',
        sleepTime: '23:00',
      ));
      await tester.pumpAndSettle();

      // The times should now be visible (either in buttons or in records)
      expect(find.textContaining('07:30'), findsAtLeast(1));
      expect(find.textContaining('23:00'), findsAtLeast(1));
      print('Sleep record displayed ✓');
    });

    testWidgets('TODO with "不记时" completes on tap', (tester) async {
      await tester.pumpWidget(const GoWorkBroApp());
      await tester.pumpAndSettle(const Duration(seconds: 5));

      final provider = getProvider(tester);
      await provider.init();

      for (final t in List.of(provider.todos)) {
        await provider.deleteTodo(t.id);
      }

      await provider.addTodo(Todo.create(
        title: '不计时任务',
        timingType: TimingType.none,
      ));
      await tester.pumpAndSettle();

      expect(find.text('不计时任务'), findsOneWidget);

      // Tap the TODO card to complete it
      await tester.tap(find.text('不计时任务'));
      await tester.pumpAndSettle();

      expect(provider.todos.where((t) => t.isCompleted).length, 1);
      print('No-timing TODO completes on tap ✓');
    });

    testWidgets('TODO with timing opens timer screen, records session', (tester) async {
      await tester.pumpWidget(const GoWorkBroApp());
      await tester.pump(const Duration(seconds: 5));

      final provider = getProvider(tester);
      await provider.init();

      for (final t in List.of(provider.todos)) {
        await provider.deleteTodo(t.id);
      }

      await provider.addTodo(Todo.create(
        title: '正向计时任务',
        timingType: TimingType.forward,
      ));
      await tester.pump(const Duration(milliseconds: 500));

      // Tap the TODO — should open timer, NOT complete
      await tester.tap(find.text('正向计时任务'));
      await tester.pump(const Duration(milliseconds: 500));

      // Timer screen should be visible (has play button)
      expect(find.byIcon(Icons.play_arrow), findsOneWidget);
      expect(provider.todos.first.isCompleted, false);
      print('Timed TODO opens timer screen ✓');

      // Navigate back via Navigator (CountdownScreen's Timer.periodic
      // prevents pumpAndSettle from ever settling)
      final navigator = tester.state<NavigatorState>(find.byType(Navigator).first);
      navigator.pop();
      await tester.pump(const Duration(milliseconds: 500));

      expect(find.text('正向计时任务'), findsAtLeast(1));
      print('Timer cancel returns to todo screen ✓');

      // Record a focus session directly via provider to verify Today data
      await provider.recordFocusSession(FocusSession.create(
        sourceType: 'todo',
        sourceTitle: '正向计时任务',
        durationSeconds: 1500,
      ));
      await tester.pump(const Duration(milliseconds: 500));
      expect(provider.todaySessionCount, greaterThan(0));
      print('Focus session recorded via provider ✓');
    });
  });
}
