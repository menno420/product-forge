#!/usr/bin/env python3
"""Tests for the capability-probe verdict engine.

Covers every verdict branch and the load-bearing API boundary (27 vs 28), so a
regression that silently weakens the OEM/OS gating is caught.
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from src import capability as cap  # noqa: E402
from src.capability import ProbeInput, evaluate  # noqa: E402


class TestAndroidClassicHid(unittest.TestCase):
    def test_modern_phone_hid_enabled_is_supported(self):
        v = evaluate(ProbeInput("android", os_api_level=34,
                                hid_device_role_available=True,
                                ble_peripheral_supported=True))
        self.assertEqual(v.code, cap.SUPPORTED_CLASSIC_HID)
        self.assertTrue(v.core_loop_available)
        self.assertEqual(v.recommended_transport, cap.TRANSPORT_CLASSIC_HID)

    def test_exactly_api_28_with_hid_role_is_supported(self):
        # API 28 is the floor for BluetoothHidDevice — it must qualify.
        v = evaluate(ProbeInput("android", os_api_level=28,
                                hid_device_role_available=True,
                                ble_peripheral_supported=False))
        self.assertEqual(v.code, cap.SUPPORTED_CLASSIC_HID)

    def test_hid_role_flag_ignored_when_os_too_old(self):
        # Even if a device oddly reports the role available, API < 28 has no
        # BluetoothHidDevice class at all — must NOT be classed as classic-HID supported.
        v = evaluate(ProbeInput("android", os_api_level=27,
                                hid_device_role_available=True,
                                ble_peripheral_supported=False))
        self.assertNotEqual(v.code, cap.SUPPORTED_CLASSIC_HID)
        self.assertEqual(v.code, cap.OS_TOO_OLD)


class TestBleFallback(unittest.TestCase):
    def test_oem_disabled_but_ble_peripheral_gives_fallback(self):
        v = evaluate(ProbeInput("android", os_api_level=33,
                                hid_device_role_available=False,
                                ble_peripheral_supported=True))
        self.assertEqual(v.code, cap.BLE_HOGP_FALLBACK)
        self.assertTrue(v.core_loop_available)
        self.assertEqual(v.recommended_transport, cap.TRANSPORT_BLE_HOGP)

    def test_old_os_but_ble_peripheral_gives_fallback(self):
        # API 24 (< 28) has no Classic HID role, but BLE peripheral (>= 21) enables HOGP.
        v = evaluate(ProbeInput("android", os_api_level=24,
                                hid_device_role_available=False,
                                ble_peripheral_supported=True))
        self.assertEqual(v.code, cap.BLE_HOGP_FALLBACK)
        self.assertTrue(v.core_loop_available)

    def test_ble_below_min_api_is_not_a_fallback(self):
        # API 20 (< 21) cannot do BLE peripheral even if the flag is set.
        v = evaluate(ProbeInput("android", os_api_level=20,
                                hid_device_role_available=False,
                                ble_peripheral_supported=True))
        self.assertEqual(v.code, cap.OS_TOO_OLD)
        self.assertFalse(v.core_loop_available)


class TestUnsupported(unittest.TestCase):
    def test_oem_disabled_no_ble_is_oem_disabled(self):
        v = evaluate(ProbeInput("android", os_api_level=33,
                                hid_device_role_available=False,
                                ble_peripheral_supported=False))
        self.assertEqual(v.code, cap.OEM_DISABLED)
        self.assertFalse(v.core_loop_available)
        self.assertIsNone(v.recommended_transport)

    def test_old_os_no_ble_is_os_too_old(self):
        v = evaluate(ProbeInput("android", os_api_level=24,
                                hid_device_role_available=False,
                                ble_peripheral_supported=False))
        self.assertEqual(v.code, cap.OS_TOO_OLD)
        self.assertFalse(v.core_loop_available)

    def test_ios_is_walled_with_network_companion_recommendation(self):
        v = evaluate(ProbeInput("ios", os_api_level=0))
        self.assertEqual(v.code, cap.IOS_WALLED)
        self.assertFalse(v.core_loop_available)
        self.assertEqual(v.recommended_transport, cap.TRANSPORT_NETWORK_COMPANION)

    def test_unknown_platform_is_unsupported(self):
        v = evaluate(ProbeInput("windows-phone", os_api_level=10))
        self.assertEqual(v.code, cap.UNSUPPORTED_PLATFORM)
        self.assertFalse(v.core_loop_available)

    def test_platform_is_case_insensitive(self):
        v = evaluate(ProbeInput("Android", os_api_level=34,
                                hid_device_role_available=True,
                                ble_peripheral_supported=True))
        self.assertEqual(v.code, cap.SUPPORTED_CLASSIC_HID)
        self.assertEqual(evaluate(ProbeInput("iOS")).code, cap.IOS_WALLED)


class TestProbeValidation(unittest.TestCase):
    def test_negative_api_rejected(self):
        with self.assertRaises(ValueError):
            ProbeInput("android", os_api_level=-1)

    def test_empty_platform_rejected(self):
        with self.assertRaises(ValueError):
            ProbeInput("", os_api_level=30)


class TestDemoScenarios(unittest.TestCase):
    def test_every_demo_scenario_evaluates_to_a_known_verdict(self):
        known = {cap.SUPPORTED_CLASSIC_HID, cap.BLE_HOGP_FALLBACK, cap.OEM_DISABLED,
                 cap.OS_TOO_OLD, cap.IOS_WALLED, cap.UNSUPPORTED_PLATFORM}
        seen = set()
        for label, probe in cap.DEMO_SCENARIOS:
            v = evaluate(probe)
            self.assertIn(v.code, known, "scenario %r -> unknown verdict %s" % (label, v.code))
            seen.add(v.code)
        # The demo set is curated to exercise a spread of outcomes, not one.
        self.assertGreaterEqual(len(seen), 4)

    def test_verdict_as_dict_roundtrip(self):
        v = evaluate(ProbeInput("android", os_api_level=34,
                                hid_device_role_available=True,
                                ble_peripheral_supported=True))
        d = v.as_dict()
        self.assertEqual(d["code"], cap.SUPPORTED_CLASSIC_HID)
        self.assertTrue(d["core_loop_available"])


if __name__ == "__main__":
    unittest.main()
