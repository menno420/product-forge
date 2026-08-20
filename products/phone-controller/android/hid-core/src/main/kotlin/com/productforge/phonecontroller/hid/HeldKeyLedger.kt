/*
 * HeldKeyLedger — reference-counted bookkeeping for captured physical keys
 * (Slice 21; built for the two Codex findings on pf #52).
 *
 * The capture engine holds one entry per physical key DOWN, keyed by
 * (deviceId, keyCode) — two keyboards can hold the same keycode at once. Keys
 * whose actions share one underlying HID state (both W and ↑ bound to DPAD UP;
 * two keyboards' Enter keys → usage 0x28) share an action IDENTITY, and the
 * identity is what reference-counts: the action fires on the FIRST holder's
 * down and releases on the LAST holder's up — never mid-hold because a sibling
 * key let go (finding 1), and never left stuck because the device that held it
 * detached (finding 2 — releaseDevice()).
 *
 * Pure JVM on purpose: this is exactly the logic a wrong bit of which means a
 * stuck or dropped input on the host, so it unit-tests in the SDK-free lane.
 * The engine supplies actions as a stored release runnable; firing the press
 * is the caller's job when press() says so.
 */
package com.productforge.phonecontroller.hid

class HeldKeyLedger {

    private class Held(val deviceId: Int, val identity: String?, val release: () -> Unit)

    private val pressed = HashMap<Long, Held>()
    private val counts = HashMap<String, Int>()

    /** True while (deviceId, keyCode) is tracked as down. */
    fun isPressed(deviceId: Int, keyCode: Int): Boolean =
        composite(deviceId, keyCode) in pressed

    /**
     * Track a captured DOWN. Returns true when the caller should FIRE the
     * action's press — i.e. this is the identity's first holder (or the entry
     * is uncounted: identity == null, the swallowed-no-op case). A duplicate
     * down for a key already tracked returns false and changes nothing.
     */
    fun press(deviceId: Int, keyCode: Int, identity: String?, release: () -> Unit): Boolean {
        val key = composite(deviceId, keyCode)
        if (key in pressed) return false
        pressed[key] = Held(deviceId, identity, release)
        if (identity == null) return true
        val n = counts[identity] ?: 0
        counts[identity] = n + 1
        return n == 0
    }

    /**
     * Track an UP. Runs the stored release only when this key was the
     * identity's LAST holder. Returns true when the key was tracked at all
     * (the caller swallows the event either way).
     */
    fun release(deviceId: Int, keyCode: Int): Boolean {
        val held = pressed.remove(composite(deviceId, keyCode)) ?: return false
        releaseCounted(held)
        return true
    }

    /**
     * Release every key a detached device was holding (its UPs can never
     * arrive). Reference counts stay correct: an identity another device still
     * holds is decremented, not fired.
     */
    fun releaseDevice(deviceId: Int) {
        val keys = pressed.filterValues { it.deviceId == deviceId }.keys.toList()
        keys.forEach { key -> pressed.remove(key)?.let { releaseCounted(it) } }
    }

    /** Release everything (mode change, disconnect, pause, focus loss). */
    fun releaseAll() {
        val all = pressed.values.toList()
        pressed.clear()
        counts.clear()
        // Each distinct identity fires exactly once; uncounted entries all fire.
        val fired = HashSet<String>()
        all.forEach { held ->
            val id = held.identity
            if (id == null) {
                held.release()
            } else if (fired.add(id)) {
                held.release()
            }
        }
    }

    /** Number of keys currently tracked as down (test + diagnostics surface). */
    fun heldCount(): Int = pressed.size

    private fun releaseCounted(held: Held) {
        val id = held.identity
        if (id == null) {
            held.release()
            return
        }
        val n = counts[id] ?: 0
        if (n <= 1) {
            counts.remove(id)
            held.release()
        } else {
            counts[id] = n - 1
        }
    }

    private fun composite(deviceId: Int, keyCode: Int): Long =
        (deviceId.toLong() shl 32) or (keyCode.toLong() and 0xFFFFFFFFL)
}
