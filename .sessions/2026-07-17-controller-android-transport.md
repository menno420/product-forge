# Session — phone-controller Slice 2 (Android BluetoothHidDevice transport + Kotlin verdict port)

> **Status:** `in-progress`

📊 Model: opus-4.8 · high · build (Android module skeleton under `products/phone-controller/android/`)

Time: 2026-07-17 · lane: builder · owner-directive wake (Slice 2)

## What this session is doing

Building **Slice 2** of `products/phone-controller/` — the phone-as-a-Bluetooth-controller
app — per the owner directive (2026-07-17) to "create the controller app". The idea doc is
`menno420/idea-engine` at `ideas/product-forge/bt-controller-plan-2026-07-17.md`
(blob `970edbfc`): phone = customizable Bluetooth-HID controller; Android viable but the HID
device role is OEM-gated (behind an OEM compile-flag on the `BluetoothHidDevice` API 28+),
so `registerApp()` must be probed at runtime; iOS is walled.

Slice 1 (merged, PR #27) shipped the portable Python verdict engine `src/capability.py`
(`evaluate(ProbeInput) -> Verdict`) + receiver matrix + CLI + stdlib tests. This slice adds,
under `products/phone-controller/android/`:

- **`capability-core/`** — a pure-JVM Kotlin port of `capability.py`: same verdict enum
  (`SUPPORTED_CLASSIC_HID` / `BLE_HOGP_FALLBACK` / `OEM_DISABLED` / `OS_TOO_OLD` /
  `IOS_WALLED` / `UNSUPPORTED_PLATFORM`), same `ProbeInput`, same branch order, plus JVM
  unit tests proving lockstep with the Python core. SDK-free so CI can build it green.
- **`app/`** — the REAL `BluetoothHidDeviceTransport`: `getProfileProxy(HID_DEVICE)` →
  `registerApp(...)` with a fixed media-remote SDP + HID report descriptor (the runtime
  OEM-flag probe) → `sendReport(...)` on the interrupt channel → feeds the probe result into
  the shared `evaluate()`. Android-app skeleton (needs the SDK); not CI-built yet.

The Gradle CI lane (`.github/workflows/android-ci.yml`) is split into a **companion,
owner-gated workflow PR** — `merge-on-green.yml` parks any `.github/workflows/**` change as
owner-merge-only (lines 171–173), so bundling it here would park this whole code PR.

## 💡 Session idea

Ship the Android layer as a *shared decision model*, not a rewrite: port `evaluate()` to
Kotlin one-for-one and have the on-device `BluetoothHidDevice` transport CALL it, so the
phone and the portable Python core can never drift on what "supported" means — and make the
thing CI proves be that port (a JVM unit-test lane, no Android SDK), because the real
hardware transport can't be unit-tested anyway. Split the CI-workflow file into its own
owner-gated PR so the reversible code lands now and only the truly owner-gated bit waits.

## previous-session review

Prior session (`.sessions/2026-07-17-controller-app-mvp.md`) shipped Slice 1 in PR #27:
the platform-agnostic capability/receiver core, gated by the existing `substrate-gate`
(`bootstrap.py check --strict`), with NO new CI workflow — it flagged that "merge-on-green
parks any workflow-touching PR as owner-merge-only" and that a full Android build "can't go
green in this gate". This slice acts on exactly that flag: it adds the Android module the
prior slice named as next, and confines the CI-workflow requirement to a separate owner-gated
PR (the split the prior card predicted). Its guard recipe — a stdlib product's `git add`
picks up `__pycache__`; add a `.gitignore` in the scaffold commit — is honored here with an
`android/.gitignore` that excludes `build/` and `.gradle/`, committed with the module.

## Decide-and-flag choices (also in the PR body)

1. **CI target = pure-Kotlin verdict port, not `assembleDebug`.** A JVM unit-test lane needs
   no Android SDK download (far more reliable in CI) and tests the load-bearing decision
   model directly; the real `BluetoothHidDevice` transport can't be unit-tested without
   hardware, so `assembleDebug` would only prove compilation. Upgrade to `assembleDebug` in
   Slice 3 when there's a UI worth compiling. *(Reversible.)*
2. **Split into two PRs.** Recon of `merge-on-green.yml` (lines 171–173: any PR whose diff
   touches `.github/workflows/**` is skipped as owner-merge-only) confirms a workflow file
   would park this PR. So: this CODE PR (android module, no workflow) auto-lands; a tiny
   companion WORKFLOW PR (`android-ci.yml` only) parks owner-gated, blocker named in its body.
3. **`app/` kept out of `settings.gradle.kts`.** So `gradle :capability-core:test` stays
   Android-toolchain-free; the transport + manifest + AGP config are reviewable skeleton
   source, wired into the build in Slice 3.
4. **Did not touch `control/status.md` / `control/inbox.md`.** One-writer rule — those are the
   coordinator's. Did not arm auto-merge / self-merge (per directive); opened READY and left
   the repo's own `merge-on-green.yml` to land the code PR once green + this card flips
   `complete`.

## Close-out review remark

_(filled at close-out)_
