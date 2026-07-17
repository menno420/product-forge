"""Capability-probe verdict engine — the phone-controller's first-run gate.

This is Slice 1 of the idea doc
(menno420/idea-engine : ideas/product-forge/bt-controller-plan-2026-07-17.md,
blob 970edbfc): *"a capability probe + support matrix as the very first slice, so an
OEM-disabled device never reaches a broken core loop."*

The verdict logic here is deliberately platform-agnostic and side-effect free: an
Android front-end performs the real probe (attempt `BluetoothHidDevice` registration
once, check BLE-peripheral capability) and feeds the *results* into `evaluate()`, which
returns a stable, testable verdict. Keeping the decision table out of the UI is what lets
it be unit-tested in CI with no Android hardware.

Grounding facts, all from the idea doc's sourced research pass:

* `BluetoothHidDevice` (the "be a HID keyboard/mouse/gamepad" device role) was introduced
  in **Android 9 / API level 28**. It is **Classic Bluetooth HID**, not BLE.
* The HID *device* role sits behind an **OEM compile-time flag**, so an API-28+ phone can
  still fail `registerApp()` — support MUST be probed at runtime, never assumed.
* A **BLE-HOGP** fallback (a manual GATT HID server, API 21+) can work on some phones where
  the Classic HID device role is disabled — but BLE **peripheral / advertiser** mode is
  itself a per-device hardware capability that must be checked at runtime.
* **iOS blocks the HID role at the OS level** (HID-over-GATT service UUID 0x1812 is
  refused; Classic HID is not exposed to apps at all). An iPhone can only be a *network*
  controller to a companion receiver app, or act as a *receiver* — never a BT-HID
  peripheral.
"""

from __future__ import annotations

from dataclasses import dataclass

# --- grounding constants (idea doc research pass) ---------------------------------
BLUETOOTH_HID_DEVICE_MIN_API = 28  # Android 9 (Pie) — BluetoothHidDevice introduced
BLE_PERIPHERAL_MIN_API = 21        # Android 5 (Lollipop) — BLE peripheral / GATT server

# --- transports ------------------------------------------------------------------
TRANSPORT_CLASSIC_HID = "classic_bt_hid"    # true BT-HID via BluetoothHidDevice
TRANSPORT_BLE_HOGP = "ble_hogp"             # manual GATT HID-over-GATT fallback
TRANSPORT_NETWORK_COMPANION = "network_companion"  # Wi-Fi/UDP to a receiver app (iOS path)
TRANSPORT_NONE = None

# --- verdict codes ---------------------------------------------------------------
SUPPORTED_CLASSIC_HID = "SUPPORTED_CLASSIC_HID"
BLE_HOGP_FALLBACK = "BLE_HOGP_FALLBACK"
OEM_DISABLED = "OEM_DISABLED"
OS_TOO_OLD = "OS_TOO_OLD"
IOS_WALLED = "IOS_WALLED"
UNSUPPORTED_PLATFORM = "UNSUPPORTED_PLATFORM"

PLATFORM_ANDROID = "android"
PLATFORM_IOS = "ios"


@dataclass(frozen=True)
class ProbeInput:
    """The runtime facts a device reports about itself.

    On Android the front-end fills these from a *real* probe:
      * ``os_api_level`` — ``Build.VERSION.SDK_INT``
      * ``hid_device_role_available`` — did a one-shot ``BluetoothHidDevice`` service bind +
        ``registerApp()`` succeed? (The OEM compile-flag gate shows up here.)
      * ``ble_peripheral_supported`` — ``BluetoothAdapter.isMultipleAdvertisementSupported()``
        / peripheral-mode advertising available.
    """

    platform: str
    os_api_level: int = 0
    hid_device_role_available: bool = False
    ble_peripheral_supported: bool = False

    def __post_init__(self) -> None:
        if not isinstance(self.os_api_level, int) or self.os_api_level < 0:
            raise ValueError("os_api_level must be a non-negative int")
        if not isinstance(self.platform, str) or not self.platform:
            raise ValueError("platform must be a non-empty string")


@dataclass(frozen=True)
class Verdict:
    """A stable, renderable support verdict for one probed device."""

    code: str
    core_loop_available: bool
    recommended_transport: object  # a TRANSPORT_* value or None
    headline: str
    reason: str

    def as_dict(self) -> dict:
        return {
            "code": self.code,
            "core_loop_available": self.core_loop_available,
            "recommended_transport": self.recommended_transport,
            "headline": self.headline,
            "reason": self.reason,
        }


def _normalize_platform(platform: str) -> str:
    return platform.strip().lower()


