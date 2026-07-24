# phone-controller — Android side

The on-device layer of the phone-as-a-Bluetooth-controller product: the **real**
Classic-BT-HID probe + transport, the combo HID device (keyboard / gamepad / media
remote), and the controller UI. It consumes the **same** support-verdict decision
model the portable Python core (Slice 1) defines.

Idea: `menno420/idea-engine` →
`ideas/product-forge/bt-controller-plan-2026-07-17.md`. Slice history: #27 (Python
verdict core) · #28 (Kotlin port + transport skeleton) · #29/#31/#32 (Gradle/CI
wiring) · Slice 4 (combo HID + controller UI + release lane).

## Layout

```
android/
  settings.gradle.kts          # :capability-core + :hid-core always; :app SDK-gated
  capability-core/             # pure-JVM verdict engine port (lockstep w/ Python core)
    src/main/kotlin/.../capability/Capability.kt
    src/test/kotlin/.../capability/CapabilityTest.kt
  hid-core/                    # pure-JVM HID report model (Slice 4 · mouse in Slice 5)
    src/main/kotlin/.../hid/ComboHidDescriptor.kt   # descriptor + button/usage constants
    src/main/kotlin/.../hid/InputStates.kt          # Keyboard/Gamepad/Mouse state builders
    src/test/kotlin/.../hid/ComboHidDescriptorTest.kt
  app/                         # Android app module (needs the Android SDK)
    build.gradle.kts           # AGP 8.5.2 · versioning · env-driven release signing
    src/main/AndroidManifest.xml                    # configChanges: rotation keeps the connection
    src/main/res/values/strings.xml
    src/main/kotlin/.../MainActivity.kt             # permissions + status + layout picker (portrait/landscape)
    src/main/kotlin/.../ui/ControllerPads.kt        # ten layouts + SlidePadRouter (glide between buttons)
    src/main/kotlin/.../ui/TouchpadView.kt          # mouse surface (drag/tap/two-finger gestures)
    src/main/kotlin/.../transport/BluetoothHidDeviceTransport.kt  # the real transport
```

## The combo HID device (what a receiver sees)

One SDP record (`SUBCLASS1_COMBO`, name **“Phone Controller”**) with four reports:

| Report | Collection | Payload | Drives |
|---|---|---|---|
| 1 | Consumer Control | 1 byte (7 button bits + pad) | media remote (Slice-2 layout, unchanged) |
| 2 | Keyboard | 8 bytes (modifiers + reserved + 6-key array) | Keyboard + Emu-keys pads — standard BT keyboard |
| 3 | Gamepad | 7 bytes (16 buttons, hat D-pad, sticks X/Y + Z/RZ) | Full-gamepad, GBA, Analog, NDS + custom pads |
| 4 | Mouse (relative) | 4 bytes (3 buttons, dx, dy, wheel) | Touchpad + NDS touch screen — standard BT mouse |

Gamepad button bits follow the Linux-kernel convention (bit0=A/south … bit11=Start,
bits 2/5 skipped), so an Android receiver yields `KEYCODE_BUTTON_A/B/X/Y/L1/R1/
SELECT/START` and D-pad key events (synthesized from the hat axes) with no custom
keylayout. X/Y ride along centered until an analog-stick slice. The wire format is
pinned by `hid-core`'s tests; if an exotic host rejects the combo descriptor, bisect
by dropping Report 3 first.

## The decision model is shared, not duplicated

`capability-core/Capability.kt` stays a faithful port of `../src/capability.py` (same
verdict codes, same branch order; the Python suite is the canonical decision table).
`BluetoothHidDeviceTransport` feeds every live probe outcome — `registerApp()`
accepted/rejected, BLE-peripheral capability — through the shared `evaluate()`, so
the phone reaches the same verdict the Python core would, including `OEM_DISABLED`
when an API-28+ phone ships with the HID device role compiled out.

