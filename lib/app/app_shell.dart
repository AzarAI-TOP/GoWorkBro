import 'package:flutter/material.dart';
import 'package:window_manager/window_manager.dart';

import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/features/todos/todo_screen.dart';
import 'package:goworkbro/features/countdowns/countdown_screen.dart';
import 'package:goworkbro/features/today/today_screen.dart';
import 'package:goworkbro/features/me/me_screen.dart';

/// Main navigation shell: side rail on desktop (>= 900px), bottom bar on
/// mobile. Also intercepts the window close button to hide to tray.
class AppShell extends StatefulWidget {
  const AppShell({super.key});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> with WindowListener {
  int _currentIndex = 0;

  @override
  void initState() {
    super.initState();
    windowManager.addListener(this);
  }

  @override
  void dispose() {
    windowManager.removeListener(this);
    super.dispose();
  }

  /// Intercept the window close button — hide to tray instead of quitting.
  @override
  void onWindowClose() async {
    await windowManager.hide();
  }

  final _screens = const [
    TodoScreen(),
    CountdownScreen(),
    TodayScreen(),
    MeScreen(),
  ];

  List<String> get _labels {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    return [s.todo, s.countdown, s.today, s.me];
  }

  final _icons = [
    Icons.check_circle_outline,
    Icons.hourglass_empty,
    Icons.today_outlined,
    Icons.person_outline,
  ];

  @override
  Widget build(BuildContext context) {
    final isDesktop = MediaQuery.of(context).size.width >= 900;

    if (isDesktop) {
      return Scaffold(
        body: Row(
          children: [
            _buildSideNav(context),
            const VerticalDivider(width: 1),
            Expanded(child: _screens[_currentIndex]),
          ],
        ),
      );
    }

    return Scaffold(
      body: IndexedStack(index: _currentIndex, children: _screens),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (i) => setState(() => _currentIndex = i),
        items: List.generate(
          4,
          (i) =>
              BottomNavigationBarItem(icon: Icon(_icons[i]), label: _labels[i]),
        ),
      ),
    );
  }

  Widget _buildSideNav(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Container(
      width: 120,
      padding: const EdgeInsets.symmetric(vertical: 24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: List.generate(4, (i) {
          final selected = _currentIndex == i;
          final color = selected
              ? colorScheme.primary
              : colorScheme.onSurfaceVariant;
          return Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: () => setState(() => _currentIndex = i),
              borderRadius: BorderRadius.circular(16),
              child: Container(
                margin: const EdgeInsets.symmetric(vertical: 6, horizontal: 10),
                padding: const EdgeInsets.symmetric(vertical: 10),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 4,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: selected
                            ? colorScheme.primary.withValues(alpha: 0.12)
                            : Colors.transparent,
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Icon(
                        _icons[i],
                        size: selected ? 28 : 24,
                        color: color,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _labels[i],
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: selected
                            ? FontWeight.w600
                            : FontWeight.normal,
                        color: color,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        }),
      ),
    );
  }
}
