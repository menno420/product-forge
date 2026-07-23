# product-forge · status

updated: 2026-07-23T19:27:52Z
phase: ACTIVE — phone-controller Slice 4 IN FLIGHT (usable controller UI + downloadable APK)
lane: builder (phone-controller) · owner-live session
health: green

## WIP — this session (2026-07-23, owner-live directive)
Owner's live instruction (outranks the 07-11 archive-ready note per fleet precedence):
finish the controller app as a **downloadable APK** usable as a Bluetooth-HID input
device for other Android devices (emulator use case). In flight on branch
`claude/controller-app-android-apk-j7tv10` — born-red card
`.sessions/2026-07-23-phone-controller-slice4-apk.md`. Slices 1–3 context: PRs
#27/#28/#29/#31/#32 (post-archive hub-driven work; the PR ledger below predates them).

## Orders
orders: acked=001,002,003,004 done=001,002,003,004

ORDER 001 (games-web phase-1) — COMPLETE and merged. Runs via `products/games-web/run.sh`;
2 mock fixtures (Durzo lvl-27, Pip Gravelton lvl-1); dashboard-data-contract v1.0.1;
contract + 9-bad-mutation tests; inline SVG art; a11y; `.github/workflows/deploy-pages.yml`
prepped. Phase-1 done-when satisfied.
ORDER 002 (model-attribution ground truth) — DONE. `📊 Model:` confirmed a machine-enforced
needle; session cards carry a real family-level line (opus-4.8).
ORDER 003 (heartbeat timestamp ground truth) — DONE. `updated:` re-derived from `date -u`;
CONVENTIONS rule + repo-owned guard `scripts/check-heartbeat.py` + `heartbeat-guard.yml`.
ORDER 004 (fleet self-review) — DONE. Moved verbatim to durable
`docs/retro/2026-07-11-self-review.md`.

## Self-review 2026-07-11
Moved verbatim to [`docs/retro/2026-07-11-self-review.md`](../docs/retro/2026-07-11-self-review.md)
at close-out so the heartbeat stays lean. Pointer only.

## games-web live-preview state
Pages deploy is PREPPED but NOT LIVE — blocked on OA-003 (below). Site returns 404 at
https://menno420.github.io/product-forge/ (last verified ~2026-07-11T19:10Z); deploy runs
29126980391 + 29128667052 both fail at `actions/configure-pages` ("Get Pages site failed
... Not Found") until Pages is enabled.

## blocked-on (external waits — not this lane's to clear)
- **superbot lane** — games-web phase-2 real-data integration needs superbot's read-only
  API. Request artifact committed at `products/games-web/docs/phase2-data-api-proposal.md`
  (GET /v1/games-web/character-sheet/{id}, no repo-stored creds, fallback-to-mock). No
  response yet; phase-2 unblocks on a superbot-lane reply.
- **fleet-manager** — inbox DRY since ORDER 004 (2026-07-11 ~10:22Z). ~9h of 15-min chain
  ticks found no new work; the honesty guard held (no filler shipped). The forge needs new
  ORDERs to build.

## ⚑ needs-owner
⚑ OWNER-ACTION (OA-003, open)
WHAT: turn on GitHub Pages for this repo.
WHERE: repo Settings → Pages → Source → select "GitHub Actions".
HOW: click only (no values to paste).
WHY-IT-MATTERS: makes the games-web character-sheet preview publicly viewable.
UNBLOCKS: the prepped deploy-pages workflow publishes games-web to
  https://menno420.github.io/product-forge/ on its next run.
VERIFIED-NEEDED: deploy-pages runs 29126980391 + 29128667052 both fail at
  `actions/configure-pages` ("Get Pages site failed ... Not Found"); the site returns 404
  (last verified ~2026-07-11T19:10Z). Enabling Pages is a repo-settings toggle only the
  owner can perform.

## PRs
PRs #1–#22 ALL MERGED — zero open, zero closed-unmerged. This close-out lands as the next PR.

## Merge grant (owner authorization)
- 2026-07-10T21:07Z (session cse_01CiurDYKFjjTjn9E56pWBBF): standing merge/auto-merge grant,
  review-AFTER-completion model.
- 2026-07-10T22:08Z (coordinator session): "I explicitly grant you and all your agents full
  permissions to merge all PRs."
Full timeline: [`docs/retro/2026-07-11-owner-rulings-timeline.md`](../docs/retro/2026-07-11-owner-rulings-timeline.md).

## Continuous mode / wake state
ARCHIVED. At close-out the coordinator disarmed the "product-forge failsafe wake" cron
(trig_012EvztCrHHg7s4mBsKT3VKs) and the rolling 15-min continuation-chain one-shot. **NO
trigger remains armed.** A fresh session must re-arm the wake per the cutover recipe — see
[`docs/retro/archive-ready-2026-07-11.md`](../docs/retro/archive-ready-2026-07-11.md).

notes: project is archive-ready — see `docs/retro/archive-ready-2026-07-11.md` for the
one-paragraph true state and resume steps.
