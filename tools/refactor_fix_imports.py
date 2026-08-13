#!/usr/bin/env python3
"""Fix package:goworkbro/... imports whose paths changed during the refactor."""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# old package path prefix -> new path under lib/
MAPPINGS = [
    ("package:goworkbro/services/app_locale.dart", "package:goworkbro/core/l10n/app_locale.dart"),
    ("package:goworkbro/services/database_service.dart", "package:goworkbro/core/database/app_database.dart"),
    ("package:goworkbro/services/device_identity_service.dart", "package:goworkbro/core/device/device_identity_service.dart"),
    ("package:goworkbro/services/sleep_chart_utils.dart", "package:goworkbro/core/utils/sleep_chart_utils.dart"),
    ("package:goworkbro/services/supabase_config.dart", "package:goworkbro/core/config/supabase_config.dart"),
    ("package:goworkbro/services/sync_service.dart", "package:goworkbro/core/sync/sync_service.dart"),
    ("package:goworkbro/theme/app_theme.dart", "package:goworkbro/core/theme/app_theme.dart"),
    ("package:goworkbro/screens/auth_screen.dart", "package:goworkbro/features/auth/auth_screen.dart"),
    ("package:goworkbro/screens/todo_screen.dart", "package:goworkbro/features/todos/todo_screen.dart"),
    ("package:goworkbro/screens/countdown_screen.dart", "package:goworkbro/features/countdowns/countdown_screen.dart"),
    ("package:goworkbro/screens/today_screen.dart", "package:goworkbro/features/today/today_screen.dart"),
    ("package:goworkbro/screens/timer_screen.dart", "package:goworkbro/features/timer/timer_screen.dart"),
    ("package:goworkbro/screens/me_screen.dart", "package:goworkbro/features/me/me_screen.dart"),
    ("package:goworkbro/widgets/todo/", "package:goworkbro/features/todos/widgets/"),
    ("package:goworkbro/widgets/countdown/", "package:goworkbro/features/countdowns/widgets/"),
    ("package:goworkbro/widgets/today/", "package:goworkbro/features/today/widgets/"),
]

targets = ["lib", "test", "integration_test"]
changed = []
for d in targets:
    root = os.path.join(ROOT, d)
    if not os.path.isdir(root):
        continue
    for dirpath, _, files in os.walk(root):
        for f in files:
            if not f.endswith(".dart"):
                continue
            p = os.path.join(dirpath, f)
            with open(p, encoding="utf-8") as fh:
                content = fh.read()
            orig = content
            for old, new in MAPPINGS:
                content = content.replace(old, new)
            if content != orig:
                with open(p, "w", encoding="utf-8") as fh:
                    fh.write(content)
                changed.append(os.path.relpath(p, ROOT))

print(f"updated {len(changed)} files")
for c in sorted(changed):
    print("  ", c)
