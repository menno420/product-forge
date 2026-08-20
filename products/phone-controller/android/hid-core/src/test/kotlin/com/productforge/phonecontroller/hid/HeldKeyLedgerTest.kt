/*
 * HeldKeyLedgerTest — pins the reference-counted hold semantics to the two
 * Codex findings on pf #52 that motivated the class, plus the bookkeeping
 * edges around them.
 */
package com.productforge.phonecontroller.hid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeldKeyLedgerTest {

    private class Probe {
        var releases = 0
        val release: () -> Unit = { releases++ }
    }

    @Test
    fun sharedIdentitySurvivesSiblingRelease() {
        // Codex finding 1: W and ↑ both bound to DPAD UP. Holding W, tapping ↑
        // must not release UP while W is still down.
        val ledger = HeldKeyLedger()
        val p = Probe()
        assertTrue(ledger.press(1, KEY_W, "a:DPAD:UP", p.release)) // first holder fires
        assertFalse(ledger.press(1, KEY_ARROW_UP, "a:DPAD:UP", p.release)) // second must NOT re-fire
        assertTrue(ledger.release(1, KEY_ARROW_UP)) // sibling up: tracked...
        assertEquals(0, p.releases) // ...but UP stays held (W still down)
        assertTrue(ledger.release(1, KEY_W))
        assertEquals(1, p.releases) // last holder releases exactly once
        assertEquals(0, ledger.heldCount())
    }

    @Test
    fun sameKeycodeFromTwoDevicesCountsAsTwoHolders() {
        // Two keyboards, both Enter (usage 0x28 = one identity): the first
        // device letting go must not release the key the second still holds.
        val ledger = HeldKeyLedger()
        val p = Probe()
        assertTrue(ledger.press(1, KEY_ENTER, "k:40", p.release))
        assertFalse(ledger.press(2, KEY_ENTER, "k:40", p.release))
        assertTrue(ledger.release(1, KEY_ENTER))
        assertEquals(0, p.releases)
        assertTrue(ledger.release(2, KEY_ENTER))
        assertEquals(1, p.releases)
    }

    @Test
    fun releaseDeviceFreesOnlyThatDevicesKeys() {
        // Codex finding 2: the keyboard that held a key detaches while another
        // keyboard stays attached — its holds must release NOW (its UPs are
        // gone forever), without disturbing the surviving device's holds.
        val ledger = HeldKeyLedger()
        val up = Probe()
        val fire = Probe()
        ledger.press(1, KEY_W, "a:DPAD:UP", up.release)
        ledger.press(2, KEY_ARROW_UP, "a:DPAD:UP", up.release) // second holder, same identity
        ledger.press(1, KEY_SPACE, "k:44", fire.release) // device 1 only
        ledger.releaseDevice(1)
        assertEquals(0, up.releases) // device 2 still holds the identity
        assertEquals(1, fire.releases) // device 1's exclusive hold released
        assertEquals(1, ledger.heldCount())
        assertTrue(ledger.release(2, KEY_ARROW_UP))
        assertEquals(1, up.releases) // last holder gone → identity releases
    }

    @Test
    fun releaseDeviceFiresSoleHolders() {
        val ledger = HeldKeyLedger()
        val p = Probe()
        ledger.press(7, KEY_W, "a:DPAD:UP", p.release)
        ledger.releaseDevice(7)
        assertEquals(1, p.releases)
        assertEquals(0, ledger.heldCount())
    }

    @Test
    fun releaseAllFiresEachIdentityOnceAndEmpties() {
        val ledger = HeldKeyLedger()
        val shared = Probe()
        val solo = Probe()
        val noop = Probe()
        ledger.press(1, KEY_W, "a:DPAD:UP", shared.release)
        ledger.press(1, KEY_ARROW_UP, "a:DPAD:UP", shared.release)
        ledger.press(1, KEY_SPACE, "k:44", solo.release)
        ledger.press(1, KEY_UNMAPPED, null, noop.release)
        ledger.releaseAll()
        assertEquals(1, shared.releases) // once, not per holder
        assertEquals(1, solo.releases)
        assertEquals(1, noop.releases) // uncounted entries all fire
        assertEquals(0, ledger.heldCount())
        // Idempotent: nothing left to fire.
        ledger.releaseAll()
        assertEquals(1, shared.releases)
    }

    @Test
    fun duplicateDownAndUntrackedReleaseAreInert() {
        val ledger = HeldKeyLedger()
        val p = Probe()
        assertTrue(ledger.press(1, KEY_W, "a:DPAD:UP", p.release))
        assertFalse(ledger.press(1, KEY_W, "a:DPAD:UP", p.release)) // double down: no re-fire
        assertEquals(1, ledger.heldCount()) // and no double entry
        assertFalse(ledger.release(1, KEY_ENTER)) // never pressed: untracked
        assertEquals(0, p.releases)
    }

    @Test
    fun uncountedEntriesFireAndReleaseIndependently() {
        // identity == null (swallowed no-ops): every press fires, every up
        // releases its own entry — no sharing.
        val ledger = HeldKeyLedger()
        val a = Probe()
        val b = Probe()
        assertTrue(ledger.press(1, KEY_UNMAPPED, null, a.release))
        assertTrue(ledger.press(1, KEY_UNMAPPED_2, null, b.release))
        assertTrue(ledger.release(1, KEY_UNMAPPED))
        assertEquals(1, a.releases)
        assertEquals(0, b.releases)
    }

    @Test
    fun isPressedTracksPerDeviceKeys() {
        val ledger = HeldKeyLedger()
        ledger.press(1, KEY_W, "a:DPAD:UP") { }
        assertTrue(ledger.isPressed(1, KEY_W))
        assertFalse(ledger.isPressed(2, KEY_W)) // other device's W is distinct
        assertFalse(ledger.isPressed(1, KEY_ENTER))
        ledger.release(1, KEY_W)
        assertFalse(ledger.isPressed(1, KEY_W))
    }

    private companion object {
        // Arbitrary Android keycode stand-ins (values irrelevant to the ledger).
        const val KEY_W = 51
        const val KEY_ARROW_UP = 19
        const val KEY_ENTER = 66
        const val KEY_SPACE = 62
        const val KEY_UNMAPPED = 77
        const val KEY_UNMAPPED_2 = 78
    }
}
