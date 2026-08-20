/*
 * KeyBindings — physical-keyboard key → controller action (Slice 21, PAD mode).
 *
 * One global table, NOT per-layout: the ten built-in pads are hand-coded views
 * with no spec model to hang a key on, so a per-layout field would cover only
 * custom layouts and split the feature in half (the card's D2). A binding fires
 * through the SAME resolveRaw action vocabulary as pads, macros and voice
 * commands, with real press/release semantics — down holds, up releases.
 *
 * Store shape mirrors VoiceStore (fail-soft JSON in prefs, public KEY so
 * backup-everything can carry the blob opaquely).
 */
package com.productforge.phonecontroller.input

import android.content.SharedPreferences
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/** One physical key → action binding. Type/code use the shared action vocabulary. */
data class KeyBinding(
    val keyCode: Int,
    /** Display name for the key (e.g. "W", "Enter") — captured at bind time. */
    val keyLabel: String,
    val actionType: String,
    val actionCode: String,
    val actionLabel: String,
)

/** Prefs-backed binding list (fail-soft JSON, same pattern as VoiceStore). */
class KeyBindingStore(private val prefs: SharedPreferences) {

    fun all(): List<KeyBinding> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                KeyBinding(
                    o.getInt("k"), o.optString("kl", o.getInt("k").toString()),
                    o.getString("t"), o.getString("c"), o.optString("l", o.getString("t")),
                )
            }
        }.getOrElse { emptyList() }
    }

    fun byKeyCode(keyCode: Int): KeyBinding? = all().firstOrNull { it.keyCode == keyCode }

    /** Add or replace — one action per physical key, the last bind wins. */
    fun put(binding: KeyBinding) =
        persist(all().filterNot { it.keyCode == binding.keyCode } + binding)

    fun remove(keyCode: Int) = persist(all().filterNot { it.keyCode == keyCode })

    /** Replace the whole table (Load-defaults and backup-restore semantics). */
    fun replaceAll(bindings: List<KeyBinding>) = persist(bindings)

    /** The raw store blob for backup (null when never written). */
    fun raw(): String? = prefs.getString(KEY, null)

    /** Restore the raw blob (backup-restore; "[]" is the explicit empty). */
    fun restoreRaw(raw: String) {
        prefs.edit().putString(KEY, raw).apply()
    }

    private fun persist(list: List<KeyBinding>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject().put("k", b.keyCode).put("kl", b.keyLabel)
                    .put("t", b.actionType).put("c", b.actionCode).put("l", b.actionLabel),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        /** Prefs key — public so backup-everything can carry the store blob opaquely. */
        const val KEY = "key_bindings"

        /**
         * The offered default table (installed only via the explicit
         * "Load defaults" confirm — never silently): WASD + arrows drive the
         * D-pad, IJKL the diamond in screen positions (I=north=X, K=south=A,
         * L=east=B, J=west=Y), Q/E → L1/R1, Z/C → L2/R2, Enter → START,
         * Right-Shift → SELECT (the classic emulator Select).
         */
        fun defaults(): List<KeyBinding> = listOf(
            KeyBinding(KeyEvent.KEYCODE_W, "W", "DPAD", "UP", "DPAD UP"),
            KeyBinding(KeyEvent.KEYCODE_A, "A", "DPAD", "LEFT", "DPAD LEFT"),
            KeyBinding(KeyEvent.KEYCODE_S, "S", "DPAD", "DOWN", "DPAD DOWN"),
            KeyBinding(KeyEvent.KEYCODE_D, "D", "DPAD", "RIGHT", "DPAD RIGHT"),
            KeyBinding(KeyEvent.KEYCODE_DPAD_UP, "↑", "DPAD", "UP", "DPAD UP"),
            KeyBinding(KeyEvent.KEYCODE_DPAD_LEFT, "←", "DPAD", "LEFT", "DPAD LEFT"),
            KeyBinding(KeyEvent.KEYCODE_DPAD_DOWN, "↓", "DPAD", "DOWN", "DPAD DOWN"),
            KeyBinding(KeyEvent.KEYCODE_DPAD_RIGHT, "→", "DPAD", "RIGHT", "DPAD RIGHT"),
            KeyBinding(KeyEvent.KEYCODE_I, "I", "GAMEPAD", "X", "X (north)"),
            KeyBinding(KeyEvent.KEYCODE_K, "K", "GAMEPAD", "A", "A (south)"),
            KeyBinding(KeyEvent.KEYCODE_L, "L", "GAMEPAD", "B", "B (east)"),
            KeyBinding(KeyEvent.KEYCODE_J, "J", "GAMEPAD", "Y", "Y (west)"),
            KeyBinding(KeyEvent.KEYCODE_Q, "Q", "GAMEPAD", "L1", "L1"),
            KeyBinding(KeyEvent.KEYCODE_E, "E", "GAMEPAD", "R1", "R1"),
            KeyBinding(KeyEvent.KEYCODE_Z, "Z", "GAMEPAD", "L2", "L2"),
            KeyBinding(KeyEvent.KEYCODE_C, "C", "GAMEPAD", "R2", "R2"),
            KeyBinding(KeyEvent.KEYCODE_ENTER, "Enter", "GAMEPAD", "START", "START"),
            KeyBinding(KeyEvent.KEYCODE_SHIFT_RIGHT, "RShift", "GAMEPAD", "SELECT", "SELECT"),
        )
    }
}