## What the transport does (Slice 4)

1. binds the `HID_DEVICE` profile proxy — `BluetoothAdapter.getProfileProxy(...)`;
2. one `registerApp(...)` with the combo SDP record + descriptor — **the runtime
   OEM-flag probe**;
3. hold-capable input APIs — `key`/`modifier` (Report 2), `gamepadButton`/`dpad`
   (Report 3), `sendMediaButton` (Report 1, tap pair) — all `sendReport(...)` on the
   interrupt channel via the `hid-core` report builders;
4. `connectTo(bondedDevice)` / `bondedHosts()` so the phone can also initiate the
   connection; all inputs auto-release on host disconnect and on `stop()`;
5. `SecurityException` (missing runtime permission) surfaces as a transport error.

## Building & testing

SDK-free lanes (no Android toolchain):

```bash
cd products/phone-controller/android
gradle :capability-core:test :hid-core:test
```

The app (needs an Android SDK — `ANDROID_HOME` set, or a `local.properties`):

```bash
gradle :app:assembleDebug      # debug APK (CI: assemble-app job, uploads the artifact)
gradle :app:assembleRelease    # release APK; signing is env-driven (below)
```

`include(":app")` stays **gated on Android-SDK presence** (Slice-3 decide-and-flag):
a bare pure-JVM test run never configures AGP, so the SDK-free lanes stay green
everywhere.

## Release signing (env-driven; keystore never committed)

`app/build.gradle.kts` reads `PC_RELEASE_KEYSTORE` (PKCS12 path),
`PC_RELEASE_KEYSTORE_PASSWORD`, optional `PC_RELEASE_KEY_ALIAS` (default
`phone-controller`) / `PC_RELEASE_KEY_PASSWORD`. Present → `assembleRelease` is
signed with that keystore; absent → falls back to debug signing (still installable).
`.github/workflows/android-release.yml` materializes the keystore from the repo
secrets `PC_RELEASE_KEYSTORE_B64` / `PC_RELEASE_KEYSTORE_PASSWORD` (stable signature
→ in-place updates) or generates an ephemeral one per run, then attaches
`phone-controller-<version>.apk` + sha256 to the GitHub Release for the
`phone-controller-v*` tag.

**The stable-signing secrets are SET (2026-07-24, OA-005 closed):** every release
from the re-signed v0.9.0 onward carries the same signature, so updates install
in place — no uninstall between versions. (Releases v0.4.0–v0.8.0 predate the
secrets and were ephemeral-signed; updating FROM one of those still needs a
one-time uninstall.) The keystore lives only in the repo secret.

## Decide-and-flag choices

- **Combo descriptor over media-only** *(Slice 4)* — keyboard + gamepad are what the
  emulator use case consumes; Report 1 keeps the Slice-2 media bytes verbatim so
  existing receivers are unaffected. Reversible per-report.
- **D-pad as hat switch + neutral X/Y** *(Slice 4)* — Android receivers synthesize
  DPAD key events from hat axes and emulators map either; the axes are already in the
  descriptor for the analog slice.
- **Pure-JVM `hid-core`** *(Slice 4)* — descriptor bytes + report bit-math are the
  load-bearing wire format, so they live where CI unit-tests them SDK-free, exactly
  like the verdict engine.
- **`include(":app")` gated on Android-SDK presence** *(Slice 3)* — unchanged.
- **Two CI jobs, kept split** *(Slice 3)* — unchanged; assemble-app now also uploads
  the debug-APK artifact.
- **No AndroidX** *(Slice 3→4)* — plain Activity + programmatic widgets keep the
  dependency graph minimal; revisit when the layout-editor slice needs real UI kit.
- **Kotlin 2.0.21 + AGP 8.5.2 + Gradle 8.x, no committed wrapper/SDK** — unchanged;
  CI provisions toolchains (`gradle/actions/setup-gradle`,
  `android-actions/setup-android`).
