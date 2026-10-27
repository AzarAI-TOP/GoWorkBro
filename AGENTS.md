# GoWorkBro — Agent Guardrails

Native Android app: Kotlin + Jetpack Compose + Room. **Offline-first,
zero tracking** — the ONLY network use is the self-updater checking and
downloading this repo's GitHub releases (`core/Updater.kt`); permissions
are just INTERNET + REQUEST_INSTALL_PACKAGES for that. Single user (Azar),
UI strings hardcoded Chinese.

## Architecture

**v3 (2026-09 rewrite — the Flutter/Supabase v1 and the v2 "news" era are
gone).** One home page, no tabs: a 2-column grid of five modules — 待办 / 习惯 /
喝水 / 倒计时 in two square rows, 睡眠 as one full-width card — each
pushing a detail screen onto the hand-rolled nav stack in `ui/AppRoot.kt`. Data is app-private Room
(`goworkbro_v3.db`).

- `ui/home/` — the grid, the running-focus mini card, the today-focus line
  (hidden while it would read all zeros), and one header entry: sprout =
  数据纵览 (its bottom gear is the ONLY way to 设置 — the home gear was
  dropped in v3.6 to keep the header to one icon). A card may carry a one-tap 打卡 pill on its title row — today
  only 喝水 uses it (`HomeViewModel.drinkWater`, logged on the logical day).
  Anything ambiguous with several items (习惯, multiple todos) deliberately
  has no home quick action.
- `ui/todo/`, `ui/habit/` — two-column card grids: tap = act, long-press =
  DRAG TO SORT (launcher-icon style, `ui/components/GridReorder.kt` +
  `core/util/Ordering.kt`: `rememberDragSort` reorders locally and writes once
  on drop, `sort_order` DESC for open todos, ASC for habits; only the open todo
  list is sortable), and the visible `⋯` chip is the only way into the
  editor. Editors
  are `ModalBottomSheet`s with the icon/colour block folded behind one 外观
  row (the old AlertDialog held 27 icons + 8 dots and clipped the last dot).
  Finishing stamps `todos.done_date` with the logical day; the list shows
  today's completions under 已完成 and folds everything older behind one
  更早完成 N 条 row (a v5→v6 migration gave existing done rows `''`, which
  counts as older). Nothing is deleted and finished items still cannot be
  removed one by one (only the settings bulk wipe clears them); a finished
  plain todo re-opens with a tap, and a finished TIMED todo re-opens *and*
  starts a new round (that is the "second session" gesture — no separate
  button). After completing
  something the grid snaps back to the top (`LazyGridState.animateScrollToItem(0)`)
  instead of following the item down into the done section.
- Deletions ask first: todo / habit / countdown go through
  `ConfirmDeleteDialog`; sleep already did.
- `ui/timer/` — pomodoro overlay (正向/倒计时). A round counts once a full
  minute passed, and only the overlay's ✓ marks the todo done (stop /
  countdown-expiry just logs the round). A round that ends while the overlay
  is open drops the user on the todo list. Timer state is in-memory on
  purpose (dies with the process).
- `ui/water/` — goal hero + quick logs + today list + 7-day bars. (`ui/fitness/`
  is gone since v3.6: exercise is just another habit. Migration v6→v7 DROPs
  `fitness_logs`, so that history is intentionally gone.)
- `ui/sleep/`, `ui/countdown/`, `ui/overview/`, `ui/settings/` — see the subsystems below.
- `ui/components/` — hand-drawn Canvas cartoons (`CartoonIcon.kt`, 27 icons:
  extend `CartoonIcons.all` + one draw function; icons are drawn in a 0..100
  space with an ink outline). Style rule: objects stay faceless — no eyes,
  smiles, blush or sparkles — only the animals (dog / cat / fish) keep a
  minimal face; decoration is what made the old set noisy at 30dp. Cards,
  charts, dialogs, the long-press drag sorter (`GridReorder.kt` — screens use
  `rememberDragSort`; its gesture half must sit INSIDE the card's click
  handler, otherwise the release of a long press also counts as a tap),
  `rememberTick()` haptics, the cold-start sprout flight (`StartupReveal.kt`,
  at most once per calendar day — see `Store.Keys.LAST_REVEAL_DATE`).
  Charts carry their values + a dashed goal line and show an explicit empty
  state, never a flat 1px row of nothing; every chart sets a
  `contentDescription` that spells the data out.
- Theme: forest palette (`ui/theme/Color.kt`), follows system light/dark.
  **Light-theme colours that carry text must stay >= 4.5:1** against the cream
  scaffold / card / input fill (current: primary 4.9, textSecondary 5.0); a
  display* style MUST exist in `ForestTypography` or the timer clock and the
  workout stopwatch silently fall back to Roboto.

