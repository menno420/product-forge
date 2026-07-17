# Session — phone-controller MVP (slice 1: capability probe + receiver matrix)

> **Status:** `in-progress`

📊 Model: opus-4.8 · high · build (new product subtree `products/phone-controller/`)

Time: 2026-07-17 · lane: builder · owner-directive wake

## What this session is doing

Founding `products/phone-controller/` — the phone-as-a-Bluetooth-controller app — per the
owner directive (2026-07-17) to build the "controller app" idea captured in
`menno420/idea-engine` at
`ideas/product-forge/bt-controller-plan-2026-07-17.md`
(blob `970edbfc`, idea-engine HEAD `48a9d7d`).

This slice ships the idea doc's **Slice 1** — the capability probe + device support
matrix — as a portable, dependency-free Python core (`src/capability.py`,
`src/receivers.py`), a runnable CLI demo (`run.sh`), and a real unittest suite
(`tests/`). It is the honest, testable decision core an Android front-end will later
call; the Android/Kotlin UI layer is the next slice (needs an Android build lane, out of
scope for this CI).

## 💡 Session idea

Execute the owner's "build the controller app" directive without stalling: resolve the
idea doc in idea-engine, then found `products/phone-controller/` in product-forge with
the idea's own recommended **first slice** — a capability-probe verdict engine + the
sourced receiver-compatibility matrix — chosen because that logic is platform-agnostic
and therefore genuinely runnable + CI-testable now (repo is stdlib-Python; a full
Android build can't go green in this gate), while the Android UI is flagged as the next
slice.

## previous-session review

Prior session (`.sessions/2026-07-11-close-out.md`) archived the games-web phase-1 work
with the lane `archive-ready` and the inbox DRY. This session reuses that lane's proven
product shape — self-contained `products/<slug>/` subtree, stdlib-only, standalone
`python3 tests/*.py` suites gated by `bootstrap.py check --strict`, born-red session
card — and adds a second product beside `games-web` without touching it (no
cross-product imports, per `products/README.md`).

## Decide-and-flag choices (also in the PR body)

1. **Stack = portable Python core, not an Android build.** The idea implies Android/Kotlin,
   but this repo is stdlib-Python and the substrate gate has no Android SDK; a Gradle app
   could not go green. Built the platform-agnostic capability/receiver core (the idea's
   named Slice 1) so CI is genuinely green-able; Android UI = next slice.
2. **No new CI workflow.** merge-on-green parks any workflow-touching PR as owner-merge-only;
   product tests run as standalone scripts gated by the existing `substrate-gate`
   (`bootstrap.py check --strict`), matching the games-web precedent (no per-product test job).
3. **No routed inbox ORDER.** `products/README.md` says subtrees are created by a routed
   ORDER; this one is founded on a direct owner directive (ORDER-equivalent). Left
   `control/inbox.md` and `control/status.md` for the coordinator (one-writer rule).
4. **Did not arm auto-merge / self-merge** (per directive). Opened READY; left the repo's
   own server-side `merge-on-green.yml` to land it once green + this card flips `complete`.
