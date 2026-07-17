#!/usr/bin/env python3
"""phone-controller — Slice 1 demo CLI (capability probe verdict + receiver matrix).

Run with no arguments to see the verdict for a set of representative device scenarios
plus the receiver-compatibility matrix. Pass an explicit probe to evaluate one device:

    python3 cli.py --platform android --api 34 --hid-role --ble-peripheral
    python3 cli.py --platform android --api 33 --no-hid-role --ble-peripheral
    python3 cli.py --platform ios

The real Android app fills the probe flags from a live device probe; this CLI lets you
exercise the exact same verdict logic from a terminal, with no Android hardware.
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from src import capability, receivers  # noqa: E402


def _print_verdict(label, probe):
    v = capability.evaluate(probe)
    loop = "core loop AVAILABLE" if v.core_loop_available else "core loop UNAVAILABLE"
    transport = v.recommended_transport or "none"
    print("  [%s] %s" % (v.code, label))
    print("      -> %s | %s | transport: %s" % (v.headline, loop, transport))
    print("      %s" % v.reason)
    print()


def _print_matrix():
    print("RECEIVER COMPATIBILITY MATRIX (std BT-HID device, no companion app)")
    print("-" * 78)
    print("  %-30s %-8s %-6s %-8s %s" % ("target", "keybrd", "mouse", "gamepad", "OOB"))
    print("  %-30s %-8s %-6s %-8s %s" % ("-" * 30, "-" * 8, "-" * 6, "-" * 8, "---"))
    for r in receivers.RECEIVERS:
        print("  %-30s %-8s %-6s %-8s %s" % (
            r.target, r.keyboard, r.mouse, r.gamepad, "yes" if r.out_of_box else "no"))
    print()
    print("  Out-of-box (no companion app) targets: %d of %d"
          % (len(receivers.out_of_box_targets()), len(receivers.RECEIVERS)))
    print()


def _build_parser():
    p = argparse.ArgumentParser(description="phone-controller capability verdict demo")
    p.add_argument("--platform", help="android | ios | <other>")
    p.add_argument("--api", type=int, default=0, help="Android API level (Build.VERSION.SDK_INT)")
    p.add_argument("--hid-role", dest="hid_role", action="store_true",
                   help="the HID device role (registerApp) succeeded")
    p.add_argument("--no-hid-role", dest="hid_role", action="store_false",
                   help="the HID device role did NOT register")
    p.add_argument("--ble-peripheral", dest="ble", action="store_true",
                   help="BLE peripheral / advertiser mode supported")
    p.add_argument("--no-ble-peripheral", dest="ble", action="store_false",
                   help="BLE peripheral mode not supported")
    p.set_defaults(hid_role=False, ble=False)
    return p


def main(argv=None):
    args = _build_parser().parse_args(argv)

    print("=" * 78)
    print("phone-controller — Slice 1: capability probe + receiver support matrix")
    print("=" * 78)
    print()

    if args.platform:
        probe = capability.ProbeInput(
            platform=args.platform,
            os_api_level=args.api,
            hid_device_role_available=args.hid_role,
            ble_peripheral_supported=args.ble,
        )
        print("PROBE VERDICT (explicit device):")
        _print_verdict("platform=%s api=%d hid_role=%s ble_peripheral=%s"
                       % (args.platform, args.api, args.hid_role, args.ble), probe)
    else:
        print("PROBE VERDICTS (representative device scenarios):")
        print()
        for label, probe in capability.DEMO_SCENARIOS:
            _print_verdict(label, probe)

    _print_matrix()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
