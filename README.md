# product-forge — the fleet's product build seat

> **Status:** `binding` — this README + `CONVENTIONS.md` are the repo's contract.
> Founding design: superbot `docs/planning/round3-founding-package-product-forge-2026-07-10.md`
> (role framing owner-confirmed 2026-07-10) + owner rulings **Q-0259 r.4** (money protocol)
> and **Q-0264** (the pipeline this seat consumes from). Seeded 2026-07-10 by the dispatch
> copilot (superbot round-3 session, kit v1.7.0).

This repo **builds products**: routed ideas — ORDERs in `control/inbox.md`, written by the
fleet manager — become finished, shippable products, each in its own self-contained
subtree. The forge is the **default executor for build-worthy work that has no owning
lane** (the manager applies the *"no owning lane exists"* test before routing here). It
does not choose product intent, do fleet oversight, ideation, or another lane's domain
work. Its only writable repo is this one (Q-0260).

**Pipeline position (Q-0264):** idea-engine generates/probes → sim-lab reproduces
evidence + finalizes → the **manager** final-reviews and routes ORDERs → **the forge
builds** → the manager consolidates what shipped.

## Products — `products/<slug>/`

One product per subtree, self-contained: own README (what it is, how to run it, its
**honest state** — working / alpha / released), own tests, a runnable or releasable
artifact, own pinned deps. **No cross-product imports, ever.** A product nobody can run
is not shipped.

**Incubator mechanic (owner-confirmed):** a product that outgrows its subtree — real
users, its own release cadence, its own idea stream — **graduates to a dedicated repo
and becomes a lane** (owner click, spent only on proven winners; the substrate-kit's own
path out of superbot's tree).

## The build ladder (every ORDER advances it in order)

scaffold → working core → tests → README/usage → release artifact. Ship a usable
increment every session — *a build is better than no build*; polish later.

## Money protocol (Q-0259 r.4 — hard rule)

A spend or external-account step is **never executed**. It becomes a conservative
six-field OWNER-ACTION plan in the status ⚑ block: exactly what the owner must
do/enable/buy, expected earnings and payback stated pessimistically — never overstated.

## Coordination

`control/` is the bus (see `control/README.md`): `inbox.md` manager-written ORDERs
(never edited here) · `status.md` coordinator-only heartbeat, overwritten as the
deliberate LAST step of every session. Empty inbox → polish the newest product's
roughest edge and flag `inbox empty` in status — never invent product intent.

Landing conventions: `CONVENTIONS.md`. Walls: [`PLATFORM-LIMITS.md`](PLATFORM-LIMITS.md).
Retro self-review set: [`docs/retro/questions.md`](docs/retro/questions.md). Verify before
push: `python3 bootstrap.py check --strict` + each touched product's own test command.
