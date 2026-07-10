# products/ — one product, one subtree

Every product lives in `products/<slug>/`, self-contained: own README (what it is · how
to run it · honest state: working / alpha / released), own tests, a runnable or
releasable artifact, own pinned deps inside the subtree (the repo root stays
stdlib-only). **No cross-product imports.** A new subtree is created only by a routed
`control/inbox.md` ORDER — never invented here. Graduation: a proven product moves to
its own repo (owner click) and becomes a lane.

## Products

### games-web — Shakes & Fidget-style comic browser-RPG character sheet
- **What:** a dependency-free web character sheet rendering superbot's MINING game from a
  committed mock game-state contract — paper-doll gear, stats, skills, structures. Two
  mock characters (Durzo, Pip Gravelton) behind a topbar switcher.
- **Run (one command):** `products/games-web/run.sh` (serves it locally, stdlib-only) —
  or open `products/games-web/index.html` directly in a browser.
- **Live preview:** https://menno420.github.io/product-forge/ — **PENDING** GitHub Pages
  enablement (OA-003). The deploy workflow is ready and publishes on push to main once
  Pages is turned on; not live yet.
- **State:** alpha — phase-1 (mock-data) COMPLETE and merged; real-data integration is
  blocked on a superbot-lane read-only API
  (`products/games-web/docs/phase2-data-api-proposal.md`).
