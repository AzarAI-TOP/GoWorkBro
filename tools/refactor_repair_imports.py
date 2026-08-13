#!/usr/bin/env python3
"""Repair package:goworkbro/... imports broken by the file moves.

Strategy: for every import that does not resolve to an existing file, look up
the basename in a canonical map (file names are unique across lib/).
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIB = os.path.join(ROOT, "lib")
PKG = "package:goworkbro/"

# basename -> canonical path under lib/
CANON = {}
for dirpath, _, files in os.walk(LIB):
    for f in files:
        if f.endswith(".dart"):
            rel = os.path.relpath(os.path.join(dirpath, f), LIB).replace("\\", "/")
            CANON.setdefault(f, rel)  # first hit wins; models/ etc are unique

def resolve_rel(from_dir, imp):
    base = os.path.normpath(os.path.join(from_dir, imp))
    if not base.endswith(".dart"):
        base += ".dart"
    rel = os.path.relpath(base, LIB)
    if rel.startswith(".."):
        return None
    return rel.replace("\\", "/")

import_re = re.compile(r'''^(\s*import\s+['"])([^'"]+)(['"];)''')
targets = ["lib", "test", "integration_test"]
changed, fixed = [], 0

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
            out, modified = [], False
            for line in lines:
                m = import_re.match(line)
                if not m:
                    out.append(line)
                    continue
                imp = m.group(2)
                if imp.startswith("dart:") or imp.startswith("package:") and not imp.startswith(PKG):
                    out.append(line)
                    continue
                target_rel = None
                if imp.startswith(PKG):
                    target_rel = imp[len(PKG):]
                else:
                    target_rel = resolve_rel(os.path.dirname(p), imp)
                if target_rel is None:
                    out.append(line)
                    continue
                target_abs = os.path.join(LIB, target_rel.replace("/", os.sep))
                if os.path.exists(target_abs):
                    out.append(line)  # fine as-is
                    continue
                # broken: look up by basename
                base = os.path.basename(target_rel)
                canon = CANON.get(base)
                if canon is None:
                    out.append(line)
                    continue
                out.append(f"{m.group(1)}{PKG}{canon}{m.group(3)}\n")
                fixed += 1
                modified = True
            if modified:
                with open(p, "w", encoding="utf-8") as fh:
                    fh.writelines(out)
                changed.append(os.path.relpath(p, ROOT))

print(f"fixed {fixed} imports across {len(changed)} files")
for c in sorted(changed):
    print("  ", c)
