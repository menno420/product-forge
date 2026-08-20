/*
 * KeyEventMap — Android KeyEvent keycode → HID mapping for physical-keyboard
 * type-through (Slice 21).
 *
 * KeyChars answers "what stroke produces this CHARACTER" (send-text replays a
 * composed string). This answers the live-event question: "the user pressed
 * PHYSICAL KEY <keycode> — which Report-2 usage / modifier bit / consumer button
 * does it ride?" Positions, not glyphs: the app forwards the base key plus the
 * modifier KEYS themselves, and the host applies its own layout — exactly how a
 * real BT keyboard behaves (so a '@' arrives as Shift held + the 2 key, decided
 * by the host).
 *
 * Pure JVM on purpose (this module never sees android.*), so the Android
 * keycodes are pinned here as constants. Their values are frozen public API
 * (KEYCODE_A has been 29 since API 1); KeyEventMapTest pins every one so a typo
 * here is a red test, not a wrong key on a host.
 *
 * Coverage = every key whose usage fits Report 2's key array (usage range
 * 0x01..0x65 — the descriptor's Usage/Logical Maximum). Keys whose HID usage
 * lies beyond that (keypad '=' 0x67, keypad ',' 0x85) return null and the
 * caller swallows them; volume keys are deliberately ABSENT (the app's
 * volume-keys seam owns that class, mapped or not).
 */
package com.productforge.phonecontroller.hid

object KeyEventMap {

    // ---- Android KeyEvent.KEYCODE_* values (frozen public API, pinned by test) ----
    const val AKEY_DPAD_UP = 19
    const val AKEY_DPAD_DOWN = 20
    const val AKEY_DPAD_LEFT = 21
    const val AKEY_DPAD_RIGHT = 22
    const val AKEY_A = 29 // ..Z = 54 (contiguous)
    const val AKEY_Z = 54
    const val AKEY_0 = 7 // ..9 = 16 (contiguous)
    const val AKEY_9 = 16
    const val AKEY_COMMA = 55
    const val AKEY_PERIOD = 56
    const val AKEY_ALT_LEFT = 57
    const val AKEY_ALT_RIGHT = 58
    const val AKEY_SHIFT_LEFT = 59
    const val AKEY_SHIFT_RIGHT = 60
    const val AKEY_TAB = 61
    const val AKEY_SPACE = 62
    const val AKEY_ENTER = 66
    const val AKEY_DEL = 67 // backspace
    const val AKEY_GRAVE = 68
    const val AKEY_MINUS = 69
    const val AKEY_EQUALS = 70
    const val AKEY_LEFT_BRACKET = 71
    const val AKEY_RIGHT_BRACKET = 72
    const val AKEY_BACKSLASH = 73
    const val AKEY_SEMICOLON = 74
    const val AKEY_APOSTROPHE = 75
    const val AKEY_SLASH = 76
    const val AKEY_MENU = 82
    const val AKEY_MEDIA_PLAY_PAUSE = 85
    const val AKEY_MEDIA_STOP = 86
    const val AKEY_MEDIA_NEXT = 87
    const val AKEY_MEDIA_PREVIOUS = 88
    const val AKEY_PAGE_UP = 92
    const val AKEY_PAGE_DOWN = 93
    const val AKEY_ESCAPE = 111
    const val AKEY_FORWARD_DEL = 112
    const val AKEY_CTRL_LEFT = 113
    const val AKEY_CTRL_RIGHT = 114
    const val AKEY_CAPS_LOCK = 115
    const val AKEY_SCROLL_LOCK = 116
    const val AKEY_META_LEFT = 117
    const val AKEY_META_RIGHT = 118
    const val AKEY_SYSRQ = 120 // PrintScreen
    const val AKEY_BREAK = 121 // Pause
    const val AKEY_MOVE_HOME = 122
    const val AKEY_MOVE_END = 123
    const val AKEY_INSERT = 124
    const val AKEY_F1 = 131 // ..F12 = 142 (contiguous)
    const val AKEY_F12 = 142
    const val AKEY_NUM_LOCK = 143
    const val AKEY_NUMPAD_0 = 144 // ..9 = 153 (contiguous)
    const val AKEY_NUMPAD_9 = 153
    const val AKEY_NUMPAD_DIVIDE = 154
    const val AKEY_NUMPAD_MULTIPLY = 155
    const val AKEY_NUMPAD_SUBTRACT = 156
    const val AKEY_NUMPAD_ADD = 157
    const val AKEY_NUMPAD_DOT = 158
    const val AKEY_NUMPAD_ENTER = 160

