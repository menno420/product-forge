/*
 * TurboEngine — per-button auto-fire for custom layouts (Slice 6).
 *
 * Wraps a press/release action: while the wrapped control is held, the action is
 * pulsed at ~10 Hz (50 ms down / 50 ms up); release cancels the pulse and sends the
 * final release. Guard recipe (session card): cancelAll() MUST be called on pad
 * switch and on host disconnect — those two call sites are the stuck-rapid-fire
 * protection.
 */
package com.productforge.phonecontroller.ui

import android.os.Handler
import android.os.Looper

class TurboEngine(private val halfPeriodMs: Long = 50L) {

    private val handler = Handler(Looper.getMainLooper())
    private val running = HashMap<Any, Runnable>()

    /** Wrap [action]; a non-turbo control passes through untouched. */
    fun wrap(key: Any, turbo: Boolean, action: (Boolean) -> Unit): (Boolean) -> Unit {
        if (!turbo) return action
        return { down ->
            if (down) {
                action(true)
                var phase = true
                val pulse = object : Runnable {
                    override fun run() {
                        phase = !phase
                        action(phase)
                        handler.postDelayed(this, halfPeriodMs)
                    }
                }
                running.remove(key)?.let(handler::removeCallbacks)
                running[key] = pulse
                handler.postDelayed(pulse, halfPeriodMs)
            } else {
                running.remove(key)?.let(handler::removeCallbacks)
                action(false)
            }
        }
    }

    /** Stop every pulse (pad switch / disconnect). Callers release held state via the transport. */
    fun cancelAll() {
        running.values.forEach(handler::removeCallbacks)
        running.clear()
    }
}
