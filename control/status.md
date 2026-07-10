# product-forge · status

updated: 2026-07-10T21:21:52Z
lane: builder (ORDER 001 · games-web) · continuous-mode
health: green

## Orders
orders: acked=001 done=001

ORDER 001 (games-web phase-1) — COMPLETE and merged. Phase-1 done-when SATISFIED:
runs with one command from the committed mock contract (`products/games-web/run.sh`);
all PRs merged on green. The build ladder is fully ticked
(scaffold · core · tests · README · release artifact) and has since been extended:
games-web now ships TWO committed mock character fixtures — a lvl-27 foreman (Durzo,
all slots filled) and a lvl-1 recruit (Pip Gravelton, edge/minimum values, empty gear
slots) — behind a dependency-free topbar `<select>` switcher. The contract test
validates BOTH fixtures on the happy path and keeps its 9 known-bad mutation cases.
Both gates green: contract test EXIT=0, `bootstrap.py check --strict` EXIT=0.

## PRs
All PRs #1–#10 merged. ZERO open PRs. Latest: PR #10 (games-web second character
fixture + topbar switcher) merged on the substrate-gate — merge commit 715e07f.

## Merge grant (owner standing authorization)
On 2026-07-10T21:07Z owner menno420 wrote in session
cse_01CiurDYKFjjTjn9E56pWBBF: "I give you explicit permission to merge/auto-merge any
current or future PR without my review, the purpose is that I review afterwards when the
whole product is finsished".

Effect: the review model is now review-AFTER — the owner reviews when the whole product
is finished, not per-PR. The substrate session-gate remains the in-flight quality gate
(every PR still lands only on green). Auto-merge (MERGE) is armed inside the gate's
pending window so GitHub merges on green.

## Continuous mode
Active. Chain alive (~15-min send_later continuation ticks) + failsafe cron
trig_012EvztCrHHg7s4mBsKT3VKs "product-forge failsafe wake" (`0 */2 * * *`), enabled.

## Manager flag
⚑ Inbox DRY — no new ORDER beyond 001; the forge needs new ORDERs. games-web phase-2
(real-data integration) stays BLOCKED on a superbot-lane read-only API. The dependency
request is a concrete artifact: products/games-web/docs/phase2-data-api-proposal.md —
a conservative read-only contract (GET /v1/games-web/character-sheet/{id} on the
existing `games-web.character-sheet` envelope, read-only, no repo-stored creds,
fallback-to-mock). Phase-2 unblocks on a superbot-lane response to that proposal.

## Landing
Landing recipe: `python3 bootstrap.py check --strict` exit 0 → push branch → PR READY
→ arm native auto-merge (MERGE) inside the substrate-gate pending window → GitHub merges
on green with no agent merge call. Backed by the owner's standing merge grant above; the
owner reviews after the product is finished. If the arming window is missed, a green+READY
PR awaits an owner click.
