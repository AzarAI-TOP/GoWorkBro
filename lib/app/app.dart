import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:provider/provider.dart';

import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/core/theme/app_theme.dart';
import 'package:goworkbro/providers/app_provider.dart';
import 'package:goworkbro/app/auth_gate.dart';

/// Root widget: wires up providers and the MaterialApp shell.
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
            theme: AppTheme.light(fontFamily: localeProvider.fontFamily),
            darkTheme: AppTheme.dark(fontFamily: localeProvider.fontFamily),
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
