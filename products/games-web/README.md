# games-web — browser character sheet (phase 1)

Turn superbot's games into a web-based visual experience: a Shakes & Fidget-style
comic browser-RPG presentation. **Phase 1** renders **MINING** character sheets —
gear paper-doll, stats, skills, structures — from **committed mock game-states**, with
a topbar dropdown to switch between characters.

## State

**alpha · runnable.** Renders two committed mock characters in the browser with
dependency-free inline SVG art (gear, skills, structures, a paper-doll miner figure),
rarity-framed chips, xp bars, and hover tooltips showing item stats. A **character
switcher** in the topbar flips between them without a reload:

- `data/mock/mining-character.json` — **Durzo Coalfist**, a level-27 Foreman with all
  eight gear slots filled (default view).
- `data/mock/recruit-character.json` — **Pip Gravelton**, a fresh level-1 recruit with
  mostly empty gear slots and minimal stats, exercising the renderer's empty-slot and
  missing-optional branches.

Responsive down to narrow widths. No real game data yet.

## Live preview

Once GitHub Pages is enabled for this repo, the site auto-deploys on every push to
`main` that touches `products/games-web/**` (via `.github/workflows/deploy-pages.yml`).

- URL: https://menno420.github.io/product-forge/

**State: pending an owner settings click.** Pages must be enabled first
(Settings → Pages → Source: "GitHub Actions") before the first deploy publishes.
Until then, run locally with `./run.sh` (see below).

## Run

```bash
./run.sh            # serves this folder on http://localhost:8000
# or: python3 -m http.server 8000
```

Open http://localhost:8000/ . Must be served over HTTP — opening `index.html` as a
`file://` URL will not work (the page fetches the mock JSON).

## Test

```bash
python3 tests/test_mock_contract.py
```

Validates both committed mocks (`mining-character.json` and `recruit-character.json`)
against the versioned contract (`data/schema/game-state.schema.json`). Uses `jsonschema`
for full-schema validation when installed; always runs dependency-free structural checks.
Beyond the happy path it
also asserts the contract's *rejection* power: nine known-bad mutations of the mock
(wrong contract id, non-semver version, missing gear slot, invalid rarity, `xp > xp_max`,
bad structure status, and more) must each be refused. Exit 0 = contract holds (good mock
accepted, bad mocks rejected).

## Data contract

`data/schema/game-state.schema.json` (`contract: games-web.character-sheet`, semver
`schema_version`) mirrors superbot's versioned dashboard-data-contract pattern
(superbot PR #1920). The renderer refuses a contract/major it does not understand.

Layout:
- `character` — name, class, level, title, portrait (placeholder)
- `stats[]` — ordered stat lines
- `gear{}` — 8 fixed paper-doll slots (head, chest, hands, legs, feet, main_hand, off_hand, trinket); a slot may be `null`
- `skills[]` — level + xp/xp_max progress bars
- `structures[]` — tier + status (idle | working | upgrading | locked)

## Out of scope (phase 1)

Real-data integration is **explicitly out of scope**: it needs a superbot-lane
**read-only API** to serve live game-state on this contract. Flagged for the owner —
not built here. Phase 1 stays mock-data-first and cheap to redirect (concept heads to
sim-lab for an evidence pass later).

## Files

```
products/games-web/
├── index.html              # entry page
├── assets/{app.js,styles.css}
├── data/schema/game-state.schema.json   # versioned contract
├── data/mock/mining-character.json      # committed mock — Durzo (lvl 27, all slots filled)
├── data/mock/recruit-character.json     # committed mock — Pip (lvl 1, empty slots / min values)
├── tests/test_mock_contract.py          # contract validation (both fixtures)
└── run.sh
```
