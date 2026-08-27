#!/usr/bin/env python3
"""Upload USTC daily news to Supabase.

Usage:
  python upload_ustc_news.py <date> <markdown_file_path>

Reads the markdown file, strips frontmatter, extracts the H1 title, and
upserts the daily edition through the validated `upsert_ustc_news` RPC.

The RPC is SECURITY DEFINER and validates the date (ISO format, not in the
future) and content limits. Execute is limited to the service_role, so a
server-side secret is required for ingestion — it is never committed and
only lives in your environment.

Environment variables:
  SUPABASE_URL               — Supabase project URL (has a default)
  SUPABASE_SERVICE_ROLE_KEY  — service-role key (REQUIRED, no default)
"""

import sys
import re
import os
import json
import urllib.request
import urllib.error

# ============ Config ============

SUPABASE_URL = os.environ.get(
    "SUPABASE_URL",
    "https://icsulquyravumynznisa.supabase.co",
)
# Required — the legacy anon-key ingestion path was revoked; never hardcode
# a fallback here.
SUPABASE_SERVICE_ROLE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY")

VAULT_PATH = os.environ.get(
    "OBSIDIAN_VAULT",
    r"C:\Users\ASUS\Documents\Notes",
)

USTC_NEWS_FOLDER = "USTC 每日要闻"

# ============ Helpers ============


def strip_frontmatter(md: str) -> str:
    """Remove YAML frontmatter (--- ... ---) from markdown."""
    if md.startswith("---"):
        end = md.find("---", 3)
        if end > 0:
            return md[end + 3 :].strip()
    return md.strip()


def extract_title(md: str) -> str:
    """Extract the first H1 heading as the title."""
    match = re.search(r"^#\s+(.+)$", md, re.MULTILINE)
    if match:
        return match.group(1).strip()
    return ""


def upload(date_str: str, title: str, content: str) -> bool:
    """Upsert news through the validated Supabase RPC."""
    url = f"{SUPABASE_URL}/rest/v1/rpc/upsert_ustc_news"
    headers = {
        "apikey": SUPABASE_SERVICE_ROLE_KEY,
        "Authorization": f"Bearer {SUPABASE_SERVICE_ROLE_KEY}",
        "Content-Type": "application/json",
    }
    payload = json.dumps(
        {
            "p_date": date_str,
            "p_title": title,
            "p_content": content,
        }
    ).encode("utf-8")

    req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            if resp.status in (200, 201):
                print(f"✅ Uploaded USTC news for {date_str}: {title}")
                return True
            print(f"❌ Upload failed: HTTP {resp.status}: {body[:200]}")
            return False
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace") if e.fp else ""
        print(f"❌ Upload error: HTTP {e.code}: {body[:300]}")
        return False
    except Exception as e:
        print(f"❌ Upload error: {e}")
        return False


def main():
    if not SUPABASE_SERVICE_ROLE_KEY:
        print(
            "❌ SUPABASE_SERVICE_ROLE_KEY is not set. "
            "Export it before uploading (never commit it)."
        )
        sys.exit(1)

    if len(sys.argv) >= 3:
        date_str = sys.argv[1]
        file_path = sys.argv[2]
    elif len(sys.argv) == 1:
        # Auto-detect today's file
        from datetime import date

        date_str = date.today().isoformat()
        file_path = os.path.join(VAULT_PATH, USTC_NEWS_FOLDER, f"{date_str}.md")
    else:
        print("Usage: python upload_ustc_news.py <YYYY-MM-DD> <markdown_file_path>")
        sys.exit(1)

    if not os.path.exists(file_path):
        print(f"❌ File not found: {file_path}")
        sys.exit(1)

    with open(file_path, "r", encoding="utf-8") as f:
        raw = f.read()

    content = strip_frontmatter(raw)
    title = extract_title(content) or f"USTC 每日要闻 — {date_str}"

    success = upload(date_str, title, content)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
