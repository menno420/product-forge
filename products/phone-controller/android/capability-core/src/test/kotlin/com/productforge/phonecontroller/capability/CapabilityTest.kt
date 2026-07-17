/*
 * Unit tests for the Kotlin capability port. These assert the SAME decision table the
 * Python core's `tests/test_capability.py` asserts, so the two implementations are
 * proven to stay in lockstep. Pure JVM — no Android SDK, no hardware — so they run in
 * the lightweight Gradle CI lane.
 */
package com.productforge.phonecontroller.capability

import com.productforge.phonecontroller.capability.CapabilityConstants.PLATFORM_ANDROID
import com.productforge.phonecontroller.capability.CapabilityConstants.PLATFORM_IOS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityTest {

    // --- Android happy path -------------------------------------------------------

    @Test
    fun modernAndroidWithHidRoleIsSupportedClassicHid() {
        val v = evaluate(
            ProbeInput(PLATFORM_ANDROID, osApiLevel = 34, hidDeviceRoleAvailable = true, blePeripheralSupported = true),
        )
        assertEquals(VerdictCode.SUPPORTED_CLASSIC_HID, v.code)
        assertTrue(v.coreLoopAvailable)
        assertEquals(Transport.CLASSIC_HID, v.recommendedTransport)
    }

    @Test
    fun minimumApiWithHidRoleIsSupported() {
        // Exactly API 28 (the boundary) with the role available is still classic HID.
        val v = evaluate(ProbeInput(PLATFORM_ANDROID, osApiLevel = 28, hidDeviceRoleAvailable = true))
        assertEquals(VerdictCode.SUPPORTED_CLASSIC_HID, v.code)
        assertTrue(v.coreLoopAvailable)
    }

    // --- BLE-HOGP fallback --------------------------------------------------------

    @Test
    fun oemDisabledHidButBleFallbackYieldsBleHogp() {
        val v = evaluate(
            ProbeInput(PLATFORM_ANDROID, osApiLevel = 33, hidDeviceRoleAvailable = false, blePeripheralSupported = true),
        )
        assertEquals(VerdictCode.BLE_HOGP_FALLBACK, v.code)
        assertTrue(v.coreLoopAvailable)
        assertEquals(Transport.BLE_HOGP, v.recommendedTransport)
    }

    @Test
    fun oldOsButBlePeripheralPresentFallsBackToBleHogp() {
        // API 21..27: no classic HID role, but BLE peripheral present -> fallback works.
        val v = evaluate(
            ProbeInput(PLATFORM_ANDROID, osApiLevel = 25, hidDeviceRoleAvailable = false, blePeripheralSupported = true),
        )
        assertEquals(VerdictCode.BLE_HOGP_FALLBACK, v.code)
        assertTrue(v.coreLoopAvailable)
    }

    // --- Dead ends ----------------------------------------------------------------

    @Test
    fun oemDisabledHidNoBleIsOemDisabled() {
        val v = evaluate(
            ProbeInput(PLATFORM_ANDROID, osApiLevel = 33, hidDeviceRoleAvailable = false, blePeripheralSupported = false),
        )
        assertEquals(VerdictCode.OEM_DISABLED, v.code)
        assertFalse(v.coreLoopAvailable)
        assertEquals(Transport.NONE, v.recommendedTransport)
    }

    @Test
    fun oldAndroidNoBleIsOsTooOld() {
        val v = evaluate(
            ProbeInput(PLATFORM_ANDROID, osApiLevel = 24, hidDeviceRoleAvailable = false, blePeripheralSupported = false),
        )
        assertEquals(VerdictCode.OS_TOO_OLD, v.code)
        assertFalse(v.coreLoopAvailable)
        assertEquals(Transport.NONE, v.recommendedTransport)
    }

    // --- Platform gates -----------------------------------------------------------

    @Test
    fun iosIsAlwaysWalled() {
        val v = evaluate(ProbeInput(PLATFORM_IOS, osApiLevel = 0))
        assertEquals(VerdictCode.IOS_WALLED, v.code)
        assertFalse(v.coreLoopAvailable)
        assertEquals(Transport.NETWORK_COMPANION, v.recommendedTransport)
    }

    @Test
    fun iosStaysWalledEvenWithGenerousProbe() {
        // No amount of reported capability unlocks iOS — the OS wall dominates.
        val v = evaluate(
            ProbeInput(PLATFORM_IOS, osApiLevel = 99, hidDeviceRoleAvailable = true, blePeripheralSupported = true),
        )
        assertEquals(VerdictCode.IOS_WALLED, v.code)
    }

    @Test
    fun unknownPlatformIsUnsupported() {
        val v = evaluate(ProbeInput("windows-phone", osApiLevel = 10))
        assertEquals(VerdictCode.UNSUPPORTED_PLATFORM, v.code)
        assertFalse(v.coreLoopAvailable)
        assertEquals(Transport.NONE, v.recommendedTransport)
    }

    @Test
    fun platformIsCaseAndWhitespaceInsensitive() {
        val v = evaluate(ProbeInput("  Android  ", osApiLevel = 34, hidDeviceRoleAvailable = true))
        assertEquals(VerdictCode.SUPPORTED_CLASSIC_HID, v.code)
    }

    // --- ProbeInput validation ----------------------------------------------------

    @Test
    fun negativeApiLevelIsRejected() {
        assertFailsWith<IllegalArgumentException> { ProbeInput(PLATFORM_ANDROID, osApiLevel = -1) }
    }

    @Test
    fun emptyPlatformIsRejected() {
        assertFailsWith<IllegalArgumentException> { ProbeInput("", osApiLevel = 28) }
    }

    // --- Lockstep with the Python core's DEMO_SCENARIOS ---------------------------

    @Test
    fun demoScenariosMatchPythonCore() {
        // Same five named scenarios as capability.py's DEMO_SCENARIOS, same verdicts.
        assertEquals(
            VerdictCode.SUPPORTED_CLASSIC_HID,
            evaluate(ProbeInput(PLATFORM_ANDROID, 34, hidDeviceRoleAvailable = true, blePeripheralSupported = true)).code,
        )
        assertEquals(
            VerdictCode.BLE_HOGP_FALLBACK,
            evaluate(ProbeInput(PLATFORM_ANDROID, 33, hidDeviceRoleAvailable = false, blePeripheralSupported = true)).code,
        )
        assertEquals(
            VerdictCode.OEM_DISABLED,
            evaluate(ProbeInput(PLATFORM_ANDROID, 33, hidDeviceRoleAvailable = false, blePeripheralSupported = false)).code,
        )
        assertEquals(
            VerdictCode.OS_TOO_OLD,
            evaluate(ProbeInput(PLATFORM_ANDROID, 24, hidDeviceRoleAvailable = false, blePeripheralSupported = false)).code,
        )
        assertEquals(
            VerdictCode.IOS_WALLED,
            evaluate(ProbeInput(PLATFORM_IOS, 0)).code,
        )
    }
}
