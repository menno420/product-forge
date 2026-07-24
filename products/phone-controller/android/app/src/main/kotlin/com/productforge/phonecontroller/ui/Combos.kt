/*
 * Combos — the shared chord table (Slice 9): one source of truth for the editor's
 * combo picker AND the built-in Shortcuts deck, so the two never drift.
 *
 * A chord is (label, modifier mask, key usage); the layout codec stores it as the
 * decimal pair "mask:usage" (PadActionType.COMBO). Windows/Linux-centric defaults —
 * the custom builder in the editor covers everything else.
 */
package com.productforge.phonecontroller.ui

import com.productforge.phonecontroller.hid.KeyUsage

object Combos {

    data class Chord(val label: String, val mask: Int, val usage: Int) {
        /** The PadAction.code encoding ("mask:usage"). */
        val code: String get() = "$mask:$usage"
    }

    private const val CTRL = KeyUsage.MOD_LEFT_CTRL
    private const val SHIFT = KeyUsage.MOD_LEFT_SHIFT
    private const val ALT = KeyUsage.MOD_LEFT_ALT
    private const val GUI = KeyUsage.MOD_LEFT_GUI

    /** The everyday chords — first 16 fill the Shortcuts deck's 4×4 grid. */
    val PRESETS: List<Chord> = listOf(
        Chord("Copy", CTRL, KeyUsage.letterUsage('c')),
        Chord("Paste", CTRL, KeyUsage.letterUsage('v')),
        Chord("Cut", CTRL, KeyUsage.letterUsage('x')),
        Chord("Undo", CTRL, KeyUsage.letterUsage('z')),
        Chord("Redo", CTRL, KeyUsage.letterUsage('y')),
        Chord("Select all", CTRL, KeyUsage.letterUsage('a')),
        Chord("Find", CTRL, KeyUsage.letterUsage('f')),
        Chord("Save", CTRL, KeyUsage.letterUsage('s')),
        Chord("Alt+Tab", ALT, KeyUsage.TAB),
        Chord("Close tab", CTRL, KeyUsage.letterUsage('w')),
        Chord("New tab", CTRL, KeyUsage.letterUsage('t')),
        Chord("Reopen tab", CTRL or SHIFT, KeyUsage.letterUsage('t')),
        Chord("Screenshot", GUI, KeyUsage.PRINT_SCREEN),
        Chord("Lock", GUI, KeyUsage.letterUsage('l')),
        Chord("Desktop", GUI, KeyUsage.letterUsage('d')),
        Chord("Task mgr", CTRL or SHIFT, KeyUsage.ESCAPE),
    )

    /** Parse a stored "mask:usage" code; null on anything malformed (fail-soft). */
    fun parse(code: String): Pair<Int, Int>? {
        val parts = code.split(":")
        if (parts.size != 2) return null
        val mask = parts[0].toIntOrNull() ?: return null
        val usage = parts[1].toIntOrNull() ?: return null
        if (mask !in 0..0xFF || usage !in 0..0x65) return null
        return mask to usage
    }
}
