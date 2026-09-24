# GoWorkBro — Agent Guardrails

Native Android app: Kotlin + Jetpack Compose + Room. **Fully offline, zero
network, zero permissions.** Single user (Azar), UI strings hardcoded Chinese.

## Architecture

**v3 (2026-09 rewrite — the Flutter/Supabase v1 and the v2 "news" era are
gone).** One home page, no tabs: a 2×3 grid of six modules — 待办 / 习惯 /
喝水 / 健身 / 起床 / 睡觉 — each pushing a detail screen onto the hand-rolled
nav stack in `ui/AppRoot.kt`. Data is app-private Room (`goworkbro_v3.db`).

- `ui/home/` — the grid, the running-focus mini card, the today-focus line.
- `ui/todo/`, `ui/habit/` — two-column card grids: tap = act, long-press = edit.
  Finished todos are history and cannot be deleted (only the settings bulk
  wipe removes them).
- `ui/timer/` — pomodoro overlay (正向/倒计时). A round counts once a full
  minute passed; a round that ends while the overlay is open drops the user on
  the todo list. Timer state is in-memory on purpose (dies with the process).
- `ui/water/`, `ui/fitness/` — goal hero + quick logs + today list + 7-day bars.
- `ui/routine/`, `ui/overview/`, `ui/settings/` — see the subsystems below.
- `ui/components/` — hand-drawn Canvas cartoons (`CartoonIcon.kt`, 16 icons:
  extend `CartoonIcons.all` + one draw function), cards/charts/dialogs,
  `rememberTick()` haptics, the cold-start sprout flight (`StartupReveal.kt`).
- Theme: forest palette (`ui/theme/Color.kt`), follows system light/dark.

### Subsystems worth knowing

- **Day boundary** — `core/LogicalDay.kt` is the single source of truth: one
  flow emits the current logical day + its window, re-emits on app foreground
  and by itself when the 04:00/midnight boundary passes. Screens must NOT
  thread refresh counters. Late-night mode (0–4am counts as yesterday) is a
  settings toggle, default ON; `core/Store.kt#Rollover` resets habits on each
  new logical day, driven from the AppRoot collector.
- **Focus statistics** — `focus_logs` rows are written with the logical day
  (`Rollover.logicalToday`), never the calendar day, and are kept forever:
  home/overview read them per logical day (`FocusDao.observeDayStats` /
  `observeDayByTodo`), the overview shows them as a per-todo donut.
- **Wake/sleep** — `routine_events` (kind + absolute local time + all-nighter
  flag), paired by `core/RoutineOps.kt`: one night = a bedtime plus the next
  wake. A wake with no pending bedtime asks the user for the missed bedtime
  (time picker) or 通宵 (`ui/routine/MissedSleepDialog.kt`). Night keys: before
  noon = the night in progress, from noon = the night starting tonight; a
  paired night is keyed by its bedtime. Streaks/averages/7-night charts are all
  derived from paired nights, home cards from "is there an event of that kind
  inside the current logical day window".

## Data safety — hard rules

- The user's live data is the on-device app-private Room database. Never run
  scripts, `adb` commands or app actions that clear/overwrite it. Deleting an
  unfinished todo is a user action; everything else is only cleared through the
  settings page's explicit, confirmed bulk wipe.
- All tests are hermetic JVM tests (Robolectric, fresh DB per test via
  `Graph.rebindForTesting`); they cannot touch device data. Keep it that way.
- Room invalidation only tracks writes through the SAME database instance, so
  tests that seed data must seed BEFORE the activity launches
  (`createEmptyComposeRule` + `ActivityScenario`, see `OverviewFlowTest`).
- Schema bumps MUST ship a real `Migration`. `fallbackToDestructiveMigration`
  did not save the v1→v2 upgrade on device (it crashed with "A migration from
  1 to 2 was required but not found"); v2→v3 has a real conversion in
  `AppDatabase.MIGRATION_2_3` — copy that pattern.
- `app/key.properties` + `app/goworkbro-release.jks` are gitignored signing
  secrets; never read or print their contents. They are currently NOT on this
  Linux machine (they lived on the old Windows box), so `assembleRelease`
  silently falls back to debug signing — call that out in release notes until
  the keystore is restored.

## Toolchain (this machine = Linux)

- JDK 21; Android SDK at `/usr/lib/android-sdk` — platform 36 + build-tools 36
  only, and the directory is NOT writable, hence `compileSdk = 36`,
  `buildToolsVersion = "36.0.0"` and `local.properties` → `sdk.dir`. Do not
  rely on Gradle auto-installing SDK pieces.
- `./gradlew :app:testDebugUnitTest` (all unit + Robolectric tests),
  `:app:assembleDebug`, `:app:assembleRelease` (`gradlew.bat` is the old
  Windows box — ignore it).

## Device automation (learned the hard way, 2026-09-24)

- Azar's phone is MIUI with system overlays (DockAssistantView, miui.notes
  sidebar) and his own IME (Sogou). Blind `input tap` automation competes with
  him using the phone, and `input text` pops his keyboard.
- Prefer hermetic Robolectric tests; if device automation is unavoidable keep
  it short, fail fast (bounded waits), temporarily
  `adb shell ime disable <id>` (restore it in the same command line), never
  leave dialogs dangling (`am force-stop`), and ask him to leave the phone idle.
- Robolectric's viewport is 320×470dp: the third home row is off-screen (use
  `performScrollTo()`), and clicks on a `clickable` nested inside another
  `clickable` (the home cards' 打卡 buttons) are never delivered — cover those
  flows from the detail pages or through the ViewModel instead.

## Conventions

- Conventional commits in English, `Co-Authored-By:` footer for AI-authored
  work (see the global instructions for the exact format).
- Large changes may commit directly to main, but never push without being asked.

## User command shorthand

- **commit** → `git commit` (may commit directly to main).
- **push** → `git push` (saying it IS the authorization to push).
- **release vX.Y.Z** → bump `versionName`/`versionCode` in
  `app/build.gradle.kts`, add a CHANGELOG entry, `chore(release)` commit, tag
  `vX.Y.Z`, `assembleRelease`, then `gh release create` with
  `GoWorkBro-vX.Y.Z.apk` + `.sha256`; Chinese notes with 安装/校验 sections
  (see v2.0.1 for the template).
- **sync** → 同步到手机: `adb install -r` the latest APK (uninstall first if
  signatures differ — debug and release builds do NOT interinstall).
