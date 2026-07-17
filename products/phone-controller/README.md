# phone-controller — turn a phone into a Bluetooth-HID controller

Make a phone act as a **customizable Bluetooth-HID controller** (keyboard / mouse /
gamepad / media remote) for other phones, tablets, TVs and PCs — no software installed on
the target. Based on the Ideas-Lab plan
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

**alpha · runnable.** This is the idea doc's **Slice 1** — the *capability probe +
device support matrix* — as a portable, dependency-free Python core. It is the honest,
testable decision logic the Android front-end will call; the actual `BluetoothHidDevice`
transport and the Android/Kotlin UI are **later slices** (they need an Android build
lane and cannot go green in this repo's CI).

What works now:
- A **capability-probe verdict engine** (`src/capability.py`) that maps a device probe
  (platform, Android API level, whether the HID role registered, whether BLE-peripheral
  mode is supported) to a stable verdict — `SUPPORTED_CLASSIC_HID`, `BLE_HOGP_FALLBACK`,
  `OEM_DISABLED`, `OS_TOO_OLD`, `IOS_WALLED`, or `UNSUPPORTED_PLATFORM` — each with a
  headline, reason, recommended transport, and a `core_loop_available` flag, so an
  OEM-disabled device never reaches a broken core loop.
- The **receiver-compatibility matrix** (`src/receivers.py`) transcribed from the idea
  doc's sourced table: which targets accept a standard BT-HID keyboard/mouse/gamepad with
  no companion app.

## Run

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
./test.sh                # or: python3 -m unittest discover -s tests -p 'test_*.py'
```

26 tests covering every verdict branch (including the load-bearing API 27-vs-28 boundary
and the BLE-peripheral min-API floor) and the receiver-matrix invariants + lookups.
Exit 0 = green.

## Files

```
products/phone-controller/
├── src/capability.py     # capability-probe verdict engine (the first-run gate)
├── src/receivers.py      # receiver-compatibility matrix + lookups
├── cli.py                # demo CLI (verdicts + matrix; --platform/--api/… flags)
├── run.sh                # one-command demo
├── test.sh               # run the unittest suite
└── tests/                # test_capability.py, test_receivers.py
```

## Build ladder / next slices (from the idea doc)

`scaffold → working core → tests → README` are done this slice. Next, in the idea doc's
order (each needs an Android build lane):

2. Core BT-HID transport — register the app + `sendReport()` on the interrupt channel.
3. A minimal fixed media-remote layout driving a real receiver end-to-end.
4. **Customizable layout editor** (button grid → HID keycodes) — the differentiator /
   moat: no mainstream app is *both* serverless true-BT-HID *and* fully customizable.
5. Profiles (save / load / switch per target). 6. Gamepad HID descriptor + analog stick.
7. Foreground volume-key-as-input mapping. 8. On-device latency measurement.

`iOS-as-controller` is deferred (network companion-receiver only); background hardware-button
capture is blocked by platform policy. See the idea doc for sources and the latency budget.
