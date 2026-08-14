# GoWorkBro — Agent Guardrails

Flutter app (Android + Windows desktop) with local SQLite + Supabase cloud sync.

## Data safety — hard rules

- The **real user database** lives at `C:\Users\ASUS\Documents\goworkbro.db`
  (`getApplicationDocumentsDirectory()` at runtime). It holds the user's live
  todos, habits, check-in records, focus sessions, and profile. **Treat it as
  read-only.**
- Never run tests, `flutter run`, or scripts against the real DB. Integration
  tests must always go through the isolated data dir wired in
  `integration_test/user_flow_test.dart` (`AppDatabase.setDataDirForTesting`).
  Do not remove or bypass that isolation.
- Never delete or overwrite user data (todos / habits / sleep / focus records)
  as part of a test run or "cleanup". If a run pollutes the real DB, report it
  to the user instead of silently cleaning.
- Do not read or print secrets from `lib/services/local_config.dart` /
  `dev.sh` (Supabase credentials).

## Toolchain

- Flutter lives at `C:\flutter\bin` — in git-bash:
  `export PATH="/c/flutter/bin:$PATH"`.
- Verify with: `flutter analyze`, `flutter test`,
  `flutter test integration_test/user_flow_test.dart -d windows`.

## Conventions

- Conventional commits in English. Large changes may commit directly to main,
  but never push without being asked.
- Test assertions must not depend on persisted profile state (user name,
  locale). The real DB currently has `locale=en`, `user_name=AzarAI` — assert
  against stable keys or fresh-install defaults instead.
