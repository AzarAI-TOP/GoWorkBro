#!/usr/bin/env python
"""Read release metadata without printing Supabase secrets."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "lib" / "services" / "local_config.dart"
PUBSPEC = ROOT / "pubspec.yaml"
INSTALLER = ROOT / "windows" / "installer" / "goworkbro.iss"


def dart_string(name: str) -> str:
    text = CONFIG.read_text(encoding="utf-8")
    match = re.search(
        rf"\b{name}\b\s*=\s*(['\"])(.*?)\1\s*;",
        text,
        flags=re.DOTALL,
    )
    if not match or not match.group(2).strip():
        raise SystemExit(f"Could not parse {name} from {CONFIG}")
    return match.group(2).strip()


def version() -> str:
    pubspec = PUBSPEC.read_text(encoding="utf-8")
    pub_match = re.search(r"^version:\s*([^+\s]+)", pubspec, flags=re.MULTILINE)
    installer = INSTALLER.read_text(encoding="utf-8")
    iss_match = re.search(r'#define\s+MyAppVersion\s+"([^"]+)"', installer)
    if not pub_match or not iss_match:
        raise SystemExit("Could not parse release versions")
    pub_version = pub_match.group(1)
    iss_version = iss_match.group(1)
    if pub_version != iss_version:
        raise SystemExit(
            f"Version mismatch: pubspec={pub_version}, installer={iss_version}"
        )
    return pub_version


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: release_config.py [url|key|version]")
    command = sys.argv[1]
    if command == "url":
        print(dart_string("localSupabaseUrl"))
    elif command == "key":
        print(dart_string("localSupabaseAnonKey"))
    elif command == "version":
        print(version())
    else:
        raise SystemExit(f"Unknown command: {command}")


if __name__ == "__main__":
    main()
