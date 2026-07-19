# Session — phone-controller Slice 3 (assembleDebug CI job for the Android app module)

> **Status:** `complete`

📊 Model: Opus 4.8 · high · CI (`.github/workflows/android-ci.yml` — add an assembleDebug job)

Time: 2026-07-19 · lane: builder · owner-directive wake (Slice 3, workflow half)

## What this session is doing

Adding the CI job that compiles Slice 3's Android **app** module:
`.github/workflows/android-ci.yml` gains a second job, `assemble-app`, that provisions the
Android SDK (`android-actions/setup-android`) and runs `gradle :app:assembleDebug` — proving
the real `BluetoothHidDeviceTransport` + capability-screen entry point compile into a debug
APK. The existing SDK-free `capability-core` job (`gradle :capability-core:test`) is kept
unchanged.

This is the **workflow half** of Slice 3, deliberately split from the code (PR #31,
`products/phone-controller/android/`, merged f527ca0 — it wired `app/` into
`settings.gradle.kts` with an SDK-gated `include(":app")`). Recon of the repo's own
`merge-on-green.yml` (lines 171–173) shows any PR whose diff touches `.github/workflows/**`
is skipped as **owner-merge-only** — so this file ships in its own PR, which **parks pending
owner approval**, while the reversible code already landed.

Ordering honored: this branch is cut **after** PR #31 merged, so the `assemble-app` job
builds against a `main` that already has the wired `app/` — `gradle :app:assembleDebug`
resolves to a real task instead of "task not found".

## 💡 Session idea

Keep the compile proof honest AND the fast lane fast: don't fold assembleDebug into the
existing verdict-test job (that would drag an Android-SDK download onto every verdict-model
change). Add it as a *separate* job that provisions the SDK, so the SDK-free
`capability-core` lane still gives a 60-second green on decision-model edits while
`assemble-app` independently proves the real transport still compiles. Two jobs, two
failure signals — a broken verdict table and a broken Android build never hide behind each
other.

## previous-session review

The Slice-3 code card (`.sessions/2026-07-19-controller-app-module.md`, PR #31) left the
exact guard recipe this session executes: *"the assembleDebug CI job MUST
`android-actions/setup-android` so ANDROID_HOME is exported — that env var is what flips
`include(":app")` on in settings.gradle.kts; without it the app module is silently skipped
and assembleDebug finds no `:app` task."* This job does exactly that, and additionally
`sdkmanager --install "platforms;android-34" "build-tools;34.0.0"` for determinism over
AGP's auto-download. The Slice-2 CI card
(`.sessions/2026-07-17-controller-gradle-ci.md`, PR #29) predicted this precise addition
("Slice 3 adds an `assembleDebug` job with an Android-SDK step once there's a UI").

## Decide-and-flag choices (also in the PR body)

1. **Separate `assemble-app` job, not folded into `capability-core`.** Keeps the SDK-free
   verdict lane fast; the SDK download only runs on the compile-proof job. *(Reversible.)*
2. **SDK via `android-actions/setup-android` + explicit `sdkmanager` install of
   `platforms;android-34` + `build-tools;34.0.0`; Gradle via `gradle/actions/setup-gradle`.**
   No committed SDK, no committed wrapper jar — no binary blob in the repo; deterministic
   over AGP auto-download. *(Reversible.)*
3. **Parks by design.** This PR trips `merge-on-green`'s workflow-touching rail on purpose;
   it awaits an owner merge. The blocker is named as ⚑ OWNER-ACTION in the PR body.
4. **Did not touch `control/status.md` / `control/inbox.md`** (one-writer rule); did not arm
   auto-merge / self-merge.

## Owner-gated / parked

- ⚑ OWNER-ACTION: this PR needs an **owner merge** — `merge-on-green` parks any
  `.github/workflows/**` change owner-merge-only.
- The real receiver-driving playtest stays hardware-gated (**owner playtests**).

## Close-out review remark

Shipped the Slice-3 CI workflow half in **PR #32** (`claude/controller-assembledebug-ci`):
`.github/workflows/android-ci.yml` gains an `assemble-app` job that provisions the Android
SDK (`android-actions/setup-android` + `sdkmanager platforms;android-34 build-tools;34.0.0`)
and runs `gradle :app:assembleDebug`. **It went GREEN on the PR** — the CI `assemble-app`
check reported `BUILD SUCCESSFUL in 1m` (36 actionable tasks executed), so the real
`BluetoothHidDeviceTransport` + `MainActivity` compile into a debug APK *in CI*, not just
locally; the SDK-provisioned job set `ANDROID_HOME`, which flipped `include(":app")` on
exactly as designed. The SDK-free `capability-core` job stayed green in the same run
(≈1 min, no Android toolchain), proving the two-lane split holds. As designed, the PR
**parks owner-merge-only** on `merge-on-green`'s workflow-touching rail — the blocker is
named ⚑ OWNER-ACTION in the PR body; the reversible code half already auto-landed (#31).
Guard recipe for the next slice: the customisable controller UI will add AndroidX/Compose
deps — when it does, `assemble-app` gains a Gradle dependency-cache warm-up cost; the
`gradle/actions/setup-gradle` cache in this job already covers it, but a pinned
`./gradlew` wrapper becomes worth reconsidering once the dependency graph grows. The real
receiver-driving playtest remains hardware-gated (**owner playtests**).