def evaluate(probe: ProbeInput) -> Verdict:
    """Return the support Verdict for a probed device.

    Pure function of ``probe`` — no I/O, no globals — so the whole decision table is
    unit-testable. The branch order mirrors the idea doc's viability findings.
    """
    platform = _normalize_platform(probe.platform)

    # iOS: walled off from the HID peripheral role at the OS level. Not viable as a
    # BT-HID controller regardless of anything else the device reports.
    if platform == PLATFORM_IOS:
        return Verdict(
            code=IOS_WALLED,
            core_loop_available=False,
            recommended_transport=TRANSPORT_NETWORK_COMPANION,
            headline="iOS cannot be a Bluetooth-HID controller",
            reason=(
                "iOS blocks the HID role at the OS level (HID-over-GATT UUID 0x1812 is "
                "refused and Classic HID is not exposed to apps). Ship iOS as a receiver, "
                "or as a network companion-controller to a helper app on the target — "
                "never as a BT-HID peripheral."
            ),
        )

    if platform != PLATFORM_ANDROID:
        return Verdict(
            code=UNSUPPORTED_PLATFORM,
            core_loop_available=False,
            recommended_transport=TRANSPORT_NONE,
            headline="Platform not supported as a controller",
            reason=(
                "Only Android can act as a true Bluetooth-HID controller in this product. "
                "Reported platform: %r." % probe.platform
            ),
        )

    # --- Android ---
    classic_hid_capable = probe.os_api_level >= BLUETOOTH_HID_DEVICE_MIN_API
    ble_hogp_capable = (
        probe.os_api_level >= BLE_PERIPHERAL_MIN_API and probe.ble_peripheral_supported
    )

    # Best case: OS new enough AND the OEM left the HID device role enabled AND the probe
    # confirmed registerApp() succeeded.
    if classic_hid_capable and probe.hid_device_role_available:
        return Verdict(
            code=SUPPORTED_CLASSIC_HID,
            core_loop_available=True,
            recommended_transport=TRANSPORT_CLASSIC_HID,
            headline="Ready — true Bluetooth-HID controller",
            reason=(
                "Android API %d (>= %d) and the HID device role registered successfully, "
                "so this phone can be a standard BT keyboard/mouse/gamepad to any receiver "
                "with no software installed on the target."
                % (probe.os_api_level, BLUETOOTH_HID_DEVICE_MIN_API)
            ),
        )

    # Classic HID path unusable (either OS too old, or the OEM disabled the role).
    # A BLE-HOGP fallback may still give a working — if narrower — core loop.
    if ble_hogp_capable:
        if not classic_hid_capable:
            why = (
                "Android API %d is below %d, so the Classic BluetoothHidDevice role is "
                "unavailable" % (probe.os_api_level, BLUETOOTH_HID_DEVICE_MIN_API)
            )
        else:
            why = (
                "the Classic HID device role did not register (OEM compile-flag gating), "
                "but"
            )
        return Verdict(
            code=BLE_HOGP_FALLBACK,
            core_loop_available=True,
            recommended_transport=TRANSPORT_BLE_HOGP,
            headline="Usable via BLE-HOGP fallback",
            reason=(
                "%s BLE peripheral mode is supported, so a manual HID-over-GATT server "
                "can drive receivers that accept a BLE HID device. Narrower coverage than "
                "Classic HID; treat as a fallback." % why
            ),
        )

    # No Classic HID and no BLE peripheral fallback.
    if not classic_hid_capable:
        return Verdict(
            code=OS_TOO_OLD,
            core_loop_available=False,
            recommended_transport=TRANSPORT_NONE,
            headline="Android version too old",
            reason=(
                "Android API %d is below %d (Android 9), so BluetoothHidDevice does not "
                "exist, and BLE peripheral mode is not available either — this phone "
                "cannot be a controller." % (probe.os_api_level, BLUETOOTH_HID_DEVICE_MIN_API)
            ),
        )

    # API >= 28 but the OEM disabled the HID device role AND no BLE peripheral fallback.
    return Verdict(
        code=OEM_DISABLED,
        core_loop_available=False,
        recommended_transport=TRANSPORT_NONE,
        headline="Blocked by the manufacturer (OEM-disabled HID role)",
        reason=(
            "Android API %d supports BluetoothHidDevice, but registerApp() failed — this "
            "OEM shipped the phone with the HID device role compiled out — and BLE "
            "peripheral mode is not supported, so no fallback exists. Many manufacturers "
            "disable this profile; it is a known, device-specific limitation."
            % probe.os_api_level
        ),
    )


# Representative scenarios used by the CLI demo (and asserted by the tests). Each is a
# named, realistic probe result drawn from the idea doc's device landscape.
DEMO_SCENARIOS = (
    (
        "Modern Pixel, HID role enabled",
        ProbeInput(PLATFORM_ANDROID, os_api_level=34, hid_device_role_available=True,
                   ble_peripheral_supported=True),
    ),
    (
        "Android 13 phone, OEM disabled HID role, BLE peripheral present",
        ProbeInput(PLATFORM_ANDROID, os_api_level=33, hid_device_role_available=False,
                   ble_peripheral_supported=True),
    ),
    (
        "Android 13 phone, OEM disabled HID role, no BLE peripheral",
        ProbeInput(PLATFORM_ANDROID, os_api_level=33, hid_device_role_available=False,
                   ble_peripheral_supported=False),
    ),
    (
        "Old Android 7 phone (API 24)",
        ProbeInput(PLATFORM_ANDROID, os_api_level=24, hid_device_role_available=False,
                   ble_peripheral_supported=False),
    ),
    (
        "iPhone",
        ProbeInput(PLATFORM_IOS, os_api_level=0),
    ),
)
