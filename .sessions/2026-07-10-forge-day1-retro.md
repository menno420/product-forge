# Session — forge day-1 retro (builder lane, continuous-mode)

> **Status:** `complete`

📊 Model: opus-4.8 · high · docs-only (retro card + review-queue grooming)

Time: 2026-07-10 · lane: builder (ORDER 001 · games-web) · continuous-mode (Q-0265)

Retro answered against the fleet universal-core question set (A–F), pointed to by
`docs/retro/questions.md` → superbot `docs/planning/fleet-retro-questions-2026-07-09.md`.
Citations required; "not measured" over invention.

## Day-1 evidence (what the forge actually did)

**A · Walking skeleton proven.** Branch → trivial PR → substrate-gate → merge was
exercised end-to-end before any product work: PR #1 (first skeleton PR) and PR #3
(status/contract) both landed on green. The landing path is real, not theoretical.

**B · First product shipped — ORDER 001 (games-web phase-1).** Routed via
`control/inbox.md`, built, and closed the SAME day. Merged across PR #4 + PR #5
(scaffold + core/tests/README) and PR #6 (release/landing). `products/games-web/`
runs with one command (`run.sh`) from a committed mock game-state contract; full
build ladder ticked (scaffold · core · tests · README · release artifact).
Real-data integration correctly held OUT of scope (needs a superbot-lane read-only
API) and flagged, not built.

**C · Landing-path findings (the load-bearing lessons).**
  - Ruleset push-block: direct pushes to protected main are blocked — PRs are the
    only path. Confirmed on the first skeleton attempt.
  - REST merge-on-green works as the primary path on born-red states (R21).
  - Auto-merge pending-window recipe: create PR READY, arm native auto-merge WHILE
    the substrate-gate check is still pending → merges on green with no agent merge
    call. Verified on PR #6 and PR #7.
  - Classifier seat-variance: the permission classifier denied auto-merge *arming*
    on PR #8 (games-web visuals) though the identical call armed on #6/#7. Arming is
    not deterministically available; miss it and the PR waits green+READY for an
    owner click. Guard: always attempt arming immediately at PR creation; on denial,
    flag "awaiting owner click" and do NOT fall back to a raw merge call.

**D · Gotcha caught — status.md `updated:` regex.** PR #3 went red because the
`updated:` line format did not match bootstrap's expected pattern. Guard recipe: the
`updated:` field is regex-validated in `bootstrap.py`; when editing
`control/status.md`, keep the ISO-8601 `updated: <ts>` shape the gate expects and
verify with `python3 bootstrap.py check --strict` locally before pushing.

**E · Concurrency footgun — two sessions, one product.** PR #4 and PR #5 overlapped
on `products/games-web/` in the same window (two builder sessions touching one
subtree). No corruption this time, but the overlap is a latent conflict risk.
Recommend a one-writer-per-product convention: at most one live session holds the
write-lock on a given `products/<slug>/` subtree; a second session polishes a
different subtree or waits. Queued as a review-queue item (not written into
CONVENTIONS.md directly this slice).

**F · Inbox flow works end-to-end.** ORDER 001 was routed by the manager, picked up
by the builder lane, built to done-when, and closed same-day with
`orders: acked=001 done=001`. The manager-as-sole-inbox-writer + status-as-progress-
channel loop is proven. Current state: inbox DRY (only the completed ORDER 001),
flagged for the manager to route more.

## 💡 Session idea

**Card-marker preflight before push.** The gate's four required needles
(`**Status:**`, the idea glyph, `previous-session review`, the model line) plus the
in-progress and fill-slot red tokens are all knowable before push. A ~15-line stdlib
preflight that runs `bootstrap.py check --strict` against the session's own card and
refuses to push a red card would kill the "PR reds on a marker typo" round-trip (this
slice spent a read pass enumerating the exact needles). Complements the seed's
"product-state honesty checker" idea — same spirit, session-log lane.

## ⟲ Previous-session review

The seed session (`2026-07-10-seed.md`, fable-5) landed clean and its handoff was
accurate: walking-skeleton-first, then ORDER 001 = games-web (owner-named), which is
exactly how the day played out. One friction the seed under-flagged: the retro
question set lives in the superbot repo (`fleet-retro-questions-2026-07-09.md`), not
in product-forge — `docs/retro/questions.md` is only a pointer, so a retro author in
this repo cannot read the actual A–F questions locally. Minor; noted for the next
retro so it doesn't re-hunt.
