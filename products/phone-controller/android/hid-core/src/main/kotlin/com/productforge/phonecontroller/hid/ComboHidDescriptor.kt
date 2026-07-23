/*
 * Combo HID report descriptor + button/usage constants for the phone-as-controller.
 *
 * Slice 4 grows the Slice-2 media-remote-only device into a THREE-REPORT combo device
 * — the shape emulator use actually needs (owner directive 2026-07-23: the phone must
 * work as an input device for other Android devices, e.g. driving emulators):
 *
 *   Report 1 — Consumer Control "media remote" (byte-for-byte the Slice-2 report, so
 *              existing receivers keep working);
 *   Report 2 — standard keyboard (8-byte boot-style payload: modifiers + reserved +
 *              6-key rollover array). Every receiver in the Slice-1 matrix that
 *              accepts a BT keyboard consumes this, and every emulator can map keys;
 *   Report 3 — gamepad: 16 buttons + a hat-switch D-pad + centered X/Y axes. Android
 *              receivers map this through the kernel HID-gamepad convention to
 *              KEYCODE_BUTTON_A…START / AXIS_HAT_X/Y, which is what emulator
 *              controller auto-detection listens for.
 *
 * Bit positions in [GamepadButton] follow the Linux-kernel convention (application
 * collection = Gamepad → buttons map from BTN_SOUTH upward): bit0=A(south),
 * bit1=B(east), bit2=C(unused), bit3=X(north), bit4=Y(west), bit5=Z(unused),
 * bit6=L1, bit7=R1, bit8=L2, bit9=R2, bit10=Select, bit11=Start. Android's
 * Generic.kl then yields KEYCODE_BUTTON_A/B/X/Y/L1/R1/SELECT/START on the receiving
 * device with no custom keylayout. If a picky host rejects the combo descriptor,
 * bisect by dropping Report 3 first (see the session card's guard recipe).
 *
 * Pure JVM on purpose — the descriptor bytes and report bit-math are unit-tested in
 * CI's SDK-free lane (ComboHidDescriptorTest); the Android transport only wraps them.
 */
package com.productforge.phonecontroller.hid

/** The combo device's report IDs and payload shapes. */
object ComboHidDescriptor {

    /** Report ID 1 — Consumer Control media remote (Slice-2 layout, unchanged). */
    const val REPORT_ID_CONSUMER: Int = 1

    /** Report ID 2 — keyboard (8-byte boot-style payload). */
    const val REPORT_ID_KEYBOARD: Int = 2

    /** Report ID 3 — gamepad (buttons + hat + X/Y payload). */
    const val REPORT_ID_GAMEPAD: Int = 3

    /** Payload length per report (Report ID byte excluded — sendReport takes it apart). */
    const val CONSUMER_REPORT_BYTES: Int = 1
    const val KEYBOARD_REPORT_BYTES: Int = 8
    const val GAMEPAD_REPORT_BYTES: Int = 5

