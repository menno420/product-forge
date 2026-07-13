# product-forge — fleet cleanup audit (2026-07-13)

> **Status:** `audit`

Complementary cleanup/audit pass requested as part of a fleet-wide sweep (owner ORDER
045, "find state of all repos, dispatch instructions for tonight — last day of the
EAP", issued 2026-07-13 ~22:00–22:30Z in a separate fleet-manager coordinator chat).
This is **not** a redispatch of work and does not touch this repo's `control/` files
beyond what is documented below (none were touched — see "What I did"). Verified
against live GitHub state (MCP `list_pull_requests`, `actions_list`, `list_branches`)
and the local clone, not against any doc's claims alone.

## What this repo is

`product-forge` is the "product build" seat in a ~20-repo fleet of autonomous Claude
Code coordinator sessions (`menno420/*`). Per its README: routed ideas arrive as
ORDERs in `control/inbox.md` (written only by the fleet manager) and become finished,
self-contained products under `products/<slug>/`. It runs on **substrate-kit v1.7.0**
(`substrate.config.json` → `"kit_version": "1.7.0"`), the same repo-resident
agent-workflow framework (working agreement, decision ledger, session-card discipline)
seen elsewhere in the fleet. It has shipped exactly one product so far:
**`products/games-web/`**, a dependency-free browser character-sheet renderer for one
of superbot's games (mock-data phase 1 only; real-data integration is explicitly out
of scope and blocked on a superbot-lane API that hasn't responded).

## Structure

```
product-forge/
├── README.md, CONVENTIONS.md, PLATFORM-LIMITS.md, CONSTITUTION.md   — binding contracts
├── bootstrap.py            — 12,055-line generated, stdlib-only substrate-kit CLI (DO NOT EDIT)
├── control/                — inbox.md (manager ORDERs) + status.md (coordinator heartbeat)
├── products/games-web/     — the one shipped product (index.html, assets/, data/, tests/)
├── docs/                   — architecture/ownership/runtime_contracts/current-state/etc.
│   └── retro/               — close-out records (self-review, owner-ruling timeline, archive-ready note)
├── .sessions/               — one dated log per session (10 files, 2026-07-10 → 2026-07-11)
├── .substrate/              — kit state (guard-fires log, episodic index, hooks/skills/ci)
├── review-queue.md          — post-merge "needs-second-eyes" ledger (repo root)
├── claims/, control/claims/ — per-file work-claim convention (both empty at rest)
└── .github/workflows/       — deploy-pages.yml, heartbeat-guard.yml, substrate-gate.yml
```

## Repo-specific context (given, not this session's to decide)

Per the dispatching brief, this project is in a documented close-out / archive-ready
state pending an owner consolidation decision (archive vs. migrate) tracked as
**`OQ-CONSOLIDATION-ARCHIVE-FORGE`** in the fleet-manager's `owner-queue.md` (a file
that lives in another repo, not here). That decision is explicitly out of scope for
this audit and was not acted on. No reference to it exists inside this repo (grepped
`OQ-CONSOLIDATION` and `consolidat` across all `*.md` — only unrelated hits in
`README.md`/`docs/owner-profile.md`/`docs/current-state.md` describing the fleet
manager's general "consolidates what shipped" role, not this specific queue item).

## Activity check — DARK, confirmed

- Last commit on `main`: `4fdfa8a` ("Merge pull request #23 …"), authored
  `2026-07-11 21:49:47 +0200` = `2026-07-11T19:49:47Z`.
- Current time at audit: `2026-07-13T23:33Z` — **~52 hours** since the last commit.
- `control/status.md` `updated:` stamp: `2026-07-11T19:39:50Z`, `phase: close-out /
  archived-ready`, and explicitly: *"At close-out the coordinator disarmed the
  'product-forge failsafe wake' cron … and the rolling 15-min continuation-chain
  one-shot. **NO trigger remains armed.**"*
- No open PRs, no branches other than `main` (`list_branches` → exactly one entry,
  `main`, protected). No stray/abandoned feature branches to clean up.

**Verdict: DARK.** The repo is genuinely dormant — no live session, no armed wake
trigger, no in-flight branches or PRs — matching its own self-declared archive-ready
state. Safe to triage per the dispatching brief's rules.

## Open PRs

**Zero open PRs.** `mcp__github__list_pull_requests(state=open)` returned `[]`.
`mcp__github__list_pull_requests(state=closed)` returned 23 PRs (#1–#23), all merged —
this matches the repo's own `control/status.md` claim ("PRs #1–#22 ALL MERGED — zero
open, zero closed-unmerged. This close-out lands as the next PR" — PR #23 is that
close-out PR, now itself merged). No PR was merged, closed, or otherwise touched by
this audit — there was nothing to act on.

## CI setup and health

Three workflows, all narrowly scoped:

| Workflow | Trigger | Purpose |
|---|---|---|
| `substrate-gate.yml` | every PR + push to `main` | kit-owned hygiene gate: session-card discipline, `bootstrap.py check --strict`; has a `control/**`-only fast lane |
| `heartbeat-guard.yml` | PR/push touching `control/status.md` | rejects a future-dated `updated:` stamp (`scripts/check-heartbeat.py`) |
| `deploy-pages.yml` | push touching `products/games-web/**` | builds/deploys the games-web static site to GitHub Pages |

Pulled the last 30 of 60 total workflow runs (`actions_list`, newest-first) as a
representative sample:

- **`substrate-gate`** and **`heartbeat-guard`**: every run in the sample is
  `completed` / `conclusion: success`, through the final push (`4fdfa8a`, run #50 and
  #8 respectively). Locally re-ran the repo's own gate — `python3 bootstrap.py check
  --strict` → `check: all checks passed.` — confirming the tree is clean at HEAD, not
  just that the historical CI runs were green.
- **`deploy-pages`**: **both** of its 2 runs (run #1, head `6f5cfad`, 2026-07-10T22:11Z;
  run #2, head `77f5231`, 2026-07-10T22:47Z) are `conclusion: failure`, at the
  `actions/configure-pages` step ("Get Pages site failed … Not Found"). This is
  expected and already tracked as **⚑ OA-003** (owner action: enable GitHub Pages,
  Settings → Pages → Source → "GitHub Actions") in `control/status.md` — an
  owner-only settings toggle, not a code defect. **Independently verified live**:
  `curl -o /dev/null -w '%{http_code}' https://menno420.github.io/product-forge/` →
  `404` at audit time (2026-07-13T23:33Z), consistent with Pages still being off.
- The product's own test suite passes cleanly at HEAD: `python3
  products/games-web/tests/test_mock_contract.py` → both fixtures validate against
  the `v1.0.1` schema and all 9 known-bad mutations are correctly rejected.

No red CI, no flaky runs, no failing required checks outstanding — the only "red" in
the run history is the two expected, already-documented, owner-gated Pages failures.

## Doc quality

Strong in the areas this project actually exercised (close-out narrative,
`PLATFORM-LIMITS.md`'s verified landing recipes, the ORDER-by-ORDER session logs, the
retro set) — dated, cites commit SHAs and PR numbers, and corrects its own prior
mistakes in place (e.g. `.sessions/2026-07-11-hygiene-review-queue-restore.md`
explicitly fixes a wrong-path claim from the ORDER 004 self-review rather than
silently dropping it).

Weaker spot, worth flagging: several of the substrate-kit-generated **binding** docs
were never filled in past their template skeleton, despite the repo being fully
close-out'd:
- `docs/current-state.md` — every section (Stability baseline, In flight, Recently
  shipped) is still the literal generator placeholder text
  (`(Describe the accepted-stable baseline once established…)`,
  `(Merged work only, newest first.)`) — 23 merged PRs and a shipped product exist,
  none are reflected here.
- `docs/architecture.md` — the layer table has one placeholder row
  (`(one row per layer, expanded from the summary above)`); "Invariants" is empty.
- `docs/ownership.md` — the "Ownership table" has a header row and no data rows.
- `docs/runtime_contracts.md` — "Startup", "Steady state" are empty placeholder
  parens; no "Shutdown" content either.
- `docs/repo-navigation-map.md` — the navigation table is one placeholder row.
- `project.index.json` (AgentContextPack manifest) — still has its single seed
  `"example-area"` entry with every field empty (`folio`, `binding_docs`,
  `source_roots`, etc. all `""`/`[]`), never populated for the real `games-web` area.

None of this blocks anything mechanically (the repo's own `bootstrap.py check
--strict` doesn't require these sections to be non-empty), and the actually-consulted
docs (README, CONVENTIONS, PLATFORM-LIMITS, the retro set, `control/status.md`) are
accurate and current as of the close-out. But a future session — or a migration —
reading `docs/current-state.md` or `docs/architecture.md` expecting the "living
ledger" / "binding" content their own status badges promise will find template text
instead. This is a proportionality mismatch (a one-product, single-subtree repo
adopted the full multi-service documentation skeleton) rather than drift from a
previously-filled state — worth a fill-in pass *if and only if* this repo is chosen
to migrate rather than archive; not worth doing pre-emptively if it's being archived.

## What I found and did with open PRs

Nothing to do: zero open PRs existed at audit time (see "Open PRs" above), and none
were created, merged, or closed by this session. This audit's own report PR (opened
below) is the only PR this session creates.

## Concrete inconsistencies / errors noticed

1. **`docs/current-state.md`, `docs/architecture.md`, `docs/ownership.md`,
   `docs/runtime_contracts.md`, `docs/repo-navigation-map.md`, `project.index.json`**
   — unfilled substrate-kit template placeholders despite the repo being fully
   close-out'd (detailed above under "Doc quality"). Not fixed in this pass — it's a
   content-authoring task, not a mechanical cleanup, and the repo's archive-vs-migrate
   fate (OQ-CONSOLIDATION-ARCHIVE-FORGE) should be decided first so the effort isn't
   wasted on a repo about to be archived.
2. **`.substrate/guard-fires.jsonl`** records three `reachable`/orphan-doc findings
   for `retro/questions.md`, `retro/2026-07-11-owner-rulings-timeline.md`, and
   `retro/archive-ready-2026-07-11.md` at timestamps `2026-07-10T18:26:25Z` and
   `2026-07-11T19:41:22Z` (i.e., mid-session, before the retro README was written to
   link them). This is stale/historical — `docs/retro/README.md` now links all three
   files, and the current `bootstrap.py check --strict` run is clean — but it's worth
   noting the guard-fires log is append-only and doesn't self-correct; a future reader
   grepping it for "current problems" would see these as still-open unless they also
   check the doc that resolved them.
3. Everything else checked (open-PR count, CI conclusions, branch list, wake-trigger
   state, Pages 404, product test suite) matches what `control/status.md` and the
   retro docs already claim — no other discrepancy between documented and live state
   was found.

## Suggestions

1. **Centralize the Pages-enablement owner-action pattern.** This is the second time
   in this fleet (evidenced here; likely elsewhere) that a repo's first GitHub Pages
   deploy fails at `actions/configure-pages` with an identical "Get Pages site failed
   … Not Found" until an owner clicks Settings → Pages → Source → "GitHub Actions" once.
   A one-time fleet-wide runbook entry (or a `gh api` / `workflow_dispatch` step that
   detects and clearly surfaces this exact failure signature) would save each new
   product-shipping repo from independently rediscovering and documenting the same
   wall (this repo already did the work in `PLATFORM-LIMITS.md`/`docs/CAPABILITIES.md`
   — worth promoting to the fleet-manager's shared capabilities doc if not already
   there).
2. **Decide the OQ-CONSOLIDATION-ARCHIVE-FORGE question before investing further doc
   work here.** The unfilled binding-doc skeletons (finding #1 above) are exactly the
   kind of debt that's cheap to fix now (a few hours of authoring against a clean,
   fully-tested, 23-PR history) or pointless to fix if the repo archives — resolving
   the archive-vs-migrate decision first avoids wasted authoring effort either way.
3. **The `.substrate/guard-fires.jsonl` append-only log has no "resolved" marker.**
   Consider a lightweight convention (fleet-wide, since this is kit-generated
   tooling, not repo-specific) for marking a guard-fires entry as resolved once the
   condition it flagged is fixed, so the log stays useful as a live signal instead of
   requiring cross-referencing against docs to tell stale fires from current ones.
4. **This repo is a clean template for "how a small forge product closes out."**
   Given zero open PRs, zero stray branches, a disarmed wake trigger, green CI history,
   and a passing local test suite, `product-forge`'s close-out sequence (the
   `docs/retro/archive-ready-2026-07-11.md` note + the "what a fresh session needs to
   resume" checklist it contains) is a reasonable pattern to point other DARK repos in
   this fleet at when they reach their own close-out, rather than each inventing its
   own archive-ready format.

## What I did (this audit session)

- Read `control/status.md`, `control/inbox.md`, `README.md`, `CONVENTIONS.md`,
  `PLATFORM-LIMITS.md`, `CONSTITUTION.md`, `review-queue.md`, all of `.sessions/*.md`,
  and the `docs/` tree (architecture, ownership, runtime_contracts, current-state,
  repo-navigation-map, helper-policy, decisions, question-router, ideas/README,
  retro/*, CAPABILITIES.md).
- Verified live GitHub state via MCP: `list_pull_requests` (open + closed),
  `list_branches`, `actions_list` (workflow runs, 30-run sample covering both
  `substrate-gate`/`heartbeat-guard` (all green) and `deploy-pages` (2 runs, both the
  expected pre-Pages-enablement failure)).
- Ran `python3 bootstrap.py check --strict` locally (repo's own gate) — clean.
- Ran `python3 products/games-web/tests/test_mock_contract.py` — passed, both
  fixtures + all 9 bad-mutation rejections.
- Verified the live Pages URL (`curl` → `404`, matching the documented blocked state).
- Grepped the whole repo for `OQ-CONSOLIDATION` / `consolidat` — no local reference to
  the pending owner-queue item beyond generic role descriptions.
- Touched **no** `control/status.md`, **no** `control/inbox.md`, and made **no**
  code/product changes. The only artifact this session produces is this report file,
  committed on a new branch, opened as a PR, and left open per instructions (not
  self-merged).
