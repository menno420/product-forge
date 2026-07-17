# phone-controller — Android side (Slice 2)

The on-device layer of the phone-as-a-Bluetooth-controller product. It performs the
**real** Classic-BT-HID probe and drives a fixed media-remote HID device — and it
consumes the **same** support-verdict decision model the portable Python core (Slice 1)
defines.

Idea: `menno420/idea-engine` →
`ideas/product-forge/bt-controller-plan-2026-07-17.md`. Slice 1 (merged, PR #27) is the
portable Python verdict engine at `products/phone-controller/src/`.

## Layout

```
android/
  settings.gradle.kts          # includes ONLY :capability-core (SDK-free, CI-built)
  capability-core/             # pure-JVM Kotlin — the buildable, unit-tested module
    build.gradle.kts
    src/main/kotlin/.../capability/Capability.kt      # Kotlin port of capability.py
    src/test/kotlin/.../capability/CapabilityTest.kt  # lockstep tests (JVM, no SDK)
  app/                         # Android app SKELETON — NOT built by CI (needs the SDK)
    build.gradle.kts           # intended AGP config, commented until Slice 3
    src/main/AndroidManifest.xml
    src/main/kotlin/.../transport/BluetoothHidDeviceTransport.kt   # the real transport
    src/main/kotlin/.../transport/MediaRemoteHidDescriptor.kt      # fixed HID descriptor
```

## The decision model is shared, not duplicated

`capability-core/Capability.kt` is a faithful, behaviour-for-behaviour port of
`../src/capability.py`: same verdict codes
(`SUPPORTED_CLASSIC_HID` / `BLE_HOGP_FALLBACK` / `OEM_DISABLED` / `OS_TOO_OLD` /
`IOS_WALLED` / `UNSUPPORTED_PLATFORM`), same `ProbeInput` fields, same branch order.
`BluetoothHidDeviceTransport.probeAndEvaluate()` builds a `ProbeInput` from the live
runtime facts (`Build.VERSION.SDK_INT`, whether `registerApp()` took, BLE-peripheral
capability) and calls `evaluate()` — so the phone reaches the same verdict the Python
core would. The Python suite remains the canonical decision table; the Kotlin tests
prove the port stays in lockstep.

## What the transport does (Slice 2 skeleton)

`BluetoothHidDeviceTransport`:

1. binds the `HID_DEVICE` profile proxy — `BluetoothAdapter.getProfileProxy(...)`;
2. calls `registerApp(...)` with a fixed media-remote SDP record + HID report
   descriptor — **this is the runtime OEM-flag probe** (an API-28+ phone can still be
   rejected here, which is exactly the engine's `OEM_DISABLED` case);
3. sends input reports on the HID interrupt channel — `sendReport(...)`;
4. feeds the probe result into the shared `evaluate()` decision model.

The HID device is a single Consumer-Control "media remote" (play/pause, next, prev,
mute, vol ±, stop) — see `MediaRemoteHidDescriptor`. Customisable layouts, pairing UI,
reconnection, and the BLE-HOGP fallback are **Slice 3+**.

## Building & testing

CI builds the SDK-free module only:

```bash
cd products/phone-controller/android
gradle :capability-core:test        # pure-JVM Kotlin unit tests, no Android SDK
```

The `app/` module is skeleton source and is intentionally **not** in the Gradle build
yet (it needs the Android SDK + AGP). Slice 3 wires it in and adds an `assembleDebug`
CI job. The CI lane that runs the command above lives in
`.github/workflows/android-ci.yml` (added in a companion, owner-gated workflow PR — the
repo's `merge-on-green` parks any `.github/workflows/**` change for owner review).

## Decide-and-flag choices

- **CI builds a pure-Kotlin verdict port, not `assembleDebug`.** A JVM unit-test lane
  needs no Android SDK download, so it is far more reliable in CI, and it tests the
  load-bearing decision model directly. The real `BluetoothHidDevice` transport cannot
  be unit-tested without hardware anyway, so `assembleDebug` would only prove it
  compiles — lower value than testing the verdict port. Upgrade to `assembleDebug` in
  Slice 3 when there is a UI worth compiling. *(Reversible.)*
- **`app/` kept out of `settings.gradle.kts`.** Keeps `gradle :capability-core:test`
  Android-toolchain-free; the app module is reviewable skeleton source until Slice 3.
- **Kotlin 2.0.21 + Gradle 8.x, no committed wrapper jar.** CI provides Gradle via the
  `gradle/actions/setup-gradle` action instead of a committed binary wrapper jar,
  avoiding a binary blob in the repo. *(Reversible.)*