    // HID usages KeyUsage does not already name (Report-2 range, US layout free).
    private const val USAGE_CAPS_LOCK = 0x39
    private const val USAGE_SCROLL_LOCK = 0x47
    private const val USAGE_PAUSE = 0x48
    private const val USAGE_INSERT = 0x49
    private const val USAGE_NUM_LOCK = 0x53
    private const val USAGE_KP_DIVIDE = 0x54
    private const val USAGE_KP_MULTIPLY = 0x55
    private const val USAGE_KP_SUBTRACT = 0x56
    private const val USAGE_KP_ADD = 0x57
    private const val USAGE_KP_ENTER = 0x58
    private const val USAGE_KP_1 = 0x59 // ..9 = 0x61
    private const val USAGE_KP_0 = 0x62
    private const val USAGE_KP_DOT = 0x63
    private const val USAGE_APPLICATION = 0x65 // the context-menu key

    /** Right-side modifier masks (report byte 0 bits 4..7; left side in KeyUsage). */
    const val MOD_RIGHT_CTRL = 0x10
    const val MOD_RIGHT_SHIFT = 0x20
    const val MOD_RIGHT_ALT = 0x40
    const val MOD_RIGHT_GUI = 0x80

    private val FIXED: Map<Int, Int> = mapOf(
        AKEY_DPAD_UP to KeyUsage.ARROW_UP,
        AKEY_DPAD_DOWN to KeyUsage.ARROW_DOWN,
        AKEY_DPAD_LEFT to KeyUsage.ARROW_LEFT,
        AKEY_DPAD_RIGHT to KeyUsage.ARROW_RIGHT,
        AKEY_COMMA to KeyUsage.COMMA,
        AKEY_PERIOD to KeyUsage.PERIOD,
        AKEY_TAB to KeyUsage.TAB,
        AKEY_SPACE to KeyUsage.SPACE,
        AKEY_ENTER to KeyUsage.ENTER,
        AKEY_DEL to KeyUsage.BACKSPACE,
        AKEY_GRAVE to KeyUsage.GRAVE,
        AKEY_MINUS to KeyUsage.MINUS,
        AKEY_EQUALS to KeyUsage.EQUALS,
        AKEY_LEFT_BRACKET to KeyUsage.LEFT_BRACKET,
        AKEY_RIGHT_BRACKET to KeyUsage.RIGHT_BRACKET,
        AKEY_BACKSLASH to KeyUsage.BACKSLASH,
        AKEY_SEMICOLON to KeyUsage.SEMICOLON,
        AKEY_APOSTROPHE to KeyUsage.APOSTROPHE,
        AKEY_SLASH to KeyUsage.SLASH,
        AKEY_MENU to USAGE_APPLICATION,
        AKEY_PAGE_UP to KeyUsage.PAGE_UP,
        AKEY_PAGE_DOWN to KeyUsage.PAGE_DOWN,
        AKEY_ESCAPE to KeyUsage.ESCAPE,
        AKEY_FORWARD_DEL to KeyUsage.DELETE_FORWARD,
        AKEY_CAPS_LOCK to USAGE_CAPS_LOCK,
        AKEY_SCROLL_LOCK to USAGE_SCROLL_LOCK,
        AKEY_SYSRQ to KeyUsage.PRINT_SCREEN,
        AKEY_BREAK to USAGE_PAUSE,
        AKEY_MOVE_HOME to KeyUsage.HOME,
        AKEY_MOVE_END to KeyUsage.END,
        AKEY_INSERT to USAGE_INSERT,
        AKEY_NUM_LOCK to USAGE_NUM_LOCK,
        AKEY_NUMPAD_DIVIDE to USAGE_KP_DIVIDE,
        AKEY_NUMPAD_MULTIPLY to USAGE_KP_MULTIPLY,
        AKEY_NUMPAD_SUBTRACT to USAGE_KP_SUBTRACT,
        AKEY_NUMPAD_ADD to USAGE_KP_ADD,
        AKEY_NUMPAD_DOT to USAGE_KP_DOT,
        AKEY_NUMPAD_ENTER to USAGE_KP_ENTER,
    )

