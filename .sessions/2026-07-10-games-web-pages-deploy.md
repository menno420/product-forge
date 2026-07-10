# Session — games-web GitHub Pages deploy prep (builder lane)

> **Status:** `complete`

📊 Model: opus-4.8 · high · build

Time: 2026-07-10 · lane: builder (games-web · phase 1) · continuous-mode

Prepped a zero-cost GitHub Pages deploy for the games-web character sheet: a
Pages Actions workflow, an honest "Live preview" README section, and an owner
action to flip the one required settings switch. No renderer or contract change —
games-web is already a static, dependency-free folder, so it ships as-is to Pages.

## What this build did

**A · Pages deploy workflow — `.github/workflows/deploy-pages.yml`.** A standard
GitHub-Actions Pages pipeline (`configure-pages` → `upload-pages-artifact` with
`path: products/games-web` → `deploy-pages`). It fires on push to `main` under
`products/games-web/**` (and on its own file), plus `workflow_dispatch`. Uses the
Pages permission triplet (`pages: write`, `id-token: write`) and a `group: pages`
concurrency lock with `cancel-in-progress: false` so overlapping pushes serialize
rather than cancel a live publish.

**B · Subpath audit — safe at `/product-forge/`.** Project Pages serve under a
repo-name subpath, so `/`-rooted asset URLs would 404. Grepped `index.html` and
`assets/app.js` for `href="/`, `src="/`, `fetch("/` — all empty. Every asset and
mock-fetch path is already relative, so the site loads unchanged under the subpath.
No code change to `index.html`/`app.js`.

**C · Honest "Live preview" README section.** Added a `## Live preview` block naming
the auto-deploy trigger and the URL (https://menno420.github.io/product-forge/), and
flagged the real state: **pending an owner settings click** — Pages must be enabled
(Settings → Pages → Source: "GitHub Actions") before the first publish. Local
`./run.sh` stays the interim path.

**D · Owner action logged — OA-003.** Appended to root `review-queue.md` under a new
`## Owner actions` heading: enable Pages so the workflow can publish, verify by
visiting the URL after the next `main` push.

## 💡 Session idea

**A subpath-safety lint leg.** The Pages subpath (`/product-forge/`) only works
because every URL in games-web is relative — a single future `src="/assets/…"` would
silently 404 the deployed site while local `./run.sh` (served at root) stays green,
so the regression would be invisible until someone loads the live URL. A tiny CI grep
(`href="/`, `src="/`, `fetch("/` over `products/games-web/`) failing the build would
catch a root-rooted path at PR time instead of post-deploy. Flagged as a follow-up;
out of scope for this deploy-prep slice.

## ⟲ Previous-session review

The second-character card (`2026-07-10-games-web-second-character.md`, opus-4.8)
verified games-web is fully static and dependency-free — no build step, mocks fetched
as committed JSON — which is exactly what made this Pages slice a config-only change
with no renderer edits. It also modeled the "card-marker preflight before push"
discipline (mirror the exact needle structure: `**Status:**`, the 📊 model line, the
💡 idea glyph, the ⟲ previous-session review), followed here to avoid a strict-gate
round-trip.
