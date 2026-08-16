# GoWorkBro

A clean, geometric todo & focus management app inspired by 番茄TODO.
Built with Flutter for Android and Windows desktop, with Supabase cloud sync.

## Features

| Module | Description |
|--------|-------------|
| **待办 (Todo)** | TODO items with forward/backward/no timer, "repeat tomorrow" auto-recreate, habits with daily reset & custom units |
| **倒计时 (Countdown)** | Create countdown events with color coding, auto-delete next day, live remaining time |
| **Today** | Focus statistics with pie chart, 7-day bar chart, session list, USTC daily news (Markdown rendered with LXGW WenKai font, date-keyed local cache) |
| **Me** | Profile & avatar, wake/workout/sleep check-in with trend charts (bedtime/wake time/duration), lifetime statistics, settings (language, theme, cloud sync, data management) |

## Architecture

Feature-first layout with a layered core. Dependencies point inward:
UI (`features/`) → state (`providers/`) → domain access (`core/database/repositories/`)
→ SQLite / Supabase.

```
lib/
├── main.dart                  # Bootstrap only: window, Supabase, locale init
├── app/                       # App shell
│   ├── app.dart               # GoWorkBroApp: providers + MaterialApp
│   ├── auth_gate.dart         # Auth routing (Supabase session / offline)
│   └── app_shell.dart         # Responsive navigation shell (rail / bottom bar)
├── features/                  # Screens grouped by domain, each with widgets/
│   ├── auth/  todos/  countdowns/  today/  timer/  me/
├── providers/
│   └── app_provider.dart      # App state (ChangeNotifier): caches + orchestration
│                              # (rollover, sync trigger, cross-domain stats)
├── core/                      # Infrastructure
│   ├── database/
│   │   ├── app_database.dart  # SQLite connection, schema, migrations, reset
│   │   └── repositories/      # Domain data access (Todo/Habit/Countdown/Focus/
│   │                          # Sleep/Settings/NewsCache) + remote-upsert mapping
│   ├── sync/
│   │   ├── sync_service.dart  # Push/pull/realtime orchestration
│   │   └── sync_table_registry.dart  # Declarative cloud-table registration
│   ├── l10n/app_locale.dart   # i18n provider (zh/en) + S translation class
│   ├── theme/app_theme.dart   # Light/dark themes, CJK fallback chain, chart colors
│   ├── config/supabase_config.dart  # Supabase URL/key via --dart-define
│   ├── device/device_identity_service.dart  # Offline device ID (GWB-…)
│   └── utils/                 # date_utils, sleep_chart_utils
├── models/                    # Data models (Todo, Habit, Countdown, FocusSession, SleepRecord)
└── services/                  # Platform/network services
    ├── api_service.dart       # USTC news fetching (Supabase → Go backend → local vault)
    ├── tray_service.dart      # Windows system tray icon
    └── update_service.dart    # GitHub release update checker
```

### Data Flow

```
User Action → AppProvider (in-memory update + notifyListeners)
                ↓                          ↓
          SQLite (local-first)      Supabase (background push)
                ↑
          SyncService.pullAll() ← Supabase Realtime
```

Sync tables are declared once in `sync_table_registry.dart`; `pullAll` and the
realtime subscriptions are single loops over that registry.

## Getting Started

### Prerequisites

- Flutter 3.44+ (stable channel)
- Dart 3.12+
- For Windows: Visual Studio with C++ desktop workload
- For Android: Android SDK, JDK 21
- A Supabase project (free tier works)

### Setup

1. **Clone & install dependencies:**
   ```bash
   git clone https://github.com/AzarAI-TOP/GoWorkBro.git
   cd GoWorkBro
   flutter pub get
   ```

2. **Configure Supabase credentials:**
   Create `lib/services/local_config.dart` (gitignored):
   ```dart
   const String localSupabaseUrl = 'https://your-project.supabase.co';
   const String localSupabaseAnonKey = 'your-publishable-key';
   ```

3. **Set up Supabase database:**
   Run `supabase/schema.sql` in Supabase Dashboard → SQL Editor.

4. **Run:**
   ```bash
   # Using dev script (reads local_config.dart):
   bash dev.sh

   # Or manually with --dart-define:
   flutter run --dart-define=SUPABASE_URL=... --dart-define=SUPABASE_ANON_KEY=...
   ```

### Build