    /**
     * The Report-2 key-array usage for an Android keycode, or null when the key
     * has no in-range usage (caller swallows it). Modifier keys return null here
     * — they ride the modifier byte via [modifierMaskFor].
     */
    fun usageFor(keyCode: Int): Int? = when (keyCode) {
        in AKEY_A..AKEY_Z -> KeyUsage.letterUsage('a' + (keyCode - AKEY_A))
        in AKEY_0..AKEY_9 -> KeyUsage.digitUsage('0' + (keyCode - AKEY_0))
        in AKEY_F1..AKEY_F12 -> KeyUsage.F1 + (keyCode - AKEY_F1)
        AKEY_NUMPAD_0 -> USAGE_KP_0
        in (AKEY_NUMPAD_0 + 1)..AKEY_NUMPAD_9 -> USAGE_KP_1 + (keyCode - AKEY_NUMPAD_0 - 1)
        else -> FIXED[keyCode]
    }

    /**
     * The modifier-byte mask for a modifier KEY (both sides — the descriptor
     * spans E0..E7), or null for every non-modifier keycode.
     */
    fun modifierMaskFor(keyCode: Int): Int? = when (keyCode) {
        AKEY_CTRL_LEFT -> KeyUsage.MOD_LEFT_CTRL
        AKEY_SHIFT_LEFT -> KeyUsage.MOD_LEFT_SHIFT
        AKEY_ALT_LEFT -> KeyUsage.MOD_LEFT_ALT
        AKEY_META_LEFT -> KeyUsage.MOD_LEFT_GUI
        AKEY_CTRL_RIGHT -> MOD_RIGHT_CTRL
        AKEY_SHIFT_RIGHT -> MOD_RIGHT_SHIFT
        AKEY_ALT_RIGHT -> MOD_RIGHT_ALT
        AKEY_META_RIGHT -> MOD_RIGHT_GUI
        else -> null
    }

    /**
     * The consumer-report button for a keyboard media key, or null. Volume keys
     * are deliberately not here — the app's volume-keys setting owns that class.
     */
    fun mediaFor(keyCode: Int): MediaButton? = when (keyCode) {
        AKEY_MEDIA_PLAY_PAUSE -> MediaButton.PLAY_PAUSE
        AKEY_MEDIA_STOP -> MediaButton.STOP
        AKEY_MEDIA_NEXT -> MediaButton.NEXT
        AKEY_MEDIA_PREVIOUS -> MediaButton.PREVIOUS
        else -> null
    }

    /** Every keycode this map answers for (test surface + caller introspection). */
    fun mappedKeyCodes(): Set<Int> = buildSet {
        addAll(AKEY_A..AKEY_Z)
        addAll(AKEY_0..AKEY_9)
        addAll(AKEY_F1..AKEY_F12)
        addAll(AKEY_NUMPAD_0..AKEY_NUMPAD_9)
        addAll(FIXED.keys)
        add(AKEY_CTRL_LEFT); add(AKEY_CTRL_RIGHT)
        add(AKEY_SHIFT_LEFT); add(AKEY_SHIFT_RIGHT)
        add(AKEY_ALT_LEFT); add(AKEY_ALT_RIGHT)
        add(AKEY_META_LEFT); add(AKEY_META_RIGHT)
        add(AKEY_MEDIA_PLAY_PAUSE); add(AKEY_MEDIA_STOP)
        add(AKEY_MEDIA_NEXT); add(AKEY_MEDIA_PREVIOUS)
    }
}
