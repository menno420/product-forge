/*
 * Tests for the combo descriptor invariants and the report builders.
 *
 * These pin the wire format: report IDs, payload lengths, the Slice-2 consumer
 * collection surviving byte-for-byte inside the combo descriptor, kernel-convention
 * gamepad bit positions, keyboard 6-key rollover, and the D-pad→hat combine table
 * (including opposite-direction cancellation). A regression in any of these is a
 * dead or mislabeled button on the receiving device.
 */
package com.productforge.phonecontroller.hid

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComboHidDescriptorTest {

    // --- descriptor invariants -----------------------------------------------------

    @Test
    fun `descriptor declares exactly the three report ids`() {
        val d = ComboHidDescriptor.DESCRIPTOR
        val reportIds = mutableListOf<Int>()
        var i = 0
        while (i < d.size - 1) {
            // 0x85 = Report ID (global item, 1-byte payload in this descriptor).
            if (d[i] == 0x85.toByte()) {
                reportIds.add(d[i + 1].toInt())
                i += 2
            } else {
                i += 1
            }
        }
        assertEquals(
            listOf(
                ComboHidDescriptor.REPORT_ID_CONSUMER,
                ComboHidDescriptor.REPORT_ID_KEYBOARD,
                ComboHidDescriptor.REPORT_ID_GAMEPAD,
            ),
            reportIds,
        )
    }

    @Test
    fun `descriptor opens and closes three application collections`() {
        val d = ComboHidDescriptor.DESCRIPTOR
        var opens = 0
        var closes = 0
        var i = 0
        while (i < d.size) {
            when (d[i]) {
                0xA1.toByte() -> { opens += 1; i += 2 }   // Collection (+1 data byte)
                0xC0.toByte() -> { closes += 1; i += 1 }  // End Collection (no data)
                // Skip other items conservatively: low 2 bits encode the data size.
                else -> i += 1 + (d[i].toInt() and 0x03)
            }
        }
        assertEquals(3, opens)
        assertEquals(3, closes)
    }

    @Test
    fun `slice-2 consumer collection is embedded byte-for-byte`() {
        // The Slice-2 media-remote descriptor (PR #28) — receivers that adopted the
        // media remote must see the identical collection inside the combo device.
        val slice2 = byteArrayOf(
            0x05.toByte(), 0x0C.toByte(), 0x09.toByte(), 0x01.toByte(),
            0xA1.toByte(), 0x01.toByte(), 0x85.toByte(), 0x01.toByte(),
            0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x01.toByte(), 0x95.toByte(), 0x07.toByte(),
            0x09.toByte(), 0xCD.toByte(), 0x09.toByte(), 0xB5.toByte(),
            0x09.toByte(), 0xB6.toByte(), 0x09.toByte(), 0xE2.toByte(),
            0x09.toByte(), 0xE9.toByte(), 0x09.toByte(), 0xEA.toByte(),
            0x09.toByte(), 0xB7.toByte(), 0x81.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(), 0x81.toByte(), 0x01.toByte(),
            0xC0.toByte(),
        )
        assertContentEquals(slice2, ComboHidDescriptor.DESCRIPTOR.copyOfRange(0, slice2.size))
    }

    // --- media report (Slice-2 semantics preserved) ---------------------------------

    @Test
    fun `media press reports keep their slice-2 bits`() {
        assertContentEquals(byteArrayOf(0x01), MediaButton.PLAY_PAUSE.pressReport())
        assertContentEquals(byteArrayOf(0x10), MediaButton.VOLUME_UP.pressReport())
        assertContentEquals(byteArrayOf(0x40), MediaButton.STOP.pressReport())
        assertContentEquals(byteArrayOf(0x00), MediaButton.RELEASE_REPORT)
    }

    // --- gamepad button bit positions ----------------------------------------------

    @Test
    fun `gamepad buttons sit on kernel-convention bits`() {
        // bit0=A(south) bit1=B(east) bit3=X(north) bit4=Y(west) — bits 2/5 (C/Z) skipped.
        assertEquals(0, GamepadButton.A.bit)
        assertEquals(1, GamepadButton.B.bit)
        assertEquals(3, GamepadButton.X.bit)
        assertEquals(4, GamepadButton.Y.bit)
        assertEquals(6, GamepadButton.L1.bit)
        assertEquals(7, GamepadButton.R1.bit)
        assertEquals(10, GamepadButton.SELECT.bit)
        assertEquals(11, GamepadButton.START.bit)
        val used = GamepadButton.entries.map { it.bit }
        assertEquals(used.size, used.toSet().size, "duplicate bit assignment")
        assertFalse(2 in used, "bit2 is the kernel's BTN_C — must stay skipped")
        assertFalse(5 in used, "bit5 is the kernel's BTN_Z — must stay skipped")
    }
}

class KeyboardStateTest {