```bash
# One-shot release build (validates version consistency, reads credentials
# from local_config.dart without printing them):
bash scripts/build_release.sh            # Windows + Android APK
bash scripts/build_release.sh windows    # Windows only
bash scripts/build_release.sh apk        # Android only

# Windows installer (manual step, requires Inno Setup):
ISCC.exe windows/installer/goworkbro.iss

# Manual build without the script:
flutter build windows --release --dart-define=SUPABASE_URL=... --dart-define=SUPABASE_ANON_KEY=...
flutter build apk --release --dart-define=SUPABASE_URL=... --dart-define=SUPABASE_ANON_KEY=...
```

### Windows 任务栏图标不更新（故障排查）

The app icon is embedded in `goworkbro.exe` (via `windows/runner/Runner.rc` → `resources/app_icon.ico`),
so the title bar always shows the current icon. If the **taskbar** still shows an old icon after
upgrading, the Windows icon cache is stale:

1. Refresh the explorer icon cache: run `ie4uinit.exe -show` (the installer does this automatically
   after install/upgrade)
2. Or restart Windows Explorer (taskbar/explorer cache)
3. If the app is **pinned** to the taskbar, unpin and pin it again — pinned entries cache their icon

Verify the exe really embeds the new icon: extract with
`[System.Drawing.Icon]::ExtractAssociatedIcon($exe)` and compare visually.

### Tests

```bash
flutter test                        # unit tests (migrations, sleep utils, i18n)
flutter test integration_test/user_flow_test.dart -d windows   # full user flow (local mode)
flutter test integration_test/auth_test.dart -d windows        # auth screen (offline)
```

## Configuration

### USTC Daily News

The Today screen displays USTC daily news fetched from:
1. **Supabase** `ustc_news` table (cloud, works on all platforms)
2. **Local Obsidian vault** (desktop fallback, path configurable via settings)

To upload news to Supabase, use the included script:
```bash
python scripts/upload_ustc_news.py YYYY-MM-DD /path/to/news.md
```

The script posts through the validated `upsert_ustc_news` RPC (SECURITY
DEFINER, date/content validation, idempotent per day) using the public anon
key — **no secret key is required**. Direct anonymous writes to `ustc_news`
are blocked at both the RLS and grant layers.

### Security (Supabase hardening)

Enforced by schema (`supabase/schema.sql` / migrations):

- RLS on every public table; personal tables allow only `authenticated`
  access to the owner's own rows.
- API surface hardening: `anon`/`authenticated` have no blanket grants;
  privileges follow an explicit grant matrix.
- `user_settings` last-write-wins trigger at the database boundary.
- News ingestion only via the validated RPC; anonymous table writes denied.

Dashboard checklist (one-time, cannot be applied via migrations):

1. **Authentication → Sign In / Up → User Signups** → turn OFF
   **Allow anonymous sign-ins**.
2. **Authentication → Password Protection** → turn ON
   **Detect leaked passwords** (if available on your plan).
3. **Authentication → Password Protection** → set
   **Minimum password length** to `10` (matches app client validation).
4. **Authentication → Sessions** → turn ON
   **Enforce single session per user**.
5. (Optional) **Authentication → Sign In / Up** → turn ON
   **Secure email change**.

Verify with:
```bash
supabase db advisors --linked --type security --level info --fail-on none
```
Anonymous-access and leaked-password warnings should disappear.

Emergency rollback of the API surface hardening (restores blanket grants;
RLS policies still filter rows):

```sql
grant all on all tables in schema public to anon, authenticated;
grant usage on all sequences in schema public to anon, authenticated;
grant execute on all functions in schema public to anon, authenticated;
alter default privileges in schema public grant all on tables to anon, authenticated;
alter default privileges in schema public grant usage on sequences to anon, authenticated;
alter default privileges in schema public grant execute on functions to anon, authenticated;
```

### Windows Tray

On Windows, closing the window hides the app to the system tray.
Right-click the tray icon to show the window or quit.

## Tech Stack

- **Flutter** 3.44 + Dart 3.12
- **State**: Provider (ChangeNotifier)
- **Database**: SQLite via sqflite_common_ffi
- **Cloud**: Supabase (Auth, Postgres, Realtime)
- **Charts**: fl_chart
- **Markdown**: flutter_markdown
- **Fonts**: 0xProto (UI) + LXGW WenKai (霞鹜文楷, news markdown)
- **Desktop**: window_manager + tray_manager

## License

© 2026 AzarAI. All rights reserved.
