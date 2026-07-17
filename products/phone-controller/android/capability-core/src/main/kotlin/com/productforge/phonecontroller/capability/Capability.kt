/*
 * Capability-probe verdict engine — Kotlin port of the Slice-1 Python core.
 *
 * This is a FAITHFUL, behaviour-for-behaviour port of
 * `products/phone-controller/src/capability.py` (Slice 1, merged in PR #27). The
 * Android layer must consume the SAME decision model the portable Python core
 * defines, so the verdict enum, the ProbeInput fields, and the branch order here
 * mirror `capability.evaluate()` exactly. When the two disagree, the Python core is
 * the reference (its stdlib test-suite is the canonical decision table); this port
 * is unit-tested in CI to prove it stays in lockstep.
 *
 * The engine is deliberately platform-agnostic and side-effect free. On a device,
 * `BluetoothHidDeviceTransport` performs the REAL probe (attempt one
 * `BluetoothHidDevice.registerApp()`, check BLE-peripheral capability) and feeds the
 * *results* into [evaluate], which returns a stable, testable verdict. Keeping the
 * decision table out of the transport is what lets it be JVM-unit-tested with no
 * Android hardware.
 *
 * Grounding facts (all from the idea doc's sourced research pass —
 * menno420/idea-engine : ideas/product-forge/bt-controller-plan-2026-07-17.md):
 *
 *  * `BluetoothHidDevice` (the "be a HID keyboard/mouse/gamepad" device role) was
 *    introduced in Android 9 / API level 28. It is Classic Bluetooth HID, not BLE.
 *  * The HID device role sits behind an OEM compile-time flag, so an API-28+ phone
 *    can still fail `registerApp()` — support MUST be probed at runtime, never
 *    assumed.
 *  * A BLE-HOGP fallback (a manual GATT HID server, API 21+) can work on some phones
 *    where the Classic HID device role is disabled — but BLE peripheral / advertiser
 *    mode is itself a per-device hardware capability that must be checked at runtime.
 *  * iOS blocks the HID role at the OS level (HID-over-GATT UUID 0x1812 is refused;
 *    Classic HID is not exposed to apps). An iPhone can only be a network controller
 *    to a companion receiver app, or act as a receiver — never a BT-HID peripheral.
 */
package com.productforge.phonecontroller.capability

/** Grounding constants (idea doc research pass). */
object CapabilityConstants {
    /** Android 9 (Pie) — `BluetoothHidDevice` introduced. */
    const val BLUETOOTH_HID_DEVICE_MIN_API = 28

    /** Android 5 (Lollipop) — BLE peripheral / GATT server. */
    const val BLE_PERIPHERAL_MIN_API = 21

    const val PLATFORM_ANDROID = "android"
    const val PLATFORM_IOS = "ios"
}

/**
 * The recommended transport a verdict points at.
 *
 * Mirrors the Python `TRANSPORT_*` string constants; [NONE] is the Python `None`.
 */
enum class Transport {
    /** True BT-HID via `BluetoothHidDevice`. */
    CLASSIC_HID,

    /** Manual HID-over-GATT fallback. */
    BLE_HOGP,

    /** Wi-Fi/UDP to a receiver app (the iOS path). */
    NETWORK_COMPANION,

    /** No usable transport. */
    NONE,
}

/**
 * The stable verdict codes. One-for-one with the Python module's string codes so a
 * verdict serialises identically across the two implementations.
 */
enum class VerdictCode {
    SUPPORTED_CLASSIC_HID,
    BLE_HOGP_FALLBACK,
    OEM_DISABLED,
    OS_TOO_OLD,
    IOS_WALLED,
    UNSUPPORTED_PLATFORM,
}

/**
 * The runtime facts a device reports about itself.
 *
 * On Android the transport fills these from a *real* probe:
 *  * [osApiLevel] — `Build.VERSION.SDK_INT`
 *  * [hidDeviceRoleAvailable] — did a one-shot `BluetoothHidDevice` service bind +
 *    `registerApp()` succeed? (The OEM compile-flag gate shows up here.)
 *  * [blePeripheralSupported] — `BluetoothAdapter.isMultipleAdvertisementSupported()`
 *    / peripheral-mode advertising available.
 */
data class ProbeInput(
    val platform: String,
    val osApiLevel: Int = 0,
    val hidDeviceRoleAvailable: Boolean = false,
    val blePeripheralSupported: Boolean = false,
) {
    init {
        require(osApiLevel >= 0) { "osApiLevel must be a non-negative int" }
        require(platform.isNotEmpty()) { "platform must be a non-empty string" }
    }
}

/** A stable, renderable support verdict for one probed device. */
data class Verdict(
    val code: VerdictCode,
    val coreLoopAvailable: Boolean,
    val recommendedTransport: Transport,
    val headline: String,
    val reason: String,
)

private fun normalizePlatform(platform: String): String = platform.trim().lowercase()

