/// Supabase configuration.
///
/// Credentials are passed via --dart-define at build time:
///   flutter run --dart-define=SUPABASE_URL=https://... --dart-define=SUPABASE_ANON_KEY=sb_...
///
/// For local dev convenience, use the dev script:
///   bash dev.sh        (reads credentials from lib/services/local_config.dart)
///
/// In CI/CD, GitHub Actions passes secrets as --dart-define.
/// If no credentials are provided, the app runs in local-only mode.
library;

const String supabaseUrl = String.fromEnvironment(
  'SUPABASE_URL',
  defaultValue: '',
);

const String supabaseAnonKey = String.fromEnvironment(
  'SUPABASE_ANON_KEY',
  defaultValue: '',
);

bool get isSupabaseConfigured =>
    supabaseUrl.isNotEmpty && supabaseAnonKey.isNotEmpty;
