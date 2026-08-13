import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:provider/provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:window_manager/window_manager.dart';
import 'providers/app_provider.dart';
import 'services/app_locale.dart';
import 'services/supabase_config.dart';
import 'services/tray_service.dart';
import 'theme/app_theme.dart';
import 'screens/auth_screen.dart';
import 'screens/todo_screen.dart';
import 'screens/countdown_screen.dart';
import 'screens/today_screen.dart';
import 'screens/me_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Must be initialized before window_manager on Windows
  if (!kIsWeb && defaultTargetPlatform == TargetPlatform.windows) {
    await windowManager.ensureInitialized();
    await windowManager.waitUntilReadyToShow(
      const WindowOptions(
        size: Size(1200, 800),
        minimumSize: Size(400, 600),
        title: 'GoWorkBro',
        titleBarStyle: TitleBarStyle.normal,
        center: true,
      ),
      () async {
        await windowManager.show();
        await windowManager.focus();
      },
    );
    // Prevent default close — we hide to tray instead
    await windowManager.setPreventClose(true);
    await TrayService().init();
  }

  // Initialize Supabase before running app (if configured)
  if (isSupabaseConfigured) {
    await Supabase.initialize(
      url: supabaseUrl,
      publishableKey: supabaseAnonKey,
      debug: kDebugMode,
    );
  }

  // Load persisted locale/theme before first paint
  final localeProvider = AppLocaleProvider();
  await localeProvider.init();

  runApp(GoWorkBroApp(localeProvider: localeProvider));
}

class GoWorkBroApp extends StatelessWidget {
  final AppLocaleProvider localeProvider;
  const GoWorkBroApp({super.key, required this.localeProvider});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<AppLocaleProvider>.value(value: localeProvider),
        ChangeNotifierProvider<AppProvider>(create: (_) => AppProvider()),
      ],
      child: Consumer<AppLocaleProvider>(
        builder: (context, localeProvider, _) {
          return MaterialApp(
            title: 'GoWorkBro',
            debugShowCheckedModeBanner: false,
            theme: AppTheme.light,
            darkTheme: AppTheme.dark,
            themeMode: localeProvider.themeMode,
            locale: localeProvider.flutterLocale,
            supportedLocales: const [Locale('zh'), Locale('en')],
            localizationsDelegates: const [
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            home: const AuthGate(),
          );
        },
      ),
    );
  }
}

/// Decides whether to show the auth screen or the main app.
/// Listens to Supabase auth state changes.
class AuthGate extends StatefulWidget {
  const AuthGate({super.key});

  @override
  State<AuthGate> createState() => _AuthGateState();
}

class _AuthGateState extends State<AuthGate> {
  bool _isLoggedIn = false;
  bool _checkingSession = true;

  @override
  void initState() {
    super.initState();
    _checkInitialSession();
    _listenAuthChanges();
  }

  void _checkInitialSession() {
    if (!isSupabaseConfigured) {
      setState(() => _checkingSession = false);
      return;
    }
    final session = Supabase.instance.client.auth.currentSession;
    _isLoggedIn = session != null;
    if (_isLoggedIn) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        context.read<AppProvider>().init();
      });
    }
    setState(() => _checkingSession = false);
  }

  void _listenAuthChanges() {
    if (!isSupabaseConfigured) return;
    Supabase.instance.client.auth.onAuthStateChange.listen((data) {
      if (!mounted) return;
      final event = data.event;
      final session = data.session;

      if (event == AuthChangeEvent.signedIn && session != null) {
        setState(() => _isLoggedIn = true);
        final provider = context.read<AppProvider>();
        provider.init();
        // Derive profile (email prefix / metadata) even if init already ran
        // earlier in offline mode.
        provider.applyAuthUser();
      } else if (event == AuthChangeEvent.signedOut) {
        setState(() => _isLoggedIn = false);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_checkingSession) {
      return Scaffold(
        body: Center(
          child: CircularProgressIndicator(
            color: Theme.of(context).colorScheme.primary,
          ),
        ),
      );
    }

    if (!isSupabaseConfigured) {
      // Local-only mode — init AppProvider directly
      WidgetsBinding.instance.addPostFrameCallback((_) {
        context.read<AppProvider>().init();
      });
      return const AppShell();
    }

    if (!_isLoggedIn) {
      return AuthScreen(
        onUseOffline: () {
          // #13: Allow offline mode — init AppProvider without auth
          WidgetsBinding.instance.addPostFrameCallback((_) {
            context.read<AppProvider>().init();
          });
          setState(() => _isLoggedIn = true); // bypass auth
        },
      );
    }

    return const AppShell();
  }
}

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
