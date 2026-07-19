# Session — phone-controller Slice 3 (wire the Android app module into the build)

> **Status:** `in-progress`

📊 Model: Opus 4.8 · high · build (Android `app/` module wired into Gradle + assembleDebug proof)

Time: 2026-07-19 · lane: builder · owner-directive wake (Slice 3, code half)

## What this session is doing

Building the **agent-buildable, CI-provable** portion of **Slice 3** of
`products/phone-controller/` — the phone-as-a-Bluetooth-controller app — per the owner
directive ("create the controller app; don't let anything stall; skip only what truly
needs my input"). Idea doc: `menno420/idea-engine` at
`ideas/product-forge/bt-controller-plan-2026-07-17.md` (phone = customisable BT-HID
controller; the Android HID device role is OEM-gated so `registerApp()` must be probed
at runtime; iOS is walled).

Slices 1 & 2 (merged): Slice 1 (PR #27) = portable Python verdict engine
`src/capability.py`; Slice 2 (PR #28 code + #29 CI) = `android/` Gradle project with a
pure-JVM Kotlin port `capability-core/` (CI-built) and an Android `app/` **skeleton**
holding the real `BluetoothHidDeviceTransport` — but `app/` was deliberately NOT in
`settings.gradle.kts` and NOT compiled by CI.

This slice (CODE half) wires `app/` into the build:

- `settings.gradle.kts` — `include(":app")`, gated on Android-SDK presence (see idea
  #1 below), plus `pluginManagement` / `dependencyResolutionManagement` so AGP + its
  deps resolve from Google's Maven.
- `app/build.gradle.kts` — the real AGP config (was documentation-in-a-comment):
  `com.android.application` 8.5.2 + `kotlin("android")` 2.0.21, compileSdk 34, minSdk 28,
  a `debug` build type, depending on `:capability-core`.
- `app/src/main/kotlin/.../MainActivity.kt` — a minimal capability-screen entry point
  (plain `android.app.Activity` + a programmatic `TextView`, no AndroidX) that builds the
  real transport and renders the verdict the shared `evaluate()` returns — compile-proof
  that the AGP build, `:capability-core`, and `BluetoothHidDeviceTransport` wire together.
- `app/src/main/res/values/strings.xml` + `AndroidManifest.xml` — the launcher activity
  registration and its display strings.

The `assembleDebug` CI job (an `android-ci.yml` edit) is the **workflow half**, split
into a separate owner-gated PR — `merge-on-green.yml` parks any `.github/workflows/**`
change as owner-merge-only, so bundling it here would park this whole code PR.

## 💡 Session idea

Wire the Android app in without sacrificing the SDK-free unit lane: gate `include(":app")`
on Android-SDK presence so a bare `gradle :capability-core:test` (the existing, fast,
SDK-free CI job) never even configures the AGP module — because Gradle configures *every*
included project and applying AGP without an SDK fails the whole build. The one job that
provisions the SDK (assembleDebug) sees `ANDROID_HOME` and picks up `:app`; every other
lane stays light. One conditional line keeps two invariants — the app compiles AND the
verdict lane never needs an Android toolchain — instead of trading one for the other.

## previous-session review

The Slice-2 code card (`.sessions/2026-07-17-controller-android-transport.md`, PR #28)
and CI card (`.sessions/2026-07-17-controller-gradle-ci.md`, PR #29) both flagged the
exact Slice-3 next step and left a guard recipe: *"to build `app/` in CI, uncomment
`include(":app")` in `settings.gradle.kts`, add an `android-actions/setup-android` step,
and a second job running `gradle :app:assembleDebug`."* This session executes that recipe,
refining it: `include(":app")` is made SDK-conditional so the existing SDK-free
`:capability-core:test` job stays green on this very PR (an unconditional include would
red it). Their `.gitignore` guard (build artifacts leak into the module) is honored — the
Slice-2 `android/.gitignore` already excludes `build/` + `.gradle/` + `local.properties`,
so the locally-built `app-debug.apk` and SDK-generated files are never staged.

## Decide-and-flag choices (also in the PR body)

1. **`include(":app")` gated on Android-SDK presence** (`ANDROID_HOME` /
   `ANDROID_SDK_ROOT` / `local.properties`). Applying AGP needs a resolvable SDK;
   configuring `:app` without one fails the WHOLE build, including `gradle
   :capability-core:test` (Gradle configures every included project). Gating the include
   keeps the SDK-free verdict lane green while the assembleDebug job — which provisions the
   SDK — gets the app module. *(Reversible — drop the guard once every lane provisions the
   SDK.)*
2. **Two PRs, code + workflow.** This CODE PR (settings + build.gradle + Activity +
   manifest + strings, NO workflow) auto-lands; the `android-ci.yml` assembleDebug edit
   ships in a companion WORKFLOW PR that parks owner-merge-only (named ⚑ OWNER-ACTION),
   because `merge-on-green.yml` (lines 171–173) skips any `.github/workflows/**` diff.
   Ordering: this code PR must land first so the workflow PR's assembleDebug builds against
   a `main` that already has the wired `app/`.
3. **Stub `MainActivity` = plain `android.app.Activity` + programmatic `TextView`, no
   AndroidX/appcompat, no layout XML.** Smallest thing that compiles the whole wiring; the
   real customisable controller UI (button grid, pairing, reconnection) is a later slice.
   *(Reversible.)*
4. **AGP/Gradle via `gradle/actions/setup-gradle` + `android-actions/setup-android`, no
   committed wrapper jar / no committed SDK.** Avoids a binary blob in the repo; matches the
   existing capability-core lane. *(Reversible.)*
5. **Did not touch `control/status.md` / `control/inbox.md`** (one-writer rule — the
   coordinator's) and did not arm auto-merge / self-merge (per directive); opened READY and
   left `merge-on-green.yml` to land this once green + this card flips `complete`.

## Owner-gated / parked (not attempted here)

- The real "receiver driving end-to-end" playtest needs a phone + a real BT-HID receiver —
  genuinely hardware-gated (**owner playtests**). Slice 3's agent scope is compile-green
  proof only.
- The `android-ci.yml` assembleDebug edit parks owner-merge-only in the companion workflow
  PR (⚑ OWNER-ACTION).

## Close-out review remark

_(filled in at flip-to-complete.)_
