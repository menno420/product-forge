/*
 * HardwareKeyboard — physical-keyboard presence + capture engine (Slice 21).
 *
 * Presence: an attached EXTERNAL alphabetic keyboard (InputManager device list +
 * add/remove listener). The alphabetic requirement is deliberate on both sides:
 * volume rockers and BT remotes/gamepads also register SOURCE_KEYBOARD, and
 * capturing those would break their normal use.
 *
 * Capture: MainActivity routes dispatchKeyEvent here — BEFORE the view tree, so
 * captured keys can never focus-wander or "click" a focused pad button. Policy
 * (the card's D1): a mode must be active, the event must come from an external
 * hardware keyboard, the host must be connected and the layout editor closed.
 * Dialogs need no policy: they own their own window, so the activity dispatch
 * never sees keys typed into one. Volume keys and system navigation are never
 * captured. Auto-repeat is swallowed, never forwarded — the host generates its
 * own repeats from the held report.
 *
 * Modes:
 *   TYPE — live type-through: keycode → Report-2 usage / modifier bit /
 *          consumer tap via hid-core's KeyEventMap (the "way to write").
 *   PAD  — key → bound controller action through the shared resolveRaw
 *          vocabulary (the "input/controls" half); unbound keys are swallowed
 *          no-ops so the pad UI stays inert under stray presses.
 *
 * Safety: every captured DOWN is tracked in a reference-counted HeldKeyLedger
 * keyed by (device, key) — actions shared by several keys (W and ↑ both on
 * DPAD UP; two keyboards' Enter) fire on the first holder and release on the
 * LAST (Codex on pf #52, finding 1). releaseAll() fires on mode change, host
 * disconnect, onPause and window-focus loss (a dialog opening mid-hold moves
 * the UP into the dialog's window); a detaching device releases exactly its
 * own holds (finding 2 — its UPs can never arrive).
 */
package com.productforge.phonecontroller.input

import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import com.productforge.phonecontroller.hid.HeldKeyLedger
import com.productforge.phonecontroller.hid.KeyEventMap
import com.productforge.phonecontroller.hid.MediaButton

