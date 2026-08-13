#!/usr/bin/env python3
"""One-shot refactor helper: move lib files to the new architecture layout and
rewrite relative imports to package:goworkbro/... absolute imports.

Run from the repo root AFTER the target directories exist.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIB = os.path.join(ROOT, "lib")
PKG = "package:goworkbro/"

# (old relative path from lib/, new relative path under lib/)
MOVES = [
    # --- screens -> features ---
    ("screens/auth_screen.dart", "features/auth/auth_screen.dart"),
    ("screens/todo_screen.dart", "features/todos/todo_screen.dart"),
    ("screens/countdown_screen.dart", "features/countdowns/countdown_screen.dart"),
    ("screens/today_screen.dart", "features/today/today_screen.dart"),
    ("screens/timer_screen.dart", "features/timer/timer_screen.dart"),
    ("screens/me_screen.dart", "features/me/me_screen.dart"),
    # --- shared widgets -> feature widgets ---
    ("widgets/todo/todo_card.dart", "features/todos/widgets/todo_card.dart"),
    ("widgets/todo/habit_card.dart", "features/todos/widgets/habit_card.dart"),
    ("widgets/todo/todo_edit_dialog.dart", "features/todos/widgets/todo_edit_dialog.dart"),
    ("widgets/todo/habit_edit_dialog.dart", "features/todos/widgets/habit_edit_dialog.dart"),
    ("widgets/countdown/add_countdown_sheet.dart", "features/countdowns/widgets/add_countdown_sheet.dart"),
    ("widgets/countdown/countdown_card.dart", "features/countdowns/widgets/countdown_card.dart"),
    ("widgets/today/focus_data_section.dart", "features/today/widgets/focus_data_section.dart"),
    ("widgets/today/sessions_card.dart", "features/today/widgets/sessions_card.dart"),
    ("widgets/today/source_pie_card.dart", "features/today/widgets/source_pie_card.dart"),
    ("widgets/today/total_focus_card.dart", "features/today/widgets/total_focus_card.dart"),
    ("widgets/today/ustc_news_section.dart", "features/today/widgets/ustc_news_section.dart"),
    ("widgets/today/weekly_bar_card.dart", "features/today/widgets/weekly_bar_card.dart"),
    # --- core services ---
    ("services/app_locale.dart", "core/l10n/app_locale.dart"),
    ("services/supabase_config.dart", "core/config/supabase_config.dart"),
    ("services/device_identity_service.dart", "core/device/device_identity_service.dart"),
    ("services/database_service.dart", "core/database/app_database.dart"),
    ("services/sync_service.dart", "core/sync/sync_service.dart"),
    ("services/sleep_chart_utils.dart", "core/utils/sleep_chart_utils.dart"),
    # --- theme ---
    ("theme/app_theme.dart", "core/theme/app_theme.dart"),
]

def resolve_rel(from_dir, imp):
    """Resolve a relative dart import to a path relative to lib/, or None."""
    base = os.path.normpath(os.path.join(from_dir, imp))
    # strip .dart or leave as-is; we only care about .dart files
    if not base.endswith(".dart"):
        base += ".dart"
    rel = os.path.relpath(base, LIB)
    if rel.startswith(".."):
        return None  # outside lib (shouldn't happen)
    return rel.replace("\\", "/")

def main():
    # 1. mkdir targets + git mv
    for old, new in MOVES:
        oldp, newp = os.path.join(LIB, old), os.path.join(LIB, new)
        if not os.path.exists(oldp):
            print(f"SKIP (missing): {old}")
            continue
        os.makedirs(os.path.dirname(newp), exist_ok=True)
        if os.path.exists(newp):
            print(f"SKIP (exists): {new}")
            continue
        subprocess.run(["git", "mv", oldp, newp], cwd=ROOT, check=True)
        print(f"moved {old} -> {new}")

    # 2. rewrite imports to package:goworkbro/...
    dart_files = []
    for dirpath, _, files in os.walk(LIB):
        for f in files:
            if f.endswith(".dart"):
                dart_files.append(os.path.join(dirpath, f))

    import_re = re.compile(r'''^\s*import\s+['"]([^'"]+)['"];''')
    changed = []
    for path in dart_files:
        with open(path, encoding="utf-8") as fh:
            lines = fh.readlines()
        out = []
        modified = False
        for line in lines:
            m = import_re.match(line)
            if not m:
                out.append(line)
                continue
            imp = m.group(1)
            if imp.startswith("package:"):
                out.append(line)
                continue
            if imp.startswith("dart:"):
                out.append(line)
                continue
            from_dir = os.path.dirname(path)
            rel = resolve_rel(from_dir, imp)
            if rel is None:
                print(f"WARN {path}: cannot resolve import {imp}")
                out.append(line)
                continue
            new_imp = PKG + rel
            indent = line[: len(line) - len(line.lstrip())]
            out.append(f"{indent}import '{new_imp}';\n")
            modified = True
        if modified:
            with open(path, "w", encoding="utf-8") as fh:
                fh.writelines(out)
            changed.append(os.path.relpath(path, LIB))
    print(f"\nrewrote imports in {len(changed)} files")
    for c in sorted(changed):
        print("  ", c)

if __name__ == "__main__":
    main()
