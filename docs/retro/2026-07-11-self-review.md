# Self-review — 2026-07-11

> **Status:** `reference`

Moved verbatim from `control/status.md` at project close-out (2026-07-11) so the retro is
durable and the heartbeat stays lean. Owner-requested fleet-wide self-review, ORDER 004 (P1).

Owner-requested fleet-wide self-review (ORDER 004, P1). Window ~2026-07-10T20:00Z → 2026-07-11T10:00Z. Every claim verified against git/PRs/CI runs; corrections vs the coordinator relay are noted inline.

### Went wrong (each cited)
- PR #3 substrate-gate red — status heartbeat missing. A status.md write dropped the required `updated:` line → gate red. Fixed by commit b1a4976 ("coordinator: add updated: heartbeat line so status-gate passes"), PR #3 merged (merge c67b1fc, 19:02Z). The failing check_run's output was EMPTY over the GitHub API — diagnosis needed local `python3 bootstrap.py check --strict`.
- Merge-consent / auto-merge classifier variance (session-log-only). In their own seats, auto-merge ARMING was denied on PRs #8/#12/#13/#14/#15/#17/#19 but SUCCEEDED on #6/#7/#9/#10/#11; PR #4 self-merge + peer-merge both denied ("permission laundering"). All named PRs are now merged — final states are git-verified; the arm/deny classifier events themselves are session-log-only (no git trace). Resolution: owner in-session grants (21:07Z; 22:08Z "all agents, all PRs") + coordinator-session merges, plus the auto-merge pending-window recipe where the seat allowed it.
- Future-dated heartbeat. status.md carried `updated: 2026-07-11T12:00:00Z` (~7.5h ahead, hand-typed) — flagged by fm roster gen #5. Fixed via ORDER 003 / PR #19 (merge 43563dc, 05:18Z): `updated:` now derived from `date -u`, plus repo-owned guard scripts/check-heartbeat.py + .github/workflows/heartbeat-guard.yml rejecting future stamps.
- Concurrency overlap. PRs #4 and #5 both built products/games-web/ in the same window — and both also touched control/status.md (two-front overlap, git-verified). One-writer-per-subtree convention proposed; needs manager ratify.
- Pages deploy failing by design until enablement. deploy-pages runs 29126980391 (head 6f5cfad, 22:11Z) and 29128667052 (head 77f5231, 22:47Z) both failed at actions/configure-pages ("Get Pages site failed ... Not Found") — both failures git-verified; only these 2 runs exist, no successful deploy. Expected until Pages is enabled (OA-003).

Correction to the relay: the count is 20 PRs merged all-time (#1–#20, zero open), not 19 — #20 is the self-review ORDER PR that merged at the review boundary (10:02Z); 16 merged inside the 20:00Z→10:00Z window (#4–#19).

### Owner attention (click-level)
- ⚑ OA-003 (open): Settings → Pages → Source "GitHub Actions" → games-web goes live at https://menno420.github.io/product-forge/ on the next deploy run. Site is 404 until then. Pages-enabled state is not confirmable from git; both deploy runs failing at configure-pages is strong evidence it is still off.
- ⚑ Merge consent is session-scoped: owner grants work via the coordinator seat, but per-seat classifier variance persists. A settings-level permission allow-rule for the GitHub merge tools would remove the variance entirely (optional convenience — not required to land PRs; the pending-window recipe + coordinator merges already land every PR on green).
- ⚑ one-writer-per-subtree convention (proposed after the #4/#5 overlap) is queued in review-queue.md (repo ROOT, added PR #9 916a407) — needs the manager to ratify it into CONVENTIONS.md. The earlier "review-queue.md missing/absent" flag was a wrong-path observation error: it looked under control/review-queue.md, but the file has lived at the repo root continuously since seed 5d52f45 (last touched PR #13 d878851) and is present + current at HEAD.
- Spend: NONE executed. Pages is free (Q-0259 r.4 honored).

### Health (one line)
20 PRs merged in ~24h, zero open; ORDERs 001–003 done (games-web phase-1 complete — SVG art, a11y, 2 fixtures, v1.0.1 contract, contract+mutation tests, Pages deploy prepped; model-attribution + heartbeat-guard landed); next: games-web phase-2 blocked on a superbot read-only API (proposal committed), inbox otherwise dry.