    @Test
    fun `press and release build the boot-style payload in press order`() {
        val kb = KeyboardState()
        assertTrue(kb.keyDown(KeyUsage.Z))
        assertTrue(kb.keyDown(KeyUsage.ARROW_UP))
        assertContentEquals(
            byteArrayOf(0, 0, KeyUsage.Z.toByte(), KeyUsage.ARROW_UP.toByte(), 0, 0, 0, 0),
            kb.report(),
        )
        assertTrue(kb.keyUp(KeyUsage.Z))
        assertContentEquals(
            byteArrayOf(0, 0, KeyUsage.ARROW_UP.toByte(), 0, 0, 0, 0, 0),
            kb.report(),
        )
    }

    @Test
    fun `seventh concurrent key is refused, not silently dropped`() {
        val kb = KeyboardState()
        val six = listOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09)
        six.forEach { assertTrue(kb.keyDown(it)) }
        assertFalse(kb.keyDown(0x0A))
        assertContentEquals(six.map { it.toByte() }.toByteArray(), kb.report().copyOfRange(2, 8))
    }

    @Test
    fun `duplicate press is refused and release of an unpressed key reports false`() {
        val kb = KeyboardState()
        assertTrue(kb.keyDown(KeyUsage.SPACE))
        assertFalse(kb.keyDown(KeyUsage.SPACE))
        assertFalse(kb.keyUp(KeyUsage.ENTER))
    }

    @Test
    fun `modifiers set and clear their mask bits`() {
        val kb = KeyboardState()
        kb.setModifier(KeyUsage.MOD_LEFT_SHIFT, true)
        kb.setModifier(KeyUsage.MOD_LEFT_CTRL, true)
        assertEquals(0x03, kb.report()[0].toInt())
        kb.setModifier(KeyUsage.MOD_LEFT_SHIFT, false)
        assertEquals(0x01, kb.report()[0].toInt())
    }

    @Test
    fun `clear returns to neutral`() {
        val kb = KeyboardState()
        kb.keyDown(KeyUsage.A)
        kb.setModifier(KeyUsage.MOD_LEFT_ALT, true)
        kb.clear()
        assertTrue(kb.isNeutral())
        assertContentEquals(ByteArray(8), kb.report())
    }

    @Test
    fun `usage range is validated`() {
        assertFailsWith<IllegalArgumentException> { KeyboardState().keyDown(0x00) }
        assertFailsWith<IllegalArgumentException> { KeyboardState().keyDown(0x66) }
    }
}

class GamepadStateTest {

    @Test
    fun `button bits land in the two button bytes`() {
        val gp = GamepadState()
        gp.buttonDown(GamepadButton.A)        // bit 0 -> byte0 0x01
        gp.buttonDown(GamepadButton.START)    // bit 11 -> byte1 0x08
        val r = gp.report()
        assertEquals(0x01, r[0].toInt() and 0xFF)
        assertEquals(0x08, r[1].toInt() and 0xFF)
        gp.buttonUp(GamepadButton.A)
        assertEquals(0x00, gp.report()[0].toInt())
    }

    @Test
    fun `hat combine covers all eight directions`() {
        val gp = GamepadState()
        fun hat(vararg dirs: DpadDirection): Int {
            gp.clear()
            dirs.forEach { gp.dpad(it, true) }
            return gp.hatValue()
        }
        assertEquals(1, hat(DpadDirection.UP))
        assertEquals(2, hat(DpadDirection.UP, DpadDirection.RIGHT))
        assertEquals(3, hat(DpadDirection.RIGHT))
        assertEquals(4, hat(DpadDirection.DOWN, DpadDirection.RIGHT))
        assertEquals(5, hat(DpadDirection.DOWN))
        assertEquals(6, hat(DpadDirection.DOWN, DpadDirection.LEFT))
        assertEquals(7, hat(DpadDirection.LEFT))
        assertEquals(8, hat(DpadDirection.UP, DpadDirection.LEFT))
        assertEquals(0, hat())
    }

    @Test
    fun `opposite dpad directions cancel instead of reporting nonsense`() {
        val gp = GamepadState()
        gp.dpad(DpadDirection.UP, true)
        gp.dpad(DpadDirection.DOWN, true)
        assertEquals(0, gp.hatValue())
        gp.dpad(DpadDirection.LEFT, true)
        assertEquals(7, gp.hatValue()) // vertical cancelled, horizontal wins
        gp.dpad(DpadDirection.DOWN, false)
        assertEquals(8, gp.hatValue()) // UP+LEFT = NW once DOWN releases
    }

    @Test
    fun `axes clamp to the descriptor's logical range and clear centers everything`() {
        val gp = GamepadState()
        gp.setAxes(500, -500)
        var r = gp.report()
        assertEquals(127, r[3].toInt())
        assertEquals(-127, r[4].toInt())
        gp.buttonDown(GamepadButton.B)
        gp.dpad(DpadDirection.UP, true)
        gp.clear()
        assertTrue(gp.isNeutral())
        r = gp.report()
        assertContentEquals(ByteArray(ComboHidDescriptor.GAMEPAD_REPORT_BYTES), r)
    }

    @Test
    fun `payload length matches the descriptor constant`() {
        assertEquals(ComboHidDescriptor.GAMEPAD_REPORT_BYTES, GamepadState().report().size)
        assertEquals(ComboHidDescriptor.KEYBOARD_REPORT_BYTES, KeyboardState().report().size)
    }
}