    /**
     * The combo HID report descriptor: three application collections, one per report.
     *
     * Consumer collection = the Slice-2 media-remote bytes verbatim (usages CD/B5/B6/
     * E2/E9/EA/B7 as 1-bit inputs + 1-bit pad). Keyboard = modifiers (E0–E7) + reserved
     * byte + 6-slot key array (usages 0x00–0x65). Gamepad = 16 buttons, a 4-bit hat
     * switch (logical 1–8, null state = 0, physical 0–315°) + 4-bit pad, then X/Y as
     * signed bytes (-127..127; physical/unit reset after the hat so they don't inherit
     * its degree scaling).
     */
    val DESCRIPTOR: ByteArray = byteArrayOf(
        // ---- Report 1: Consumer Control media remote (Slice-2 bytes, unchanged) ----
        0x05.toByte(), 0x0C.toByte(),             //   Usage Page (Consumer)
        0x09.toByte(), 0x01.toByte(),             //   Usage (Consumer Control)
        0xA1.toByte(), 0x01.toByte(),             //   Collection (Application)
        0x85.toByte(), REPORT_ID_CONSUMER.toByte(), //   Report ID (1)
        0x15.toByte(), 0x00.toByte(),             //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),             //     Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),             //     Report Size (1)
        0x95.toByte(), 0x07.toByte(),             //     Report Count (7)
        0x09.toByte(), 0xCD.toByte(),             //     Usage (Play/Pause)
        0x09.toByte(), 0xB5.toByte(),             //     Usage (Scan Next Track)
        0x09.toByte(), 0xB6.toByte(),             //     Usage (Scan Previous Track)
        0x09.toByte(), 0xE2.toByte(),             //     Usage (Mute)
        0x09.toByte(), 0xE9.toByte(),             //     Usage (Volume Increment)
        0x09.toByte(), 0xEA.toByte(),             //     Usage (Volume Decrement)
        0x09.toByte(), 0xB7.toByte(),             //     Usage (Stop)
        0x81.toByte(), 0x02.toByte(),             //     Input (Data,Var,Abs)
        0x95.toByte(), 0x01.toByte(),             //     Report Count (1)
        0x81.toByte(), 0x01.toByte(),             //     Input (Const) — 1-bit pad
        0xC0.toByte(),                            //   End Collection

        // ---- Report 2: keyboard (boot-style 8-byte payload) ----
        0x05.toByte(), 0x01.toByte(),             //   Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),             //   Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),             //   Collection (Application)
        0x85.toByte(), REPORT_ID_KEYBOARD.toByte(), //   Report ID (2)
        0x05.toByte(), 0x07.toByte(),             //     Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(),             //     Usage Minimum (LeftControl)
        0x29.toByte(), 0xE7.toByte(),             //     Usage Maximum (Right GUI)
        0x15.toByte(), 0x00.toByte(),             //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),             //     Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),             //     Report Size (1)
        0x95.toByte(), 0x08.toByte(),             //     Report Count (8)
        0x81.toByte(), 0x02.toByte(),             //     Input (Data,Var,Abs) — modifiers
        0x75.toByte(), 0x08.toByte(),             //     Report Size (8)
        0x95.toByte(), 0x01.toByte(),             //     Report Count (1)
        0x81.toByte(), 0x01.toByte(),             //     Input (Const) — reserved byte
        0x05.toByte(), 0x07.toByte(),             //     Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0x00.toByte(),             //     Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(),             //     Usage Maximum (101)
        0x15.toByte(), 0x00.toByte(),             //     Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(),             //     Logical Maximum (101)
        0x75.toByte(), 0x08.toByte(),             //     Report Size (8)
        0x95.toByte(), 0x06.toByte(),             //     Report Count (6)
        0x81.toByte(), 0x00.toByte(),             //     Input (Data,Array) — 6-key rollover
        0xC0.toByte(),                            //   End Collection

        // ---- Report 3: gamepad (16 buttons + hat D-pad + X/Y) ----
        0x05.toByte(), 0x01.toByte(),             //   Usage Page (Generic Desktop)
        0x09.toByte(), 0x05.toByte(),             //   Usage (Gamepad)
        0xA1.toByte(), 0x01.toByte(),             //   Collection (Application)
        0x85.toByte(), REPORT_ID_GAMEPAD.toByte(), //   Report ID (3)
        0x05.toByte(), 0x09.toByte(),             //     Usage Page (Button)
        0x19.toByte(), 0x01.toByte(),             //     Usage Minimum (Button 1)
        0x29.toByte(), 0x10.toByte(),             //     Usage Maximum (Button 16)
        0x15.toByte(), 0x00.toByte(),             //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),             //     Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),             //     Report Size (1)
        0x95.toByte(), 0x10.toByte(),             //     Report Count (16)
        0x81.toByte(), 0x02.toByte(),             //     Input (Data,Var,Abs) — 2 button bytes
        0x05.toByte(), 0x01.toByte(),             //     Usage Page (Generic Desktop)
        0x09.toByte(), 0x39.toByte(),             //     Usage (Hat switch)
        0x15.toByte(), 0x01.toByte(),             //     Logical Minimum (1)
        0x25.toByte(), 0x08.toByte(),             //     Logical Maximum (8)
        0x35.toByte(), 0x00.toByte(),             //     Physical Minimum (0)
        0x46.toByte(), 0x3B.toByte(), 0x01.toByte(), //  Physical Maximum (315)
        0x65.toByte(), 0x14.toByte(),             //     Unit (degrees)
        0x75.toByte(), 0x04.toByte(),             //     Report Size (4)
        0x95.toByte(), 0x01.toByte(),             //     Report Count (1)
        0x81.toByte(), 0x42.toByte(),             //     Input (Data,Var,Abs,Null) — hat
        0x75.toByte(), 0x04.toByte(),             //     Report Size (4)
        0x95.toByte(), 0x01.toByte(),             //     Report Count (1)
        0x81.toByte(), 0x01.toByte(),             //     Input (Const) — 4-bit pad
        0x65.toByte(), 0x00.toByte(),             //     Unit (none) — reset after hat
        0x45.toByte(), 0x00.toByte(),             //     Physical Maximum (0) — reset
        0x09.toByte(), 0x30.toByte(),             //     Usage (X)
        0x09.toByte(), 0x31.toByte(),             //     Usage (Y)
        0x15.toByte(), 0x81.toByte(),             //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),             //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),             //     Report Size (8)
        0x95.toByte(), 0x02.toByte(),             //     Report Count (2)
        0x81.toByte(), 0x02.toByte(),             //     Input (Data,Var,Abs) — X, Y
        0xC0.toByte(),                            //   End Collection
    )
}