/**
 * Return the support [Verdict] for a probed device.
 *
 * Pure function of [probe] — no I/O, no globals — so the whole decision table is
 * unit-testable. The branch order mirrors `capability.evaluate()` in the Python core.
 */
fun evaluate(probe: ProbeInput): Verdict {
    val platform = normalizePlatform(probe.platform)

    // iOS: walled off from the HID peripheral role at the OS level. Not viable as a
    // BT-HID controller regardless of anything else the device reports.
    if (platform == CapabilityConstants.PLATFORM_IOS) {
        return Verdict(
            code = VerdictCode.IOS_WALLED,
            coreLoopAvailable = false,
            recommendedTransport = Transport.NETWORK_COMPANION,
            headline = "iOS cannot be a Bluetooth-HID controller",
            reason = "iOS blocks the HID role at the OS level (HID-over-GATT UUID 0x1812 is " +
                "refused and Classic HID is not exposed to apps). Ship iOS as a receiver, " +
                "or as a network companion-controller to a helper app on the target — " +
                "never as a BT-HID peripheral.",
        )
    }

    if (platform != CapabilityConstants.PLATFORM_ANDROID) {
        return Verdict(
            code = VerdictCode.UNSUPPORTED_PLATFORM,
            coreLoopAvailable = false,
            recommendedTransport = Transport.NONE,
            headline = "Platform not supported as a controller",
            reason = "Only Android can act as a true Bluetooth-HID controller in this product. " +
                "Reported platform: '${probe.platform}'.",
        )
    }

    // --- Android ---
    val classicHidCapable = probe.osApiLevel >= CapabilityConstants.BLUETOOTH_HID_DEVICE_MIN_API
    val bleHogpCapable =
        probe.osApiLevel >= CapabilityConstants.BLE_PERIPHERAL_MIN_API && probe.blePeripheralSupported

    // Best case: OS new enough AND the OEM left the HID device role enabled AND the
    // probe confirmed registerApp() succeeded.
    if (classicHidCapable && probe.hidDeviceRoleAvailable) {
        return Verdict(
            code = VerdictCode.SUPPORTED_CLASSIC_HID,
            coreLoopAvailable = true,
            recommendedTransport = Transport.CLASSIC_HID,
            headline = "Ready — true Bluetooth-HID controller",
            reason = "Android API ${probe.osApiLevel} (>= ${CapabilityConstants.BLUETOOTH_HID_DEVICE_MIN_API}) " +
                "and the HID device role registered successfully, so this phone can be a " +
                "standard BT keyboard/mouse/gamepad to any receiver with no software " +
                "installed on the target.",
        )
    }

    // Classic HID path unusable (either OS too old, or the OEM disabled the role).
    // A BLE-HOGP fallback may still give a working — if narrower — core loop.
    if (bleHogpCapable) {
        val why = if (!classicHidCapable) {
            "Android API ${probe.osApiLevel} is below ${CapabilityConstants.BLUETOOTH_HID_DEVICE_MIN_API}, " +
                "so the Classic BluetoothHidDevice role is unavailable"
        } else {
            "the Classic HID device role did not register (OEM compile-flag gating), but"
        }
        return Verdict(
            code = VerdictCode.BLE_HOGP_FALLBACK,
            coreLoopAvailable = true,
            recommendedTransport = Transport.BLE_HOGP,
            headline = "Usable via BLE-HOGP fallback",
            reason = "$why BLE peripheral mode is supported, so a manual HID-over-GATT server " +
                "can drive receivers that accept a BLE HID device. Narrower coverage than " +
                "Classic HID; treat as a fallback.",
        )
    }

    // No Classic HID and no BLE peripheral fallback.
    if (!classicHidCapable) {
        return Verdict(
            code = VerdictCode.OS_TOO_OLD,
            coreLoopAvailable = false,
            recommendedTransport = Transport.NONE,
            headline = "Android version too old",
            reason = "Android API ${probe.osApiLevel} is below ${CapabilityConstants.BLUETOOTH_HID_DEVICE_MIN_API} " +
                "(Android 9), so BluetoothHidDevice does not exist, and BLE peripheral mode " +
                "is not available either — this phone cannot be a controller.",
        )
    }

    // API >= 28 but the OEM disabled the HID device role AND no BLE peripheral fallback.
    return Verdict(
        code = VerdictCode.OEM_DISABLED,
        coreLoopAvailable = false,
        recommendedTransport = Transport.NONE,
        headline = "Blocked by the manufacturer (OEM-disabled HID role)",
        reason = "Android API ${probe.osApiLevel} supports BluetoothHidDevice, but registerApp() " +
            "failed — this OEM shipped the phone with the HID device role compiled out — and " +
            "BLE peripheral mode is not supported, so no fallback exists. Many manufacturers " +
            "disable this profile; it is a known, device-specific limitation.",
    )
}
