# games-web phase 2 — read-only data API contract (PROPOSAL)

> **PROPOSAL — nothing here is built until the superbot lane accepts.** This
> document is a dependency request from the product-forge `games-web` lane to the
> superbot / manager lane. It defines a *proposed* read-only API so games-web can
> render **live** game-state instead of the committed mock. No endpoint, client, or
> credential described here exists yet. product-forge builds nothing against this
> contract until the superbot lane replies with an accepted (or amended) version.

## Why this exists

games-web phase 1 renders a committed mock (`data/mock/mining-character.json`)
validated against the versioned contract `games-web.character-sheet` (semver
`schema_version`, currently `1.0.0`; see `data/schema/game-state.schema.json`).
Phase 2 = swap the mock file for a live read-only source **without changing the
render contract**. The renderer already refuses a `contract`/major it does not
understand, so a live source that emits the same contract is a drop-in.

This proposal mirrors the versioned dashboard-data-contract pattern superbot
already ships (superbot PR #1920 style): a stable `contract` id, a semver version,
additive-only minor changes, and an explicit compatibility rule.

## Proposed endpoint shape

Single read-only resource. One character sheet per request, on the existing
contract envelope.

```
GET /v1/games-web/character-sheet/{character_id}
```

- **Response body:** exactly the `games-web.character-sheet` envelope games-web
  already consumes — `schema_version`, `contract`, `generated_at`, `character`,
  `stats[]`, `gear{}` (8 fixed slots), `skills[]`, `structures[]`. Validated
  against the same `data/schema/game-state.schema.json`. The live payload MUST
  pass that schema unchanged.
- **`contract`** is the const `"games-web.character-sheet"`. A payload with any
  other `contract` value is rejected client-side (existing behavior).
- **Content-Type:** `application/json`.
- **No write verbs.** GET only. No POST/PUT/PATCH/DELETE in this contract.

Optional companion (only if trivial for the superbot lane; not required for phase 2):
```
GET /v1/games-web/character-sheet            # list of {character_id, updated_at}
```
so games-web can offer a picker instead of a hard-coded id. Out of scope until the
single-sheet endpoint lands.

## Versioning + compatibility rules

- **Path carries the major** (`/v1/...`). A breaking change = a new major path
  (`/v2/...`); `/v1/` keeps serving the `1.x` contract.
- **Body carries the semver** in `schema_version` (`MAJOR.MINOR.PATCH`).
- **Additive-only within a major.** New optional fields bump MINOR; games-web
  ignores unknown optional fields it does not render (forward-compatible read).
- **Required-field or enum-narrowing changes are breaking** → new major.
- **games-web pins the major it understands.** If the served `schema_version`
  major exceeds the pinned major, games-web refuses the payload and falls back
  (see below) rather than mis-rendering.
- **`generated_at`** stays an ISO-8601 UTC timestamp; games-web may surface it as
  a "last updated" line and MAY treat a stale value as a soft fallback trigger.

## Auth stance

- **Read-only, unprivileged.** This contract exposes non-sensitive presentational
  game-state only. No user PII, no write scope, no account mutation.
- **No credentials are stored in this repo, ever.** product-forge commits no
  tokens, keys, or secrets for this API. If the superbot lane requires auth, the
  accepted design MUST allow one of: (a) a public read-only endpoint, or (b) a
  short-lived token injected at runtime via environment/secret store the owner
  controls — never a committed value. product-forge will not build a client that
  reads a secret from the repo tree.
- **CORS:** the endpoint must permit browser reads from the games-web origin
  (served over HTTP per README), since the renderer fetches client-side.

## Fallback-to-mock behavior

games-web MUST degrade to the committed mock and stay usable if the live source
is unavailable or non-conforming. Fallback triggers, in order:

1. **Network/HTTP error** (endpoint unreachable, 4xx/5xx) → render the committed
   mock, show a non-blocking "live data unavailable — showing sample" banner.
2. **Contract mismatch** (`contract` != `games-web.character-sheet`) → fallback +
   banner. Never render a foreign contract.
3. **Unsupported major** (served major > pinned major) → fallback + banner.
4. **Schema-invalid body** (fails `game-state.schema.json`) → fallback + banner.

The mock therefore stays a committed, tested artifact permanently — it is the
guaranteed-usable floor, not throwaway scaffolding. "A product nobody can run is
not shipped" holds even when the live source is down.

## What the superbot lane must decide (the ask)

The superbot lane owns these; product-forge only proposes:

1. **Accept / amend the endpoint path + envelope** (does superbot already emit
   something close? Map it, don't reinvent).
2. **Auth model** — public read vs. runtime-injected short-lived token.
3. **Who hosts** the endpoint and its base URL (games-web will config it, not
   hard-code it).
4. **Character id space** — what `{character_id}` is and how games-web obtains a
   valid one.

Until this doc is accepted (or amended) by the superbot lane, phase-2 real-data
integration stays BLOCKED and games-web ships mock-data-first. This proposal is the
artifact that unblocks phase-2 *planning* without building anything.

## Money / spend note

Nothing here executes a spend. If accepting this requires paid hosting, a domain,
or a managed API tier, that is an owner-action for the superbot/manager lane to
cost and approve — product-forge only names the dependency, never buys it.
