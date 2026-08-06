/// Supabase configuration.
///
/// In production, pass credentials via --dart-define:
///   flutter run --dart-define=SUPABASE_URL=... --dart-define=SUPABASE_ANON_KEY=...
///
/// For local dev, create `lib/services/local_config.dart` (gitignored) with:
///   const String localSupabaseUrl = 'https://your-project.supabase.co';
///   const String localSupabaseAnonKey = 'your-key';
///
/// If neither is provided, the app runs in local-only mode (no auth/sync).
library;

import 'local_config.dart' if (dart.library.html) 'empty_config.dart';

const String supabaseUrl = String.fromEnvironment(
  'SUPABASE_URL',
  defaultValue: localSupabaseUrl,
);

const String supabaseAnonKey = String.fromEnvironment(
  'SUPABASE_ANON_KEY',
  defaultValue: localSupabaseAnonKey,
);

bool get isSupabaseConfigured =>
    supabaseUrl.isNotEmpty && supabaseAnonKey.isNotEmpty;
