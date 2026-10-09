# GoWorkBro — Agent Guardrails

Native Android app: Kotlin + Jetpack Compose + Room. **Offline-first,
zero tracking** — the ONLY network use is the self-updater checking and
downloading this repo's GitHub releases (`core/Updater.kt`); permissions
are just INTERNET + REQUEST_INSTALL_PACKAGES for that. Single user (Azar),
UI strings hardcoded Chinese.

## Architecture

**v3 (2026-09 rewrite — the Flutter/Supabase v1 and the v2 "news" era are
gone).** One home page, no tabs: a 2×3 grid of six modules — 待办 / 习惯 /
喝水 / 健身 / 倒计时 / 睡眠 — each pushing a detail screen onto the
hand-rolled nav stack in `ui/AppRoot.kt`. Data is app-private Room
(`goworkbro_v3.db`).

- `ui/home/` — the grid, the running-focus mini card, the today-focus line.
- `ui/todo/`, `ui/habit/` — two-column card grids: tap = act, long-press = edit.
  Finished todos are history and cannot be deleted (only the settings bulk
  wipe removes them).
- `ui/timer/` — pomodoro overlay (正向/倒计时). A round counts once a full
  minute passed; a round that ends while the overlay is open drops the user on
  the todo list. Timer state is in-memory on purpose (dies with the process).
- `ui/water/`, `ui/fitness/` — goal hero + quick logs + today list + 7-day bars.
- `ui/sleep/`, `ui/countdown/`, `ui/overview/`, `ui/settings/` — see the subsystems below.
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
- **Sleep records (v3.3)** — `sleep_records`: ONE row per SLEEP — a nap and
  a night are the same thing, a day can hold any number of them. Recording
  UI: two-step wheel bottom sheet (`ui/sleep/SleepScreen.kt`) — step 1
  sleep start (default now−7h, wheel now−48h → now−15min), step 2 wake
  (default now, wheel sleep+15min → now); the ONLY hard bound is "never
  the future". 15-min steps, wheels stop at their ends (no wrap), light
  date label under the wheel auto-crosses midnight. Stats in
  `core/SleepOps.kt`: a record is filed under the logical day it STARTED
  on (00:00–04:00 bedtimes belong to the previous evening); per-day totals
  (home card + hero "今天 X"), ≥7h-per-day streak (an unrecorded today
  doesn't break it), per-sleep average. Migration v3→v4 DROPPED
  `routine_events` on purpose (user abandoned the check-in history) but a
  real Migration object must exist or on-device upgrades crash.
- **Countdowns (v3.3)** — `countdowns` (title + target ISO datetime +
  pastel color). `ui/countdown/` lists items with live remaining labels
  (`core/CountdownOps.kt`: "X 天 HH:mm" past a day, ticking "HH:mm:ss"
  inside one, 已到时 after); the home tile shows the nearest item's short
  label, refreshed twice a minute. Add/edit = title field + date picker →
  time picker; migration v4→v5 created the table.
- **Launcher shortcuts** — static XML (`res/xml/shortcuts.xml`): 新建待办 /
  记录睡眠. Intents hit `MainActivity`, which keeps the action in
  a `mutableStateOf` handed to `AppRoot` (`core/Shortcuts.kt` has the action
  constants); AppRoot performs it once — NEW_TODO pushes the todo page and
  auto-opens the add dialog, LOG_SLEEP pushes the sleep page with the
  two-step record sheet already open.
- **Auto-update** — `core/Updater.kt` is the app's single networked surface:
  daily-throttled GitHub `releases/latest` check (skipped when the 设置 switch
  is off, and hard-skipped under Robolectric to keep tests hermetic),
  DownloadManager into the private external Download dir, then a
  PackageInstaller session committed from the home banner (system
  install-confirm UI; ACTION_VIEW+apk intents do not resolve reliably
  on modern Android). Pending-APK version is derived
  from the file name (`GoWorkBro-update-<version>.apk`); stale APKs (<= the
  running version) are deleted on startup.

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
  secrets; never read or print their contents. The ORIGINAL keystore was
  lost with the old Windows box; a NEW one was generated 2026-10-09
  (CN=GoWorkBro, O=azarai.top, validity 10000 days). The new signature does
  NOT interinstall with anything ≤ v3.2.0 (those were debug-signed) —
  upgrading requires an uninstall (data wipe). From v3.3.0 on, releases are
  properly signed and interinstall with each other.

## Toolchain (this machine = Linux)

- JDK 21; Android SDK at `/usr/lib/android-sdk` — platform 36 + build-tools 36
  only, and the directory is NOT writable, hence `compileSdk = 36`,
  `buildToolsVersion = "36.0.0"` and `local.properties` → `sdk.dir`. Do not
  rely on Gradle auto-installing SDK pieces.
- `scripts/test.sh fast|ui|all` — test TIERS, run the right amount:
  - `fast` (≈4s): pure-JVM logic classes. Run on every logic change.
  - `ui` (≈12s): Robolectric UI/VM classes. Run when a screen or VM
    changed, and before committing.
  - `all`: everything with a 15-min hard timeout (a hung test must never
    eat a session again). One full round before a commit is enough.
- Builds: `:app:assembleDebug`, `:app:assembleRelease` (`gradlew.bat` is
  the old Windows box — ignore it).
- Testing discipline (all learned the hard way, 2026-10-09):
  - Never `runBlocking { flow.first { … } }` without a timeout — one
    unsatisfied predicate deadlocks the whole executor.
  - Bare-VM tests must pump the main looper themselves
    (`shadowOf(Looper.getMainLooper()).idle()`) — viewModelScope is
    Main.immediate and nothing else drives it.
  - Keep Compose-test assertions SAME-PAGE; cross-page data-roundtrip
    assertions (write on page A, assert on home) are flaky in Robolectric —
    verify those once on the emulator (`.agents/skills/gwb-emu`) instead.
  - Do NOT enable `maxParallelFutures`: Graph/LogicalDay are static
    singletons shared across classes in one classloader; parallel classes
    poison each other's day/db.
  - New test classes get sorted into a tier in `scripts/test.sh`.
- Emulator testing (local-only skill, gitignored): `.agents/skills/gwb-emu`
  drives the headless local AVD — install/launch, tap-by-label, screenshots,
  app-data reset, guest-clock faking for 04:00 day-boundary tests. Prefer it
  over the phone for any manual verification or destructive-flow testing.

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
