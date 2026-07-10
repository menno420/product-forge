# review-queue — needs-second-eyes ledger (post-merge review)

> Merge on green, log here (and/or @codex the PR — Q-0258). Veto = revert. Format:
> `PR # · what to re-check · why`.

## Day-1 ledger (2026-07-10)

- PR #1 · walking-skeleton PR · merged green — skeleton landing path proven, no re-check needed.
- PR #3 · status/contract PR · merged green after a red — `updated:` regex gotcha (see day-1 retro §D); re-check the `updated:` format guard holds on the next status edit.
- PR #4 · games-web scaffold · merged green — overlapped PR #5 on the same subtree (see §E concurrency note).
- PR #5 · games-web core/tests/README · merged green — overlap-with-#4 window; verify no lost edits from the two-writer race.
- PR #6 · games-web release/landing · merged green — auto-merge pending-window recipe verified here.
- PR #7 · phase-2 proposal + platform-limits · merged green — docs-only.
- PR #8 · games-web visual polish · OPEN, awaiting owner click — classifier denied auto-merge arming (seat-variance); needs an owner merge click, do NOT raw-merge.

## Follow-ups surfaced by the day-1 retro

- PROPOSAL · one-writer-per-product convention · at most one live session holds the write-lock on a `products/<slug>/` subtree at a time (the PR #4/#5 overlap was a latent conflict). Manager to ratify into CONVENTIONS.md if accepted — not edited directly this slice.
- PROPOSAL · card-marker preflight before push · run `bootstrap.py check --strict` against the session's own card in run.sh/pre-push to kill marker-typo PR reds (see retro idea section).

## Owner actions

- OA-003 · enable GitHub Pages (Settings → Pages → Source: GitHub Actions) so the deploy workflow can publish products/games-web at https://menno420.github.io/product-forge/ · verify by visiting the URL after the next main push.
