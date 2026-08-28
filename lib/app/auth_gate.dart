import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'package:goworkbro/core/config/supabase_config.dart';
import 'package:goworkbro/providers/app_provider.dart';
import 'package:goworkbro/features/auth/auth_screen.dart';
import 'package:goworkbro/app/app_shell.dart';

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
        // onSignedIn awaits init() first — this fixes the race where
        // applyAuthUser used to run concurrently with the startup pull and
        // clobber the cloud profile name with the email-prefix fallback.
        final provider = context.read<AppProvider>();
        provider.onSignedIn();
      } else if (event == AuthChangeEvent.signedOut) {
        // Stop realtime/sync before the auth screen shows — the sync client
        // must not keep channels open for the signed-out session. Local
        // data is kept; a different account signing in later is handled by
        // AppProvider's account isolation wipe.
        context.read<AppProvider>().onSignedOut();
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
