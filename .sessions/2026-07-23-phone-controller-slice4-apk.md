# Session — phone-controller Slice 4: usable controller UI + downloadable APK (builder lane)

> **Status:** `in-progress`

📊 Model: fable-5 · high · build

Time: 2026-07-23 · lane: builder (phone-controller · slice 4) · owner-live directive

💡 Session idea: finish the controller app into a product a person can actually use —
a downloadable APK that turns the phone into a Bluetooth-HID input device for other
Android devices (pair, open an emulator, play). Owner's live instruction this session:
*"make sure this app will be a downloadable apk file that works on android and can be
used as input device for other android devices, for example to use with emulators."*

## previous-session review

Slices 1–3 (PRs #27, #28, #29, #31, #32) left the product honest but unusable:

- Slice 1 — portable Python verdict engine + receiver matrix (the matrix's first row is
  exactly this session's use case: *Android phone/tablet — keyboard yes, mouse yes,
  gamepad yes, out-of-box*).
- Slice 2 — Kotlin port (`:capability-core`, lockstep-tested) + the real
  `BluetoothHidDeviceTransport` + a fixed media-remote descriptor.
- Slice 3 — `:app` wired into the Gradle build (SDK-gated include) + an `assembleDebug`
  CI job proving the module compiles.

Gaps this session closes: `MainActivity` was a stub that never called
`transport.start()` (no permission flow, no pairing, no buttons); the HID descriptor
was media-remote-only (useless for emulators); CI built the APK and then threw it away
(no artifact, no release); no install/usage docs.

## What this build did

**A · `:hid-core` — pure-JVM HID report model (new module, tested).** Combo HID
descriptor (Report 1 = consumer media, Report 2 = keyboard, Report 3 = gamepad with
16 buttons + hat-switch D-pad + centered X/Y axes), `KeyboardState` (6-key rollover +
modifiers), `GamepadState` (button bits, D-pad→hat combine with opposite-direction
cancel), `MediaButton`/`GamepadButton`/`KeyUsage` constants — all SDK-free, unit-tested
like `:capability-core` (same pure-JVM pattern). Gamepad button bit positions follow the
Linux-kernel HID-gamepad convention (bit0=A/south … bit11=Start) so the receiving
Android maps them to `KEYCODE_BUTTON_A`…`BUTTON_START` without a custom keylayout.

**B · Transport upgrade.** `BluetoothHidDeviceTransport` now registers the combo
descriptor and exposes hold-capable input APIs: `keyDown/keyUp` + modifiers (keyboard),
`gamepadButtonDown/Up` + `setDpad` (gamepad), `sendMediaButton` (unchanged), plus
`connectTo(bonded device)`, bonded-device listing, and stuck-input protection (all-release
reports on host disconnect and on stop). `SecurityException` paths surface as transport
errors instead of crashes.

**C · Controller UI.** `MainActivity` is now the real thing: runtime
`BLUETOOTH_CONNECT`/`BLUETOOTH_ADVERTISE` request (API 31+), transport start + live
status line (verdict → advertising → connected-to-⟨host⟩), *Make discoverable* and
*Connect (bonded devices)* actions, and three switchable pads — **Gamepad** (D-pad,
A/B/X/Y, L1/R1, Select/Start), **Keys** (arrows, Z/X/A/S, Enter/Space/Shift/Esc — classic
emulator-default keys), **Media** (the Slice-2 remote). Buttons send press on
ACTION_DOWN and release on ACTION_UP, so holds work (hold-to-run in games). Screen
stays on while the controller is open. Still no-AndroidX by design.

**D · Downloadable APK plumbing** (companion workflow PR — owner-merge-only rail):
`android-ci.yml` now uploads the debug APK as a run artifact and runs `:hid-core:test`
in the SDK-free lane; new `android-release.yml` builds a **signed release APK** on a
`phone-controller-v*` tag (or manual dispatch) and attaches it + a sha256 to a GitHub
Release. Signing prefers the repo keystore secrets (`PC_RELEASE_KEYSTORE_B64` /
`PC_RELEASE_KEYSTORE_PASSWORD` — stable signature, in-place updates); with no secrets it
falls back to an ephemeral keystore (installable, but updates need uninstall). Gradle
release build falls back to debug signing outside CI so `assembleRelease` always yields
an installable APK.

**E · Docs.** Product README rewritten around the real user path: download from
Releases → sideload → grant Bluetooth permissions → make discoverable → pair from the
target device → map buttons in the emulator (RetroArch notes included). Android README
updated for the new module/descriptor layout. Verdict semantics unchanged — the Python
core stays the canonical decision table; no `:capability-core` or `src/` changes.

## Decide-and-flag

- **Combo descriptor over media-only** — keyboard + gamepad are what emulators consume;
  media kept as a third pad. Reversible (report IDs are additive).
- **D-pad as hat switch (+ neutral X/Y axes)** — Android synthesizes DPAD key events
  from hat axes, and emulators map either; axes stay centered until an analog UI slice.
- **Kernel-convention gamepad bits (skip C/Z at bits 2/5)** — matches BTN_SOUTH-first
  mapping on the Android receiver; labels on the pad match the keycodes the target sees.
- **Two PRs** — code (this card's PR, auto-lands on green) + workflows (separate PR under
  merge-on-green's owner-merge-only rail, hub-merged under the owner's live directive,
  same precedent as PR #29 / `OQ-FORGE-29-WORKFLOW-MERGE`).
- **Release signing via repo secrets, keystore never committed** — secrets are the
  fleet-verified path (capabilities ledger); ephemeral fallback keeps the release lane
  green with zero secrets.

## Guard recipes

- **Hardware playtest is the one thing CI cannot prove.** `assembleDebug`/`assembleRelease`
  prove compile + packaging; `registerApp()` acceptance and end-to-end input delivery
  need two physical devices. Flagged ⚑ in `control/status.md` as the owner playtest
  step (`BluetoothHidDeviceTransport.start()` → `MainActivity` status line is the
  on-device evidence surface).
- **If a picky host rejects the combo descriptor**, bisect by dropping report 3 (gamepad)
  first — `ComboHidDescriptor.DESCRIPTOR` in
  `products/phone-controller/android/hid-core/.../ComboHidDescriptor.kt`; the
  descriptor-invariant tests in `hid-core/src/test/.../ComboHidDescriptorTest.kt` pin the
  report IDs/lengths.

## Verify

```bash
cd products/phone-controller/android
gradle :capability-core:test :hid-core:test   # SDK-free lanes
gradle :app:assembleDebug                     # needs ANDROID_HOME (CI: assemble-app job)
python3 bootstrap.py check --strict           # substrate gate, locally
```
