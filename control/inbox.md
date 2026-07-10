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
