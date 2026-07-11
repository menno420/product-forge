#!/usr/bin/env python3
"""Guard against future-dated status.md heartbeats (ORDER 003).

The `updated:` line in control/status.md is hand-authored each session and
bootstrap.py's only time check flags STALE / too-old (>72h), never a FUTURE
value — so a round, invented stamp ahead of real UTC passes the kit gate
silently and corrupts fleet freshness ranking. This repo-owned guard closes
that gap without forking the kit (bootstrap.py / substrate-gate.yml stay
untouched per upgrade-never-fork): it rejects any `updated:` stamp that is
ahead of now.

Usage: check-heartbeat.py [FILE ...]
  No args -> checks control/status.md plus any control/status-*.md shards.
"""
from __future__ import annotations

import glob
import re
import sys
from datetime import datetime, timezone

_UPDATED_RE = re.compile(r"^updated:\s*(\S+)", re.MULTILINE)
_TOLERANCE_S = 300  # 5 min clock-skew grace


def _default_targets() -> list[str]:
    targets = ["control/status.md"]
    targets.extend(sorted(glob.glob("control/status-*.md")))
    return targets


def _parse(stamp: str) -> datetime | None:
    try:
        dt = datetime.fromisoformat(stamp.replace("Z", "+00:00"))
    except ValueError:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def check_file(path: str, now: datetime) -> bool:
    """Return True if the file is OK (or has no parseable stamp), False on future."""
    try:
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
    except OSError as exc:
        print(f"WARN: {path}: cannot read ({exc}); skipping")
        return True
    match = _UPDATED_RE.search(text)
    if not match:
        print(f"WARN: {path}: no `updated:` stamp; skipping (bootstrap owns that check)")
        return True
    dt = _parse(match.group(1))
    if dt is None:
        print(f"WARN: {path}: unparseable `updated:` stamp {match.group(1)!r}; skipping")
        return True
    if dt > now:
        ahead = int((dt - now).total_seconds())
        if ahead > _TOLERANCE_S:
            print(f"FAIL: {path} updated: {match.group(1)} is {ahead}s in the future (now {now.isoformat()})")
            return False
    return True


def main(argv: list[str]) -> int:
    now = datetime.now(timezone.utc)
    targets = argv[1:] or _default_targets()
    ok = True
    for path in targets:
        if not check_file(path, now):
            ok = False
    if not ok:
        return 1
    print("OK: heartbeat stamp(s) not in the future")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