class HardwareKeyboard(
    private val inputManager: InputManager?,
    private val bindings: KeyBindingStore,
    private val prefs: SharedPreferences,
    /** Gate evaluated on every captured DOWN: connected && editor closed. */
    private val captureAllowed: () -> Boolean,
    /** Resolve a binding through the shared action vocabulary (null = corrupt). */
    private val resolveBinding: (KeyBinding) -> ((Boolean) -> Unit)?,
    private val typeKey: (usage: Int, down: Boolean) -> Unit,
    private val typeModifier: (mask: Int, down: Boolean) -> Unit,
    private val mediaTap: (MediaButton) -> Unit,
    /** Presence flip (attach/detach), on the main thread — UI refresh hook. */
    private val onPresenceChanged: (present: Boolean) -> Unit,
) {

    enum class KeyMode { OFF, TYPE, PAD }

    /** Ref-counted (device, key) hold tracking — the release-safety core. */
    private val ledger = HeldKeyLedger()

    private var bindingCache: Map<Int, KeyBinding>? = null
    private var lastPresence: Boolean? = null

    var mode: KeyMode
        get() = KeyMode.entries.getOrElse(prefs.getInt(PREF_MODE, 0)) { KeyMode.OFF }
        set(value) {
            if (value == mode) return
            releaseAll()
            prefs.edit().putInt(PREF_MODE, value.ordinal).apply()
        }

    fun cycleMode(): KeyMode {
        mode = KeyMode.entries[(mode.ordinal + 1) % KeyMode.entries.size]
        return mode
    }

    /** Call after any binding-store mutation (editor dialog, defaults, restore). */
    fun invalidateBindings() {
        bindingCache = null
    }

    /** True while an external alphabetic keyboard is attached. */
    fun keyboardPresent(): Boolean =
        InputDevice.getDeviceIds().any { id -> isExternalKeyboard(InputDevice.getDevice(id)) }

    /**
     * The dispatchKeyEvent hook. True = consumed (never reaches the view tree).
     */
    fun handle(event: KeyEvent): Boolean {
        if (mode == KeyMode.OFF) return false
        if (event.keyCode in NEVER_CAPTURE) return false
        // A key we hold must always see its UP, whatever changed mid-hold —
        // the ledger releases the shared action only on its LAST holder.
        if (event.action == KeyEvent.ACTION_UP) {
            if (ledger.release(event.deviceId, event.keyCode)) return true
        }
        if (!isExternalKeyboard(event.device)) return false
        if (!captureAllowed()) return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // Swallow auto-repeats and double-downs; the held report repeats host-side.
                if (event.repeatCount > 0 || ledger.isPressed(event.deviceId, event.keyCode)) {
                    return true
                }
                val resolved = actionForDown(event.keyCode)
                val identity = resolved?.first
                val action = resolved?.second ?: NOOP
                if (ledger.press(event.deviceId, event.keyCode, identity) { action(false) }) {
                    action(true)
                }
                true
            }
            // Untracked UP of an eligible key (pressed before the mode engaged):
            // swallow for consistency — the view tree never saw its DOWN either.
            KeyEvent.ACTION_UP -> true
            else -> true
        }
    }

    /** Release every captured key (mode change, disconnect, pause, focus loss). */
    fun releaseAll() = ledger.releaseAll()

    /**
     * Start watching attach/detach; call once from onCreate (main thread).
     * Registration comes FIRST, then a notify-capable reconcile — a keyboard
     * attached between the UI's first presence sample and this point would
     * otherwise stay invisible until an unrelated device event (Codex round 2
     * on pf #52). The reconcile always notifies once; the UI side compares
     * against what it actually rendered.
     */
    fun startWatching() {
        inputManager?.registerInputDeviceListener(deviceListener, null)
        lastPresence = null
        presenceCheck()
    }

    fun stopWatching() {
        inputManager?.unregisterInputDeviceListener(deviceListener)
    }

    /** Human label for a keycode ("W", "ENTER", "SHIFT RIGHT") — bind-time capture. */
    fun keyLabel(keyCode: Int): String =
        KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')

    /** Bind-time eligibility: a capturable key from an external keyboard. */
    fun bindable(event: KeyEvent): Boolean =
        event.keyCode !in NEVER_CAPTURE && isExternalKeyboard(event.device)

    /**
     * (action identity, action) for a captured DOWN, or null for a swallowed
     * no-op. The identity is what the ledger reference-counts: keys sharing an
     * underlying HELD state share it (same usage, same modifier mask, same
     * bound action), so a sibling key's UP can never release a held input.
     * STATELESS actions — media taps, which emit their whole effect on
     * action(true) — carry a null identity instead: each press must fire, so
     * they are tracked per key and never coalesced (Codex round 2 on pf #52).
     */
    private fun actionForDown(keyCode: Int): Pair<String?, (Boolean) -> Unit>? = when (mode) {
        KeyMode.OFF -> null
        KeyMode.TYPE -> {
            val modifier = KeyEventMap.modifierMaskFor(keyCode)
            val usage = KeyEventMap.usageFor(keyCode)
            val media = KeyEventMap.mediaFor(keyCode)
            when {
                modifier != null -> "m:$modifier" to { down: Boolean -> typeModifier(modifier, down) }
                usage != null -> "k:$usage" to { down: Boolean -> typeKey(usage, down) }
                media != null -> null to { down: Boolean -> if (down) mediaTap(media) }
                else -> null // unmappable: swallowed silently (positions, not glyphs)
            }
        }
        KeyMode.PAD -> bindingMap()[keyCode]?.let { b ->
            resolveBinding(b)?.let { action ->
                val identity = if (b.actionType == STATELESS_TYPE) null else "a:${b.actionType}:${b.actionCode}"
                identity to action
            }
        }
    }

    private fun bindingMap(): Map<Int, KeyBinding> =
        bindingCache ?: bindings.all().associateBy { it.keyCode }.also { bindingCache = it }

    private fun isExternalKeyboard(device: InputDevice?): Boolean {
        if (device == null || device.isVirtual) return false
        if (device.sources and InputDevice.SOURCE_KEYBOARD != InputDevice.SOURCE_KEYBOARD) return false
        if (device.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC) return false
        // isExternal is public API only from 29; below that the virtual+alphabetic
        // checks stand alone (built-in rockers are non-alphabetic anyway).
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || device.isExternal
    }

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = presenceCheck()
        override fun onInputDeviceRemoved(deviceId: Int) {
            // The device's UPs can never arrive — release exactly ITS holds,
            // even when another keyboard keeps aggregate presence true
            // (Codex on pf #52, finding 2).
            ledger.releaseDevice(deviceId)
            presenceCheck()
        }
        override fun onInputDeviceChanged(deviceId: Int) = presenceCheck()
    }

    private fun presenceCheck() {
        val present = keyboardPresent()
        if (present != lastPresence) {
            lastPresence = present
            if (!present) releaseAll() // belt: no keyboard left, nothing may stay held
            onPresenceChanged(present)
        }
    }

    companion object {
        /** The mode preference key — public so backup-everything can whitelist it. */
        const val PREF_MODE = "hw_key_mode"

        /** The one stateless action type reachable from a binding (taps on down). */
        private const val STATELESS_TYPE = "MEDIA"

        private val NOOP: (Boolean) -> Unit = { }

        /** Keys never captured in any mode (volume seam + system navigation). */
        private val NEVER_CAPTURE = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_POWER,
        )
    }
}