/**
 * The seven media buttons (Report 1) — Slice-2 semantics unchanged: a "press" report
 * sets the button's bit; the matching "release" report is all-zero.
 */
enum class MediaButton(val bit: Int) {
    PLAY_PAUSE(0),
    NEXT(1),
    PREVIOUS(2),
    MUTE(3),
    VOLUME_UP(4),
    VOLUME_DOWN(5),
    STOP(6);

    /** The single-byte input-report payload for this button held down. */
    fun pressReport(): ByteArray = byteArrayOf((1 shl bit).toByte())

    companion object {
        /** The all-zero payload: nothing pressed (a release). */
        val RELEASE_REPORT: ByteArray = byteArrayOf(0x00)
    }
}

/**
 * Gamepad buttons (Report 3) with their input-report bit positions.
 *
 * Positions follow the Linux-kernel HID-gamepad convention so the receiving Android
 * maps them to the matching KEYCODE_BUTTON_* out of the box (bits 2 and 5 — the
 * kernel's BTN_C / BTN_Z — are deliberately skipped; wiring A/B/X/Y through them
 * would shift every label by one on the receiver).
 */
enum class GamepadButton(val bit: Int) {
    A(0),        // BTN_SOUTH  -> KEYCODE_BUTTON_A
    B(1),        // BTN_EAST   -> KEYCODE_BUTTON_B
    X(3),        // BTN_NORTH  -> KEYCODE_BUTTON_X
    Y(4),        // BTN_WEST   -> KEYCODE_BUTTON_Y
    L1(6),       // BTN_TL     -> KEYCODE_BUTTON_L1
    R1(7),       // BTN_TR     -> KEYCODE_BUTTON_R1
    L2(8),       // BTN_TL2    -> KEYCODE_BUTTON_L2
    R2(9),       // BTN_TR2    -> KEYCODE_BUTTON_R2
    SELECT(10),  // BTN_SELECT -> KEYCODE_BUTTON_SELECT
    START(11),   // BTN_START  -> KEYCODE_BUTTON_START
}

/** The four D-pad directions (combined into the hat-switch value by [GamepadState]). */
enum class DpadDirection { UP, DOWN, LEFT, RIGHT }

/**
 * HID Keyboard/Keypad usage IDs (Report 2) for the keys the built-in layouts send,
 * plus the modifier masks of the report's first byte. Letter usages run a=0x04..z=0x1D.
 */
object KeyUsage {
    const val A = 0x04
    const val S = 0x16
    const val X = 0x1B
    const val Z = 0x1D
    const val ENTER = 0x28
    const val ESCAPE = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val ARROW_RIGHT = 0x4F
    const val ARROW_LEFT = 0x50
    const val ARROW_DOWN = 0x51
    const val ARROW_UP = 0x52

    /** Modifier bit masks (report byte 0). */
    const val MOD_LEFT_CTRL = 0x01
    const val MOD_LEFT_SHIFT = 0x02
    const val MOD_LEFT_ALT = 0x04
}
