/*
 * KeyCharsTest — pins the send-text character map (Slice 9).
 *
 * Every printable ASCII character must map to a stroke a US-QWERTY host decodes
 * back into that character; shift pairs must share their base usage; anything
 * outside the contract must return null (the app skips it, never crashes).
 */
package com.productforge.phonecontroller.hid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyCharsTest {

    @Test
    fun `every printable ascii char maps`() {
        for (code in 0x20..0x7E) {
            val c = code.toChar()
            val stroke = KeyChars.strokeFor(c)
            assertTrue(stroke != null, "expected a stroke for '$c' (0x${code.toString(16)})")
            assertTrue(stroke.usage in 0x04..0x65, "usage for '$c' outside the descriptor's key array")
        }
    }

    @Test
    fun `letters lowercase plain uppercase shifted same usage`() {
        for (c in 'a'..'z') {
            val lower = KeyChars.strokeFor(c)!!
            val upper = KeyChars.strokeFor(c.uppercaseChar())!!
            assertEquals(lower.usage, upper.usage)
            assertEquals(false, lower.shift)
            assertEquals(true, upper.shift)
        }
    }

    @Test
    fun `digit row shift pairs share the digit usage`() {
        val pairs = ")!@#$%^&*(" // shift of 0,1,2..9 in digit order 0..9
        for (d in '0'..'9') {
            val sym = pairs[d - '0']
            assertEquals(KeyChars.strokeFor(d)!!.usage, KeyChars.strokeFor(sym)!!.usage, "pair $d/$sym")
            assertEquals(true, KeyChars.strokeFor(sym)!!.shift)
        }
    }

    @Test
    fun `punctuation shift pairs share base usage`() {
        val pairs = listOf('-' to '_', '=' to '+', '[' to '{', ']' to '}', '\\' to '|',
            ';' to ':', '\'' to '"', '`' to '~', ',' to '<', '.' to '>', '/' to '?')
        for ((base, shifted) in pairs) {
            assertEquals(KeyChars.strokeFor(base)!!.usage, KeyChars.strokeFor(shifted)!!.usage)
            assertEquals(false, KeyChars.strokeFor(base)!!.shift)
            assertEquals(true, KeyChars.strokeFor(shifted)!!.shift)
        }
    }

    @Test
    fun `newline and tab map to enter and tab`() {
        assertEquals(KeyUsage.ENTER, KeyChars.strokeFor('\n')!!.usage)
        assertEquals(KeyUsage.TAB, KeyChars.strokeFor('\t')!!.usage)
    }

    @Test
    fun `non-ascii returns null and typeableCount skips it`() {
        assertNull(KeyChars.strokeFor('é'))
        assertNull(KeyChars.strokeFor('€'))
        assertNull(KeyChars.strokeFor('\r'))
        assertEquals(5, KeyChars.typeableCount("héllo!")) // h,l,l,o,! — é skipped
    }

    @Test
    fun `new navigation and gui constants pin their HID values`() {
        assertEquals(0x46, KeyUsage.PRINT_SCREEN)
        assertEquals(0x4A, KeyUsage.HOME)
        assertEquals(0x4B, KeyUsage.PAGE_UP)
        assertEquals(0x4D, KeyUsage.END)
        assertEquals(0x4E, KeyUsage.PAGE_DOWN)
        assertEquals(0x08, KeyUsage.MOD_LEFT_GUI)
    }
}
