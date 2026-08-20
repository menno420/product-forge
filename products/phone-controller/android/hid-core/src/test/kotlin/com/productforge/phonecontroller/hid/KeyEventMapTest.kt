/*
 * KeyEventMapTest — pins the Android-keycode constants to their frozen platform
 * values and the keycode→HID mapping to the usage table, so a typo in either
 * is a red test here instead of a wrong key on a host.
 *
 * The AKEY_* expectations below are the documented android.view.KeyEvent
 * values (public API, frozen since their introduction); this module is pure
 * JVM so they cannot be imported, which is exactly why they are pinned.
 */
package com.productforge.phonecontroller.hid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyEventMapTest {

    @Test
    fun androidKeycodeConstantsMatchThePlatformValues() {
        // Spot-pin every constant family to the documented KeyEvent values.
        assertEquals(29, KeyEventMap.AKEY_A)
        assertEquals(54, KeyEventMap.AKEY_Z)
        assertEquals(7, KeyEventMap.AKEY_0)
        assertEquals(16, KeyEventMap.AKEY_9)
        assertEquals(19, KeyEventMap.AKEY_DPAD_UP)
        assertEquals(20, KeyEventMap.AKEY_DPAD_DOWN)
        assertEquals(21, KeyEventMap.AKEY_DPAD_LEFT)
        assertEquals(22, KeyEventMap.AKEY_DPAD_RIGHT)
        assertEquals(55, KeyEventMap.AKEY_COMMA)
        assertEquals(56, KeyEventMap.AKEY_PERIOD)
        assertEquals(57, KeyEventMap.AKEY_ALT_LEFT)
        assertEquals(58, KeyEventMap.AKEY_ALT_RIGHT)
        assertEquals(59, KeyEventMap.AKEY_SHIFT_LEFT)
        assertEquals(60, KeyEventMap.AKEY_SHIFT_RIGHT)
        assertEquals(61, KeyEventMap.AKEY_TAB)
        assertEquals(62, KeyEventMap.AKEY_SPACE)
        assertEquals(66, KeyEventMap.AKEY_ENTER)
        assertEquals(67, KeyEventMap.AKEY_DEL)
        assertEquals(68, KeyEventMap.AKEY_GRAVE)
        assertEquals(69, KeyEventMap.AKEY_MINUS)
        assertEquals(70, KeyEventMap.AKEY_EQUALS)
        assertEquals(71, KeyEventMap.AKEY_LEFT_BRACKET)
        assertEquals(72, KeyEventMap.AKEY_RIGHT_BRACKET)
        assertEquals(73, KeyEventMap.AKEY_BACKSLASH)
        assertEquals(74, KeyEventMap.AKEY_SEMICOLON)
        assertEquals(75, KeyEventMap.AKEY_APOSTROPHE)
        assertEquals(76, KeyEventMap.AKEY_SLASH)
        assertEquals(82, KeyEventMap.AKEY_MENU)
        assertEquals(85, KeyEventMap.AKEY_MEDIA_PLAY_PAUSE)
        assertEquals(86, KeyEventMap.AKEY_MEDIA_STOP)
        assertEquals(87, KeyEventMap.AKEY_MEDIA_NEXT)
        assertEquals(88, KeyEventMap.AKEY_MEDIA_PREVIOUS)
        assertEquals(92, KeyEventMap.AKEY_PAGE_UP)
        assertEquals(93, KeyEventMap.AKEY_PAGE_DOWN)
        assertEquals(111, KeyEventMap.AKEY_ESCAPE)
        assertEquals(112, KeyEventMap.AKEY_FORWARD_DEL)
        assertEquals(113, KeyEventMap.AKEY_CTRL_LEFT)
        assertEquals(114, KeyEventMap.AKEY_CTRL_RIGHT)
        assertEquals(115, KeyEventMap.AKEY_CAPS_LOCK)
        assertEquals(116, KeyEventMap.AKEY_SCROLL_LOCK)
        assertEquals(117, KeyEventMap.AKEY_META_LEFT)
        assertEquals(118, KeyEventMap.AKEY_META_RIGHT)
        assertEquals(120, KeyEventMap.AKEY_SYSRQ)
        assertEquals(121, KeyEventMap.AKEY_BREAK)
        assertEquals(122, KeyEventMap.AKEY_MOVE_HOME)
        assertEquals(123, KeyEventMap.AKEY_MOVE_END)
        assertEquals(124, KeyEventMap.AKEY_INSERT)
        assertEquals(131, KeyEventMap.AKEY_F1)
        assertEquals(142, KeyEventMap.AKEY_F12)
        assertEquals(143, KeyEventMap.AKEY_NUM_LOCK)
        assertEquals(144, KeyEventMap.AKEY_NUMPAD_0)
        assertEquals(153, KeyEventMap.AKEY_NUMPAD_9)
        assertEquals(154, KeyEventMap.AKEY_NUMPAD_DIVIDE)
        assertEquals(155, KeyEventMap.AKEY_NUMPAD_MULTIPLY)
        assertEquals(156, KeyEventMap.AKEY_NUMPAD_SUBTRACT)
        assertEquals(157, KeyEventMap.AKEY_NUMPAD_ADD)
        assertEquals(158, KeyEventMap.AKEY_NUMPAD_DOT)
        assertEquals(160, KeyEventMap.AKEY_NUMPAD_ENTER)
    }

    @Test
    fun lettersDigitsAndFunctionRowsMapContiguously() {
        assertEquals(0x04, KeyEventMap.usageFor(KeyEventMap.AKEY_A)) // a
        assertEquals(0x1D, KeyEventMap.usageFor(KeyEventMap.AKEY_Z)) // z
        assertEquals(0x27, KeyEventMap.usageFor(KeyEventMap.AKEY_0)) // 0 wraps to 0x27
        assertEquals(0x1E, KeyEventMap.usageFor(KeyEventMap.AKEY_0 + 1)) // 1
        assertEquals(0x26, KeyEventMap.usageFor(KeyEventMap.AKEY_9)) // 9
        assertEquals(KeyUsage.F1, KeyEventMap.usageFor(KeyEventMap.AKEY_F1))
        assertEquals(KeyUsage.F12, KeyEventMap.usageFor(KeyEventMap.AKEY_F12))
        // Numpad: Android 0..9 contiguous; HID puts 0 (0x62) after 1..9 (0x59..0x61).
        assertEquals(0x62, KeyEventMap.usageFor(KeyEventMap.AKEY_NUMPAD_0))
        assertEquals(0x59, KeyEventMap.usageFor(KeyEventMap.AKEY_NUMPAD_0 + 1))
        assertEquals(0x61, KeyEventMap.usageFor(KeyEventMap.AKEY_NUMPAD_9))
    }

    @Test
    fun namedKeysMapToTheirUsages() {
        assertEquals(KeyUsage.ARROW_UP, KeyEventMap.usageFor(KeyEventMap.AKEY_DPAD_UP))
        assertEquals(KeyUsage.ARROW_RIGHT, KeyEventMap.usageFor(KeyEventMap.AKEY_DPAD_RIGHT))
        assertEquals(KeyUsage.ENTER, KeyEventMap.usageFor(KeyEventMap.AKEY_ENTER))
        assertEquals(KeyUsage.BACKSPACE, KeyEventMap.usageFor(KeyEventMap.AKEY_DEL))
        assertEquals(KeyUsage.DELETE_FORWARD, KeyEventMap.usageFor(KeyEventMap.AKEY_FORWARD_DEL))
        assertEquals(KeyUsage.ESCAPE, KeyEventMap.usageFor(KeyEventMap.AKEY_ESCAPE))
        assertEquals(KeyUsage.HOME, KeyEventMap.usageFor(KeyEventMap.AKEY_MOVE_HOME))
        assertEquals(KeyUsage.END, KeyEventMap.usageFor(KeyEventMap.AKEY_MOVE_END))
        assertEquals(KeyUsage.PAGE_UP, KeyEventMap.usageFor(KeyEventMap.AKEY_PAGE_UP))
        assertEquals(KeyUsage.PAGE_DOWN, KeyEventMap.usageFor(KeyEventMap.AKEY_PAGE_DOWN))
        assertEquals(KeyUsage.PRINT_SCREEN, KeyEventMap.usageFor(KeyEventMap.AKEY_SYSRQ))
        assertEquals(0x39, KeyEventMap.usageFor(KeyEventMap.AKEY_CAPS_LOCK))
        assertEquals(0x47, KeyEventMap.usageFor(KeyEventMap.AKEY_SCROLL_LOCK))
        assertEquals(0x48, KeyEventMap.usageFor(KeyEventMap.AKEY_BREAK))
        assertEquals(0x49, KeyEventMap.usageFor(KeyEventMap.AKEY_INSERT))
        assertEquals(0x53, KeyEventMap.usageFor(KeyEventMap.AKEY_NUM_LOCK))
        assertEquals(0x58, KeyEventMap.usageFor(KeyEventMap.AKEY_NUMPAD_ENTER))
        assertEquals(0x65, KeyEventMap.usageFor(KeyEventMap.AKEY_MENU))
    }

    @Test
    fun modifierKeysRideTheModifierByteNotTheKeyArray() {
        assertEquals(KeyUsage.MOD_LEFT_CTRL, KeyEventMap.modifierMaskFor(KeyEventMap.AKEY_CTRL_LEFT))
        assertEquals(KeyUsage.MOD_LEFT_SHIFT, KeyEventMap.modifierMaskFor(KeyEventMap.AKEY_SHIFT_LEFT))
        assertEquals(KeyUsage.MOD_LEFT_ALT, KeyEventMap.modifierMaskFor(KeyEventMap.AKEY_ALT_LEFT))
        assertEquals(KeyUsage.MOD_LEFT_GUI, KeyEventMap.modifierMaskFor(KeyEventMap.AKEY_META_LEFT))
        assertEquals(0x10, KeyEventMap.modifierMaskFor(KeyEventMap.AKEY_CTRL_RIGHT))
        assertEquals(0x20, KeyEventMap.modifierMaskFor(KeyEventMap.AKEY_SHIFT_RIGHT))
        assertEquals(0x40, KeyEventMap.modifierMaskFor(KeyEventMap.AKEY_ALT_RIGHT))
        assertEquals(0x80, KeyEventMap.modifierMaskFor(KeyEventMap.AKEY_META_RIGHT))
        for (mod in listOf(
            KeyEventMap.AKEY_CTRL_LEFT, KeyEventMap.AKEY_CTRL_RIGHT,
            KeyEventMap.AKEY_SHIFT_LEFT, KeyEventMap.AKEY_SHIFT_RIGHT,
            KeyEventMap.AKEY_ALT_LEFT, KeyEventMap.AKEY_ALT_RIGHT,
            KeyEventMap.AKEY_META_LEFT, KeyEventMap.AKEY_META_RIGHT,
        )) {
            assertNull(KeyEventMap.usageFor(mod), "modifier $mod must not enter the key array")
        }
        assertNull(KeyEventMap.modifierMaskFor(KeyEventMap.AKEY_A))
    }

    @Test
    fun mediaKeysTapTheConsumerReport() {
        assertEquals(MediaButton.PLAY_PAUSE, KeyEventMap.mediaFor(KeyEventMap.AKEY_MEDIA_PLAY_PAUSE))
        assertEquals(MediaButton.STOP, KeyEventMap.mediaFor(KeyEventMap.AKEY_MEDIA_STOP))
        assertEquals(MediaButton.NEXT, KeyEventMap.mediaFor(KeyEventMap.AKEY_MEDIA_NEXT))
        assertEquals(MediaButton.PREVIOUS, KeyEventMap.mediaFor(KeyEventMap.AKEY_MEDIA_PREVIOUS))
        assertNull(KeyEventMap.mediaFor(KeyEventMap.AKEY_ENTER))
    }

    @Test
    fun everyMappedUsageFitsTheReportTwoKeyArray() {
        // KeyboardState.keyDown requires usage in 0x01..0x65 (the descriptor's
        // Usage/Logical Maximum) — nothing this map emits may violate that.
        for (keyCode in KeyEventMap.mappedKeyCodes()) {
            val usage = KeyEventMap.usageFor(keyCode) ?: continue
            assertTrue(usage in 0x01..0x65, "usage 0x${usage.toString(16)} for keycode $keyCode out of range")
        }
    }

    @Test
    fun everyMappedKeycodeAnswersExactlyOneChannel() {
        // A keycode is a key-array usage OR a modifier OR a media tap — never two.
        for (keyCode in KeyEventMap.mappedKeyCodes()) {
            val channels = listOfNotNull(
                KeyEventMap.usageFor(keyCode),
                KeyEventMap.modifierMaskFor(keyCode),
                KeyEventMap.mediaFor(keyCode),
            )
            assertEquals(1, channels.size, "keycode $keyCode answers ${channels.size} channels")
        }
    }

    @Test
    fun volumeAndSystemKeysStayUnmapped() {
        // 24/25 = VOLUME_UP/DOWN (the app's volume-keys seam), 164 = VOLUME_MUTE,
        // 4 = BACK, 3 = HOME, 187 = APP_SWITCH, 26 = POWER.
        for (keyCode in listOf(24, 25, 164, 4, 3, 187, 26)) {
            assertNull(KeyEventMap.usageFor(keyCode))
            assertNull(KeyEventMap.modifierMaskFor(keyCode))
            assertNull(KeyEventMap.mediaFor(keyCode))
        }
    }

    @Test
    fun keyboardStateAcceptsEveryMappedUsage() {
        // End-to-end guard: every usage this map emits is accepted by the actual
        // report builder (fresh state per key so rollover never interferes).
        for (keyCode in KeyEventMap.mappedKeyCodes()) {
            val usage = KeyEventMap.usageFor(keyCode) ?: continue
            val state = KeyboardState()
            assertTrue(state.keyDown(usage), "KeyboardState refused usage 0x${usage.toString(16)}")
        }
    }
}
