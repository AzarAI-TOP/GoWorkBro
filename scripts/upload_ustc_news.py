#!/usr/bin/env python3
"""Upload USTC daily news to Supabase.

Usage:
  python upload_ustc_news.py <date> <markdown_file_path>

Reads the markdown file, strips frontmatter, extracts H1 title,
and upserts into the Supabase ustc_news table.

Environment variables (optional, override defaults):
  SUPABASE_URL       — Supabase project URL
  SUPABASE_ANON_KEY  — Supabase anon/publishable key
"""

import sys
import re
import os
import json
import urllib.request

# ============ Config ============

SUPABASE_URL = os.environ.get(
    "SUPABASE_URL",
    "https://icsulquyravumynznisa.supabase.co",
)
SUPABASE_ANON_KEY = os.environ.get(
    "SUPABASE_ANON_KEY",
    "sb_publishable_HRd65D5M-BIcBakD3XQBSQ_iG9lC6fK",
)

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
    """Upsert news into Supabase ustc_news table."""
    url = f"{SUPABASE_URL}/rest/v1/ustc_news"
    headers = {
        "apikey": SUPABASE_ANON_KEY,
        "Authorization": f"Bearer {SUPABASE_ANON_KEY}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates",  # upsert
    }
    payload = json.dumps(
        {
            "date": date_str,
            "title": title,
            "content": content,
        }
    ).encode("utf-8")

    req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            if resp.status in (200, 201):
                print(f"✅ Uploaded USTC news for {date_str}: {title}")
                return True
            else:
                print(f"❌ Upload failed: HTTP {resp.status}")
                return False
    except Exception as e:
        print(f"❌ Upload error: {e}")
        return False


def main():
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
