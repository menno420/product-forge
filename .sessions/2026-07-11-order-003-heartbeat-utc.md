# Session — ORDER 003 — heartbeat UTC ground truth

> **Status:** `complete`

📊 Model: opus-4.8 · high · docs+tooling (heartbeat future-stamp fix + guard)

Time: 2026-07-11 · lane: builder · continuous-mode

## What this session did

Executed ORDER 003 — corrected the future-dated `control/status.md` heartbeat and added a
repo-owned anti-recurrence guard. Close-out:

- **Heartbeat corrected:** `control/status.md` `updated:` was `2026-07-11T12:00:00Z`
  (~7h ahead of real UTC, silently passing the kit gate); re-derived from `date -u` at
  write time.
- **Convention rule** added to `CONVENTIONS.md` (under Heartbeat-before-work) and
  `.sessions/README.md`: the stamp is machine-derived, never typed.
- **Guard anchors** (repo-owned, kit untouched): `scripts/check-heartbeat.py` parses the
  `updated:` line (`_UPDATED_RE` → `check_file`) and fails on any stamp > now + 300s;
  `.github/workflows/heartbeat-guard.yml` runs it on PRs/pushes touching status.md.
  Tested green (2020 stamp → exit 0) and red (2099 stamp → exit 1).
- **Gate verdict:** `python3 bootstrap.py check --strict` exit 0 after this flip.
- **Guard recipe (for a later session):** to extend future-stamp coverage, the anchor is
  `check_file` in `scripts/check-heartbeat.py` (tolerance constant `_TOLERANCE_S`); test
  target is the green/red pair `/tmp/hb-good.md` (2020) and `/tmp/hb-bad.md` (2099).

- **📊 Model:** opus-4.8 · high · docs+tooling (heartbeat future-stamp fix + guard)

## 💡 Session idea

ORDER 003 — correct future-dated control/status.md heartbeat + add anti-recurrence guard.
Root cause: the `updated:` stamp is hand-typed each session and bootstrap.py only checks
for STALE/too-old (>72h), never a FUTURE value, so a round `12:00:00Z` guess ~7h ahead of
real UTC passed the gate silently. Fix per upgrade-never-fork: re-derive the stamp from
`date -u` at write time and add a REPO-OWNED guard (new script + new workflow) rather than
editing the kit's bootstrap.py or substrate-gate.yml.

## ⟲ Previous-session review

ORDER 002 (model-attribution ground truth) landed at merge 8c64db4 (PR #17): it confirmed
`📊 Model:` is already a machine-enforced needle and committed a card with a real
family-level Model line (opus-4.8). This session reuses that same up-front marker-preflight
discipline to avoid a gate round-trip.
