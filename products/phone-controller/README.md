# phone-controller — turn a phone into a Bluetooth-HID controller

Make a phone act as a **customizable Bluetooth-HID controller** (keyboard / gamepad /
media remote) for other phones, tablets, TVs and PCs — no software installed on the
target. Based on the Ideas-Lab plan
[`ideas/product-forge/bt-controller-plan-2026-07-17.md`](https://github.com/menno420/idea-engine/blob/48a9d7d7f71371248a49e8ca4573e9ad10b1abd7/ideas/product-forge/bt-controller-plan-2026-07-17.md)
(menno420/idea-engine, blob `970edbfc`, HEAD `48a9d7d7`).

## Viability (from the idea doc's research pass)

- **Android — viable, but OEM-gated.** `BluetoothHidDevice` (Android 9 / API 28+) lets a
  phone *be* a true Classic BT-HID device, but the HID device role sits behind an OEM
  compile-time flag, so `registerApp()` fails on many shipped phones and support must be
  **probed at runtime, never assumed.**
- **iOS — not viable as a HID peripheral.** iOS blocks the HID role at the OS level; an
  iPhone can only be a *receiver* or a *network companion-controller*, never a BT-HID
  peripheral.

## State

**beta · usable controller, hardware playtest pending.** Slices 1–4 are built and
CI-proven: the capability verdict engine (portable Python + lockstep Kotlin port), the
real `BluetoothHidDevice` transport, a **combo HID device** (keyboard + gamepad + media
remote), a working controller UI (permission flow, pairing actions, three hold-capable
pads), and a release pipeline that publishes a signed, installable APK. What CI cannot
prove is the physical end-to-end (two real devices over the air) — that is the
remaining playtest step, and the app's own verdict screen reports honestly per device.

## Get the app (Android 9+)

- **Releases (recommended):** download `phone-controller-<version>.apk` from
  <https://github.com/menno420/product-forge/releases> (each release carries the APK +
  its sha256; new releases are cut by tagging `phone-controller-v*` —
  `.github/workflows/android-release.yml`).
- **Per-CI-run artifact:** every green `android-ci` run uploads a
  `phone-controller-debug-apk` artifact (Actions → run → Artifacts).

Install: open the downloaded APK on the phone and allow *install unknown apps* for the
browser/files app when prompted (normal sideload flow — this app is not on a store).

## Use it as a controller for another Android device (emulators etc.)

1. **Open the app** on the controller phone → grant the **Nearby devices / Bluetooth**
   permission it requests (Android 12+).
2. The status line shows the **capability verdict** from the shared decision engine.
   `SUPPORTED_CLASSIC_HID — Ready` means go; `OEM_DISABLED` means this phone's
   manufacturer compiled the HID-device role out (a known, device-specific platform
   gate — the app detects it honestly instead of half-working; try another phone as
   the controller).
3. Tap **Discoverable**, then on the **target** device: *Settings → Bluetooth → pair
   new device* → pair with **“Phone Controller”**. (Already paired once? Just tap
   **Connect…** and pick the device.)
4. When the status reads **Connected — controller is live**, pick a pad:
   - **Gamepad** — D-pad + A/B/X/Y + L1/R1 + Select/Start. The target Android sees a
     standard HID gamepad (`KEYCODE_BUTTON_A`…, D-pad from the hat switch) — emulators
     auto-detect it as a controller.
   - **Keys** — arrows, Z/X/A/S, Enter/Space/Shift/Esc/Tab as a real BT keyboard (the
     classic emulator default binds; also drives TV UIs, slide decks, anything
     keyboard-driven).
   - **Media** — play/pause, next/prev, stop, volume, mute (the Slice-2 remote).
   Buttons **hold**: press-and-hold walks your character; it is a held HID report, not
   repeated taps.
5. **In the emulator** (e.g. RetroArch): Settings → Input → Port 1 Controls → map each
   button by pressing it on the phone — or use the Keys pad with the emulator's default
   keyboard binds and map nothing at all.

Receiver compatibility beyond Android (PCs, TVs): `./run.sh` prints the Slice-1
sourced matrix; its first row (*Android phone/tablet: keyboard/mouse/gamepad — yes,
out-of-box*) is exactly this use case.

## Run (portable verdict core — no phone needed)

```bash
./run.sh                 # verdicts for representative device scenarios + the matrix
# evaluate one explicit probe:
./run.sh --platform android --api 34 --hid-role --ble-peripheral
./run.sh --platform android --api 33 --no-hid-role --ble-peripheral
./run.sh --platform ios
```

Stdlib-only — no dependencies, no network.

## Test

```bash
./test.sh                                     # Python decision table (canonical)
cd android && gradle :capability-core:test :hid-core:test   # Kotlin lockstep + HID model
```

26 Python tests covering every verdict branch; the Kotlin suites prove the Android port
stays in lockstep and pin the HID wire format (descriptor invariants, report bit-math,
6-key rollover, D-pad→hat combine). Exit 0 = green.

## Files

```
products/phone-controller/
├── src/capability.py     # capability-probe verdict engine (canonical decision table)
├── src/receivers.py      # receiver-compatibility matrix + lookups
├── cli.py · run.sh · test.sh · tests/
└── android/              # the app (see android/README.md)
    ├── capability-core/  # pure-JVM Kotlin port of the verdict engine (lockstep tests)
    ├── hid-core/         # pure-JVM combo HID descriptor + report builders (tests)
    └── app/              # the controller app: transport + pads UI (APK)
```

## Build ladder / next slices (from the idea doc)

Slices 1–4 done: scaffold → working core → tests → README → **release artifact** (the
APK release lane). Remaining, in the idea doc's order:

5. **Customizable layout editor** (button grid → HID keycodes) — the differentiator:
   no mainstream app is *both* serverless true-BT-HID *and* fully customizable.
6. Profiles (save / load / switch per target). 7. Analog stick on the gamepad's X/Y
   (the descriptor already carries centered axes). 8. Foreground volume-key-as-input
   mapping. 9. On-device latency measurement. BLE-HOGP fallback transport for
   `BLE_HOGP_FALLBACK`-verdict devices.

`iOS-as-controller` is deferred (network companion-receiver only); background
hardware-button capture is blocked by platform policy. See the idea doc for sources and
the latency budget.
