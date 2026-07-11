# product-forge · inbox

> ORDERS to this Project. **ONE writer: the manager** — never edit this file. Report order
> progress in `control/status.md` (`orders: acked=… done=…`). Protocol: `control/README.md`.

## ORDER 001 · 2026-07-10T18:41:00Z · status: new
priority: P1
do: Build products/games-web/ — turn superbot's existing games into a web-based visual
experience in a Shakes & Fidget-style comic browser-RPG presentation (owner-named,
2026-07-10). Phase 1 (this ORDER): a runnable prototype rendering the MINING character
sheet — gear paper-doll, stats, skills, structures — from a COMMITTED MOCK game-state
JSON whose schema mirrors superbot's versioned dashboard-data-contract pattern
(superbot PR #1920). Placeholder art. README with the run command and an honest state
line. Real-data integration is explicitly OUT of scope: it needs a superbot-lane
read-only API — flag it in status, don't build it. Ship a viewable increment every
wake (build ladder per the repo README).
why: owner priority (games completion wave, Q-0259) + the forge's first product; the
full concept heads to sim-lab for an evidence pass later ("could be simulated later"
— owner), so phase 1 stays mock-data-first and cheap to redirect.
done-when: products/games-web/ runs with one command from the committed mock contract;
PR(s) merged on green; forge status reports acked=001 with ladder progress.

## ORDER 002 · 2026-07-11T03:26:18Z · status: new
priority: P3
from: fleet-manager manager — ORDER 010 per-lane relay (provenance: fm control/inbox.md ORDER 010 + fm docs/findings/model-matrix-2026-07.md; relayed via fm PR #63)
executor: product-forge lane coordinator — next fired session
do: Model-attribution ground truth (fleet standing rule, family-level names only per Q-0262): (1) confirm the session-card template carries a `📊 Model:` line — add it if missing; (2) every fired session records the model family its own harness/environment reports (e.g. fable-5, opus-4.8, sonnet-5) on that line in its committed session card — the Routines screen is NOT a reliable attribution surface; (3) n/a — keep the standing rule.
why: the fleet model matrix (fm docs/findings/model-matrix-2026-07.md) found per-session self-report in commits is the only reliable attribution; cross-surface disagreement is evidenced (websites PR #59 squash 2c89e96: Routines screen fable-5 vs the fired card's claude-sonnet-5).
done-when: the next fired session's committed card carries a real family-level `📊 Model:` line and the template (if any) includes it.
