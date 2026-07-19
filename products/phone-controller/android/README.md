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
  app/                         # Android app module — assembleDebug (needs the Android SDK)
    build.gradle.kts           # AGP config (com.android.application 8.5.2)
    src/main/AndroidManifest.xml
    src/main/res/values/strings.xml                                # capability-screen strings
    src/main/kotlin/.../MainActivity.kt                            # capability-screen entry point
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

The SDK-free verdict port needs no Android toolchain:

```bash
cd products/phone-controller/android
gradle :capability-core:test        # pure-JVM Kotlin unit tests, no Android SDK
```

The `app/` module is wired into the build (Slice 3) but `include(":app")` is **gated on
Android-SDK presence** — a bare `:capability-core:test` never configures AGP, so the
verdict lane stays SDK-free. With an Android SDK provisioned (`ANDROID_HOME` set, e.g. by
`android-actions/setup-android`):

```bash
gradle :app:assembleDebug           # builds the debug APK (needs the Android SDK + AGP)
```

The CI lane lives in `.github/workflows/android-ci.yml`. Its `assembleDebug` job is added
in a companion, owner-gated workflow PR — the repo's `merge-on-green` parks any
`.github/workflows/**` change for owner review.

## Decide-and-flag choices

- **`include(":app")` gated on Android-SDK presence.** Applying AGP needs a resolvable
  SDK; configuring `:app` without one fails the *whole* build (Gradle configures every
  included project), which would red the SDK-free `:capability-core:test` lane. Gating the
  include on `ANDROID_HOME` / `ANDROID_SDK_ROOT` / `local.properties` keeps the verdict
  lane Android-toolchain-free while the assembleDebug job (which provisions the SDK) gets
  the app module. *(Reversible — drop the guard once every lane provisions the SDK.)*
- **Two CI jobs, kept split.** `:capability-core:test` stays a fast, SDK-free job that
  tests the load-bearing decision model directly; `:app:assembleDebug` is a separate job
  that provisions the Android SDK and proves the real transport compiles. The transport
  can't be unit-tested without hardware, so assembleDebug proves compilation, not runtime.
- **Stub `MainActivity`, no AndroidX.** Plain `android.app.Activity` + a programmatic
  `TextView` — the smallest entry point that compiles the AGP build against the shared
  decision model and the real transport. The customisable controller UI is a later slice.
  *(Reversible.)*
- **Kotlin 2.0.21 + AGP 8.5.2 + Gradle 8.x, no committed wrapper jar / no committed SDK.**
  CI provides Gradle via `gradle/actions/setup-gradle` and the SDK via
  `android-actions/setup-android`, avoiding a binary blob in the repo. *(Reversible.)*
