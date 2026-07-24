/*
 * KeyChars — printable-character → HID keystroke map (Slice 9, send-text feature).
 *
 * Turns a Char into the (usage, shift) pair a US-QWERTY host decodes back into that
 * character. This is the pure wire-format half of "type text on the host": the app
 * layer walks a string, and for each mappable char presses Shift (if needed) + the
 * usage, then releases. Coverage is exactly printable ASCII (0x20..0x7E) plus '\n'
 * (Enter) and '\t' (Tab); everything else returns null and the caller skips it —
 * HID keyboards send POSITIONS, not characters, so non-ASCII output depends on the
 * host's layout and is deliberately out of scope (documented app-side).
 *
 * Pure JVM on purpose — unit-tested in CI's SDK-free lane (KeyCharsTest pins every
 * printable ASCII char to a stroke and the shift pairs to their bases).
 */
package com.productforge.phonecontroller.hid

object KeyChars {

    /** One keystroke: the usage to press, and whether Shift must be held around it. */
    data class Stroke(val usage: Int, val shift: Boolean)

    private fun plain(usage: Int) = Stroke(usage, shift = false)
    private fun shifted(usage: Int) = Stroke(usage, shift = true)

    /** Shifted-symbol table for the digit row and punctuation (US-QWERTY pairs). */
    private val SYMBOLS: Map<Char, Stroke> = mapOf(
        '!' to shifted(KeyUsage.digitUsage('1')),
        '@' to shifted(KeyUsage.digitUsage('2')),
        '#' to shifted(KeyUsage.digitUsage('3')),
        '$' to shifted(KeyUsage.digitUsage('4')),
        '%' to shifted(KeyUsage.digitUsage('5')),
        '^' to shifted(KeyUsage.digitUsage('6')),
        '&' to shifted(KeyUsage.digitUsage('7')),
        '*' to shifted(KeyUsage.digitUsage('8')),
        '(' to shifted(KeyUsage.digitUsage('9')),
        ')' to shifted(KeyUsage.digitUsage('0')),
        '-' to plain(KeyUsage.MINUS),
        '_' to shifted(KeyUsage.MINUS),
        '=' to plain(KeyUsage.EQUALS),
        '+' to shifted(KeyUsage.EQUALS),
        '[' to plain(KeyUsage.LEFT_BRACKET),
        '{' to shifted(KeyUsage.LEFT_BRACKET),
        ']' to plain(KeyUsage.RIGHT_BRACKET),
        '}' to shifted(KeyUsage.RIGHT_BRACKET),
        '\\' to plain(KeyUsage.BACKSLASH),
        '|' to shifted(KeyUsage.BACKSLASH),
        ';' to plain(KeyUsage.SEMICOLON),
        ':' to shifted(KeyUsage.SEMICOLON),
        '\'' to plain(KeyUsage.APOSTROPHE),
        '"' to shifted(KeyUsage.APOSTROPHE),
        '`' to plain(KeyUsage.GRAVE),
        '~' to shifted(KeyUsage.GRAVE),
        ',' to plain(KeyUsage.COMMA),
        '<' to shifted(KeyUsage.COMMA),
        '.' to plain(KeyUsage.PERIOD),
        '>' to shifted(KeyUsage.PERIOD),
        '/' to plain(KeyUsage.SLASH),
        '?' to shifted(KeyUsage.SLASH),
    )

    /**
     * The stroke that produces [c] on a US-QWERTY host, or null if the character
     * is outside printable ASCII (+ newline/tab) and should be skipped.
     */
    fun strokeFor(c: Char): Stroke? = when (c) {
        in 'a'..'z' -> plain(KeyUsage.letterUsage(c))
        in 'A'..'Z' -> shifted(KeyUsage.letterUsage(c.lowercaseChar()))
        in '0'..'9' -> plain(KeyUsage.digitUsage(c))
        ' ' -> plain(KeyUsage.SPACE)
        '\n' -> plain(KeyUsage.ENTER)
        '\t' -> plain(KeyUsage.TAB)
        else -> SYMBOLS[c]
    }

    /** How many characters of [text] are typeable (callers report skipped count). */
    fun typeableCount(text: CharSequence): Int = text.count { strokeFor(it) != null }
}
