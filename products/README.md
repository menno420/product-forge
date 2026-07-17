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

### phone-controller — turn a phone into a Bluetooth-HID controller
- **What:** make a phone act as a customizable Bluetooth-HID controller (keyboard / mouse
  / gamepad / media remote) for phones, tablets, TVs and PCs. From the Ideas-Lab plan
  `menno420/idea-engine : ideas/product-forge/bt-controller-plan-2026-07-17.md`. This
  slice ships the idea's **Slice 1** — a portable capability-probe verdict engine +
  receiver-compatibility matrix (the platform-agnostic decision core the Android UI will
  call).
- **Run (one command):** `products/phone-controller/run.sh` (stdlib-only) — verdicts for
  representative device scenarios + the receiver matrix; pass `--platform/--api/…` for one
  explicit probe.
- **State:** alpha — Slice 1 (capability core) runnable + tested (26 tests). The actual
  `BluetoothHidDevice` transport and the Android/Kotlin UI are later slices (need an
  Android build lane; can't go green in this CI). iOS-as-controller is deferred (OS wall).