### Subsystems worth knowing

- **Backup (v3.4)** — `core/Backup.kt`: the ONLY safety net for the
  app-private DB (`allowBackup=false` on purpose). Export serializes all 7
  tables into one JSON doc (org.json, keys mirror DB columns, `format` field
  gates imports); import is REPLACE-ALL (clear + insert in one transaction).
  Settings page wires it over SAF (`CreateDocument`/`OpenDocument`, import
  asks for confirmation first). `Backup.snapshotToDownloads` writes a silent
  JSON snapshot into public `Download/GoWorkBro/` via MediaStore before every
  update install (API 29+; survives an uninstall-reinstall signature break).
  Breaking format changes bump `Backup.FORMAT` — imports of other formats are
  rejected, so old files keep working until explicitly deprecated.
- **Day boundary** — `core/LogicalDay.kt` is the single source of truth: one
  flow emits the current logical day + its window, re-emits on app foreground
  and by itself when midnight passes. Screens must NOT thread refresh
  counters. The day IS the calendar day — the old 熬夜模式 toggle (0–4am
  counted as yesterday) was removed in v3.6. `core/Store.kt#Rollover` resets
  habits on each new day, driven from the AppRoot collector.
- **Focus statistics** — `focus_logs` rows are written with `Dates.todayKey()`
  and are kept forever:
  home/overview read them per logical day (`FocusDao.observeDayStats` /
  `observeDayByTodo`), the overview shows them as a per-todo donut.
- **Sleep records (v3.3)** — `sleep_records`: ONE row per SLEEP — a nap and
  a night are the same thing, a day can hold any number of them. Recording
  UI: two-step wheel bottom sheet (`ui/sleep/SleepScreen.kt`) — step 1
  sleep start (default now−7h, wheel now−48h → now−15min), step 2 wake
  (default now, wheel sleep+15min → now); the ONLY hard bound is "never
  the future". 15-min steps, wheels stop at their ends (no wrap), light
  date label under the wheel auto-crosses midnight and names 今天 / 昨天 /
  前天 (`SleepOps.relativeDateLabel`). Stats in `core/SleepOps.kt`: a record
  is filed under the calendar day it ENDED on (the wake day), so a
  23:00–07:00 night already counts for the morning it ended on; per-day totals
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
  on modern Android). The release's `.sha256` asset is stashed next to the
  APK at download time; `refreshPendingApk` (startup scan) and
  `startInstall` both verify the digest and delete corrupted downloads —
  releases without a checksum file skip verification gracefully. The
  download-complete receiver is `RECEIVER_NOT_EXPORTED` (system broadcasts
  still arrive; third-party apps cannot spoof them). Pending-APK version is
  derived from the file name (`GoWorkBro-update-<version>.apk`); stale APKs
  (<= the running version) and orphaned sidecars are deleted on startup.
- **Data-access scale rules** (learned 2026-10: history only grows): home
  reads SQL aggregates (`observeCounts`) and the wake-time calendar window
  (`SleepOps.dayWindow` + `SleepDao.observeWakesBetween`), never `observeAll`;
  the sleep stats page reads a 370-day bounded window (`SleepOps.historySince`);
  the todo page renders undone in full but done history capped at 50 with a
  展开更多 row. High-frequency state (the focus timer's 4Hz elapsed) is
  collected INSIDE the smallest composable that reads it (ActiveTimerBar),
  not in the HomeScreen body.

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
- Builds: `:app:assembleDebug`, `:app:assembleRelease` (`gradlew.bat` is part of the standard Gradle wrapper set and
  is regenerated on every wrapper upgrade; this machine is Linux-only, never
  run it.)
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
  app-data reset, guest-clock faking for midnight day-boundary tests. Prefer it
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
- **Version numbering** (surveyed 2026-10 from curl/OpenSSL/CPython/Signal/
  VS Code practices; see also CHANGELOG's SemVer note): the number is a
  compatibility promise to the user — for an app, the "API" is the user's
  data and established workflows. Pick by what changed:

  | Change | Bump |
  |---|---|
  | Bug fix, crash, perf-only, cosmetic tweak | PATCH |
  | New feature/setting/screen, data auto-migrates, backup FORMAT still imports old files | MINOR |
  | Breaking backup format without an import path, deliberately data-dropping migration, module-level rewrite | MAJOR |
  | Pure refactor / dependency bump | rides along, no own release |

  Heuristic: if the CHANGELOG entry needs a “升级会…” warning sentence
  (data wiped, uninstall required), that is a MAJOR signal — 3.2.0 and
  3.3.0 both qualified and shipped as MINOR only because the single user
  consented. `versionCode` is orthogonal: +1 every release, always.

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
