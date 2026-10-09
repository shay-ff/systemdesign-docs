#!/usr/bin/env python3
"""Rewrite directory-style markdown links to explicit README targets,
using suggestions from the mkdocs build log."""
import re
import sys
from pathlib import Path
from collections import defaultdict

ROOT = Path(__file__).resolve().parents[1]
LOG = sys.argv[1] if len(sys.argv) > 1 else "/tmp/mkdocs-log2.txt"

pat = re.compile(
    r"Doc file '([^']+)' contains an unrecognized relative link '([^']+)'.*Did you mean '([^']+)'"
)

edits = defaultdict(set)  # source file -> {(old_link, new_link)}
for line in Path(LOG).read_text().splitlines():
    m = pat.search(line)
    if m:
        edits[m.group(1)].add((m.group(2), m.group(3)))

total = 0
for rel, pairs in sorted(edits.items()):
    path = ROOT / rel
    text = path.read_text()
    orig = text
    for old, new in pairs:
        needle = f"]({old})"
        repl = f"]({new})"
        count = text.count(needle)
        if count:
            text = text.replace(needle, repl)
            total += count
        else:
            print(f"  MISS: {rel}: {needle}")
    if text != orig:
        path.write_text(text)
        print(f"  {rel}: {len(pairs)} unique link(s)")

print(f"Total replacements: {total}")
