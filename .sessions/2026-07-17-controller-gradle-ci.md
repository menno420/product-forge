# Session — phone-controller Slice 2 (Gradle CI lane for the Android verdict port)

> **Status:** `in-progress`

📊 Model: opus-4.8 · high · CI (new `.github/workflows/android-ci.yml`)

Time: 2026-07-17 · lane: builder · owner-directive wake (Slice 2, workflow half)

## What this session is doing

Adding the CI lane that keeps Slice 2's Android verdict port green:
`.github/workflows/android-ci.yml` runs `gradle :capability-core:test` (pure-JVM Kotlin
unit tests, no Android SDK) on changes under `products/phone-controller/android/**`.

This is the **workflow half** of Slice 2, deliberately split from the code
(PR #28, `products/phone-controller/android/`, merged). Recon of the repo's own
`.github/workflows/merge-on-green.yml` (lines 171–173) shows any PR whose diff touches
`.github/workflows/**` is skipped as **owner-merge-only** — so this file must ship in its
own PR, which **parks pending owner approval of a new CI workflow**, while the reversible
code landed on its own.

## 💡 Session idea

Confine the stall to the smallest possible diff: land the Android transport + verdict port
as a normal auto-merging code PR, and carve the one genuinely owner-gated artifact — a new
CI workflow file — into a separate, tiny PR that names its own blocker. The owner reviews a
one-file diff (a build lane) instead of a whole feature, and the product doesn't wait on
that review to progress.

## previous-session review

The companion code session (`.sessions/2026-07-17-controller-android-transport.md`, PR #28,
merged by `merge-on-green` at 2026-07-17T23:55Z) shipped the Android module and flagged that
its CI-workflow file would be split here because `merge-on-green` parks workflow-touching
PRs. This session executes exactly that split. The Slice-1 close-out
(`.sessions/2026-07-17-controller-app-mvp.md`) had already recorded the same carve-out
("merge-on-green parks any workflow-touching PR as owner-merge-only") as the reason it shipped
no CI workflow — this PR is the deferred workflow, isolated so it parks alone.

## Decide-and-flag choices (also in the PR body)

1. **Lane builds the pure-JVM `:capability-core` only, not `assembleDebug`.** No Android SDK
   download → reliable + fast in CI, and it tests the load-bearing decision model directly.
   The real `BluetoothHidDevice` transport can't be unit-tested without hardware, so
   `assembleDebug` would only prove compilation. Slice 3 adds an `assembleDebug` job with an
   Android-SDK step once there's a UI. *(Reversible.)*
2. **Gradle via `gradle/actions/setup-gradle`, no committed wrapper jar.** Avoids a binary
   blob in the repo. *(Reversible — a pinned `./gradlew` wrapper can replace it in Slice 3.)*
3. **Paths-filtered triggers** (`products/phone-controller/android/**` + this workflow file)
   so the lane only runs when the Android module or the lane itself changes.
4. **Parks by design.** This PR trips `merge-on-green`'s workflow-touching rail on purpose;
   it awaits an owner merge. The blocker is named in the PR body as an OWNER-ACTION item.

## Close-out review remark

_(filled at close-out)_
