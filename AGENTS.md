# GoWorkBro — Agent Guardrails

Native Android app (Kotlin + Jetpack Compose + Room, offline-first). The v1
Flutter/Supabase codebase is gone — do not follow any Flutter-era instructions
still floating around in old notes.

## Architecture in one line

Local-only todos / habits / focus timer / countdown / check-ins in app-private
SQLite (Room); the app's **only network call** is an anonymous GitHub Gist
fetch for the "USTC 每日要闻" news feed (no API keys, no accounts, no sync).

## Data safety — hard rules

- The user's live data is the on-device app-private Room database. Never run
  scripts, `adb` commands, or app actions that clear/overwrite it.
- All tests are hermetic JVM tests (Robolectric, fresh sandbox +
  `Graph.rebindForTesting`) — they cannot touch device data. Keep it that
  way; don't add tests that need a real device or the production DB.
- `app/key.properties` and `app/goworkbro-release.jks` are the release signing
  secrets (gitignored). Do not read or print their contents.

## Toolchain

- JDK 21 + Android SDK 35; the wrapper ships Gradle 8.12. Windows + git-bash.
- Build/test:
  - `./gradlew.bat :app:testDebugUnitTest` — all unit + Robolectric smoke tests
  - `./gradlew.bat :app:assembleDebug` — debug APK
  - `./gradlew.bat :app:assembleRelease` — signed APK (needs the keystore above)

## Conventions

- News markdown is rendered by the hand-rolled parser in
  `core/markdown/Markdown.kt` + `ui/components/MarkdownText.kt` (no markdown
  library). Extend both in tandem, and keep the parser scoped to what the
  Gist generator actually emits (see the `/ustc-news` skill for the format).
- Conventional commits in English. Large changes may commit directly to main,
  but never push without being asked.

## User command shorthand

When the user uses these words, they refer to the full flow, not the bare verb:

- **commit** → `git commit` (may commit directly to main).
- **push** → `git push` (saying it IS the authorization to push).
- **release vX.Y.Z** → 发行: bump `versionName`/`versionCode` in
  `app/build.gradle.kts`, add a CHANGELOG entry, `chore(release)` commit,
  tag `vX.Y.Z`, `assembleRelease`, then `gh release create` with
  `GoWorkBro-vX.Y.Z.apk` + `.sha256`; Chinese notes with 安装/校验 sections
  (see v2.0.1 for the template).
- **sync** → 同步到手机: `adb install -r` the latest released APK onto the
  user's phone (same-signature in-place upgrade, data preserved). Device
  details and debugging workflow live in the `/adb-debug` skill.
