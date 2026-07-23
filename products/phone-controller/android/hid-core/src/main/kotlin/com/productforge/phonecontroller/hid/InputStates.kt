/*
 * Stateful input-report builders for the combo device's keyboard and gamepad reports.
 *
 * The transport owns one instance of each; the UI calls press/release mutators and
 * the transport ships `report()` on every change. Pure JVM, no synchronization —
 * the transport is the single writer (it serializes UI-thread calls itself).
 */
package com.productforge.phonecontroller.hid

/**
 * Keyboard state → the 8-byte Report-2 payload: [modifiers, reserved, k1..k6].
 *
 * Standard 6-key rollover: at most six simultaneous non-modifier keys, reported in
 * press order. A seventh concurrent press is refused (returns false) rather than
 * silently dropping an older key — the UI's layouts never need more than six.
 */
class KeyboardState {

    private val pressed = LinkedHashSet<Int>()
    private var modifiers = 0

    /** Press a key usage (0x04..0x65). False if already down or 6 keys are held. */
    fun keyDown(usage: Int): Boolean {
        require(usage in 0x01..0x65) { "usage out of keyboard range: $usage" }
        if (usage in pressed) return false
        if (pressed.size >= MAX_KEYS) return false
        pressed.add(usage)
        return true
    }

    /** Release a key usage. False if it was not down. */
    fun keyUp(usage: Int): Boolean = pressed.remove(usage)

    /** Set or clear a modifier mask bit (see [KeyUsage.MOD_LEFT_SHIFT] etc.). */
    fun setModifier(mask: Int, down: Boolean) {
        modifiers = if (down) modifiers or mask else modifiers and mask.inv()
    }

    /** Release everything (host disconnect / transport stop — no stuck keys). */
    fun clear() {
        pressed.clear()
        modifiers = 0
    }

    /** True when no key and no modifier is held. */
    fun isNeutral(): Boolean = pressed.isEmpty() && modifiers == 0

    /** The 8-byte Report-2 payload for the current state. */
    fun report(): ByteArray {
        val out = ByteArray(ComboHidDescriptor.KEYBOARD_REPORT_BYTES)
        out[0] = modifiers.toByte()
        // out[1] reserved = 0
        pressed.take(MAX_KEYS).forEachIndexed { i, usage -> out[2 + i] = usage.toByte() }
        return out
    }

    companion object {
        const val MAX_KEYS = 6
    }
}

/**
 * Gamepad state → the 5-byte Report-3 payload: [buttonsLo, buttonsHi, hat, x, y].
 *
 * The D-pad is tracked as four held directions and folded into the hat-switch value
 * (1=N clockwise to 8=NW; 0 = the descriptor's null state = released). Opposite
 * directions cancel — up+down or left+right read as neutral on that axis, so a
 * finger-roll across the pad can never report an impossible combination. X/Y stay
 * centered (0) until an analog-stick UI slice sets them.
 */
class GamepadState {

    private var buttons = 0
    private val dpadHeld = HashSet<DpadDirection>()
    private var x: Int = 0
    private var y: Int = 0

    fun buttonDown(button: GamepadButton) {
        buttons = buttons or (1 shl button.bit)
    }

    fun buttonUp(button: GamepadButton) {
        buttons = buttons and (1 shl button.bit).inv()
    }

    fun dpad(direction: DpadDirection, held: Boolean) {
        if (held) dpadHeld.add(direction) else dpadHeld.remove(direction)
    }

    /** Set the stick axes, each clamped to -127..127 (future analog slice). */
    fun setAxes(newX: Int, newY: Int) {
        x = newX.coerceIn(-127, 127)
        y = newY.coerceIn(-127, 127)
    }

    /** Release everything (host disconnect / transport stop — no stuck inputs). */
    fun clear() {
        buttons = 0
        dpadHeld.clear()
        x = 0
        y = 0
    }

    /** True when nothing is held and the axes are centered. */
    fun isNeutral(): Boolean = buttons == 0 && dpadHeld.isEmpty() && x == 0 && y == 0

    /** The current hat-switch value: 0 (released) or 1..8 (N, NE, E, SE, S, SW, W, NW). */
    fun hatValue(): Int {
        val v = (if (DpadDirection.UP in dpadHeld) 1 else 0) -
            (if (DpadDirection.DOWN in dpadHeld) 1 else 0)
        val h = (if (DpadDirection.RIGHT in dpadHeld) 1 else 0) -
            (if (DpadDirection.LEFT in dpadHeld) 1 else 0)
        return when {
            v > 0 && h == 0 -> 1  // N
            v > 0 && h > 0 -> 2   // NE
            v == 0 && h > 0 -> 3  // E
            v < 0 && h > 0 -> 4   // SE
            v < 0 && h == 0 -> 5  // S
            v < 0 && h < 0 -> 6   // SW
            v == 0 && h < 0 -> 7  // W
            v > 0 && h < 0 -> 8   // NW
            else -> 0             // neutral / cancelled out
        }
    }

    /** The 5-byte Report-3 payload for the current state. */
    fun report(): ByteArray = byteArrayOf(
        (buttons and 0xFF).toByte(),
        ((buttons shr 8) and 0xFF).toByte(),
        hatValue().toByte(),
        x.toByte(),
        y.toByte(),
    )
}
