# Session — ORDER 003 — heartbeat UTC ground truth

> **Status:** `in-progress`

📊 Model: opus-4.8 · high · docs+tooling (heartbeat future-stamp fix + guard)

Time: 2026-07-11 · lane: builder · continuous-mode

## What this session did

Executing ORDER 003 — correct the future-dated `control/status.md` heartbeat and add a
repo-owned anti-recurrence guard. (Close-out written at flip to `complete`.)

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
