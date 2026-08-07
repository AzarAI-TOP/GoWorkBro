# GoWorkBro

A clean, geometric todo & focus management app inspired by 番茄TODO.
Built with Flutter for Android and Windows desktop, with Supabase cloud sync.

## Features

| Module | Description |
|--------|-------------|
| **待办 (Todo)** | TODO items with forward/backward/no timer, "repeat tomorrow" auto-recreate, habits with daily reset & custom units |
| **倒计时 (Countdown)** | Create countdown events with color coding, auto-delete next day, live remaining time |
| **Today** | Focus statistics with pie chart, 7-day bar chart, session list, USTC daily news (Markdown rendered with LXGW WenKai font) |
| **Me** | Profile, wake/workout/sleep check-in, statistics, settings (language, theme, cloud sync, data management) |

## Architecture

```
lib/
├── main.dart                  # App entry, providers, auth gate, navigation shell
├── models/                    # Data models (Todo, Habit, Countdown, FocusSession, SleepRecord)
├── providers/
│   └── app_provider.dart      # Central state (ChangeNotifier), local-first with Supabase sync
├── services/
│   ├── app_locale.dart        # i18n provider (zh/en) + S translation class
│   ├── api_service.dart       # USTC news fetching (Supabase → local vault fallback)
│   ├── database_service.dart  # SQLite (sqflite_common_ffi) CRUD, migrations, daily rollover
│   ├── sync_service.dart      # Supabase sync (push/pull/realtime)
│   ├── supabase_config.dart   # Supabase URL/key via --dart-define
│   ├── tray_service.dart      # Windows system tray icon
│   └── update_service.dart    # GitHub release update checker
├── screens/                   # 6 screens (todo, timer, countdown, today, me, auth)
├── theme/
│   └── app_theme.dart         # Light/dark themes, chart colors
└── widgets/                   # Extracted widgets (cards, dialogs, chart components)
```

### Data Flow

```
User Action → AppProvider (in-memory update + notifyListeners)
                ↓                          ↓
          SQLite (local-first)      Supabase (background push)
                ↑
          SyncService.pullAll() ← Supabase Realtime
```

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
# Windows release
flutter build windows --release --dart-define=SUPABASE_URL=... --dart-define=SUPABASE_ANON_KEY=...

# Android APK
flutter build apk --release --dart-define=SUPABASE_URL=... --dart-define=SUPABASE_ANON_KEY=...
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
- **Fonts**: LXGW WenKai (霞鹜文楷)
- **Desktop**: window_manager + tray_manager

## License

© 2026 AzarAI. All rights reserved.
