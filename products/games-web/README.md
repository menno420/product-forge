# games-web — browser character sheet (phase 1)

Turn superbot's games into a web-based visual experience: a Shakes & Fidget-style
comic browser-RPG presentation. **Phase 1** renders the **MINING** character sheet —
gear paper-doll, stats, skills, structures — from a **committed mock game-state**.

## State

**alpha · runnable.** Renders the committed mock (`data/mock/mining-character.json`)
in the browser. Placeholder art (emoji + rarity-framed chips). No real game data yet.

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

Validates the committed mock against the versioned contract
(`data/schema/game-state.schema.json`). Uses `jsonschema` for full-schema validation
when installed; always runs dependency-free structural checks. Exit 0 = contract holds.

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
├── data/mock/mining-character.json      # committed mock game-state
├── tests/test_mock_contract.py          # contract validation
└── run.sh
```
