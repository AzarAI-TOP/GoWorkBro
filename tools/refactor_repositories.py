#!/usr/bin/env python3
"""Migrate DatabaseService.* calls to the new Repository classes."""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIB = os.path.join(ROOT, "lib")

REPL = [
    ("DatabaseService.getSetting(", "SettingsRepository.get("),
    ("DatabaseService.setSetting(", "SettingsRepository.set("),
    ("DatabaseService.delete(", "SettingsRepository.delete("),
    ("DatabaseService.incrementSettingCounterOnceForTesting(", "SettingsRepository.incrementCounterOnceForTesting("),
    ("DatabaseService.incrementSettingCounterOnce(", "SettingsRepository.incrementCounterOnce("),
    ("DatabaseService.getTodos(", "TodoRepository.getAll("),
    ("DatabaseService.insertTodo(", "TodoRepository.insert("),
    ("DatabaseService.updateTodo(", "TodoRepository.update("),
    ("DatabaseService.deleteTodo(", "TodoRepository.deleteById("),
    ("DatabaseService.rollOverTodos(", "TodoRepository.rollOver("),
    ("DatabaseService.getTodoById(", "TodoRepository.getById("),
    ("DatabaseService.upsertTodoFromRemote(", "TodoRepository.upsertFromRemote("),
    ("DatabaseService.getHabits(", "HabitRepository.getAll("),
    ("DatabaseService.insertHabit(", "HabitRepository.insert("),
    ("DatabaseService.updateHabit(", "HabitRepository.update("),
    ("DatabaseService.deleteHabit(", "HabitRepository.deleteById("),
    ("DatabaseService.getHabitById(", "HabitRepository.getById("),
    ("DatabaseService.resetHabitsForNewDay(", "HabitRepository.resetForNewDay("),
    ("DatabaseService.upsertHabitFromRemote(", "HabitRepository.upsertFromRemote("),
    ("DatabaseService.getCountdowns(", "CountdownRepository.getAll("),
    ("DatabaseService.insertCountdown(", "CountdownRepository.insert("),
    ("DatabaseService.updateCountdown(", "CountdownRepository.update("),
    ("DatabaseService.deleteCountdown(", "CountdownRepository.deleteById("),
    ("DatabaseService.getCountdownById(", "CountdownRepository.getById("),
    ("DatabaseService.cleanupExpiredCountdowns(", "CountdownRepository.cleanupExpired("),
    ("DatabaseService.upsertCountdownFromRemote(", "CountdownRepository.upsertFromRemote("),
    ("DatabaseService.insertFocusSession(", "FocusRepository.insert("),
    ("DatabaseService.getFocusSessionsByDate(", "FocusRepository.getByDate("),
    ("DatabaseService.getFocusSessionsDateRange(", "FocusRepository.getDateRange("),
    ("DatabaseService.getAllFocusSessions(", "FocusRepository.getAll("),
    ("DatabaseService.insertFocusSessionIfNotExists(", "FocusRepository.insertIfNotExists("),
    ("DatabaseService.getSleepRecords(", "SleepRepository.getAll("),
    ("DatabaseService.upsertSleepRecord(", "SleepRepository.upsert("),
    ("DatabaseService.getSleepRecordById(", "SleepRepository.getById("),
    ("DatabaseService.upsertSleepFromRemote(", "SleepRepository.upsertFromRemote("),
    ("DatabaseService.getCachedUstcNews(", "NewsCacheRepository.getCached("),
    ("DatabaseService.getLatestCachedUstcNews(", "NewsCacheRepository.getLatest("),
    ("DatabaseService.cacheUstcNews(", "NewsCacheRepository.cache("),
    ("DatabaseService.database", "AppDatabase.database"),
    ("DatabaseService.migrateForTesting", "AppDatabase.migrateForTesting"),
    ("DatabaseService.closeForTesting", "AppDatabase.closeForTesting"),
    ("DatabaseService.deleteAllData", "AppDatabase.deleteAllData"),
]

# repository class -> import path
REPO_IMPORTS = {
    "TodoRepository": "package:goworkbro/core/database/repositories/todo_repository.dart",
    "HabitRepository": "package:goworkbro/core/database/repositories/habit_repository.dart",
    "CountdownRepository": "package:goworkbro/core/database/repositories/countdown_repository.dart",
    "FocusRepository": "package:goworkbro/core/database/repositories/focus_repository.dart",
    "SleepRepository": "package:goworkbro/core/database/repositories/sleep_repository.dart",
    "SettingsRepository": "package:goworkbro/core/database/repositories/settings_repository.dart",
    "NewsCacheRepository": "package:goworkbro/core/database/repositories/news_cache_repository.dart",
}

IMPORT_RE = re.compile(r'''^\s*import\s+['"]([^'"]+)['"];''')

targets = ["lib", "test", "integration_test"]
changed_files = []
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
                lines = fh.readlines()
            text = "".join(lines)
            orig = text
            for old, new in REPL:
                text = text.replace(old, new)
            if text == orig:
                continue
            # collect which repositories are referenced now
            needed = {cls for cls in REPO_IMPORTS if re.search(rf"\b{cls}\b", text)}
            # add missing imports
            imports = {m.group(1) for m in IMPORT_RE.finditer(text)}
            additions = [REPO_IMPORTS[c] for c in sorted(needed) if REPO_IMPORTS[c] not in imports]
            if additions:
                # insert after the last existing import line
                lines = text.splitlines(keepends=True)
                last_import_idx = -1
                for i, line in enumerate(lines):
                    if IMPORT_RE.match(line):
                        last_import_idx = i
                if last_import_idx >= 0:
                    lines = lines[:last_import_idx + 1] + [f"import '{a}';\n" for a in additions] + lines[last_import_idx + 1:]
                else:
                    lines = [f"import '{a}';\n" for a in additions] + lines
                text = "".join(lines)
            with open(p, "w", encoding="utf-8") as fh:
                fh.write(text)
            changed_files.append(os.path.relpath(p, ROOT))

print(f"updated {len(changed_files)} files")
for c in sorted(changed_files):
    print("  ", c)
