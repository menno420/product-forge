/*
 * MacroRunner — plays a MACRO action's timed step sequence (Slice 10).
 *
 * A macro is a JSON array of steps: {"t": action type name, "c": action code,
 * "l": display label, "h": hold ms, "g": gap ms}. Each step is pressed for h,
 * released, then the runner waits g before the next step. Steps resolve through
 * the same raw-action resolver the pads use, so a step can be any input the app
 * can send (key, chord, gamepad button, D-pad, media, mouse) — except another
 * macro (no nesting; the parser skips them).
 *
 * One macro at a time (busy-guard: re-pressing while running is ignored — safer
 * than queueing during gameplay). cancel() is the guard recipe: called on pad
 * switch, host disconnect, and destroy, and it RELEASES the in-flight step so
 * nothing stays held (same contract as TextTyper/TurboEngine).
 */
package com.productforge.phonecontroller.ui

import org.json.JSONArray
import org.json.JSONObject

class MacroRunner(
    /** Resolve (type name, code) to a press/release sink; null = unresolvable step. */
    private val resolve: (String, String) -> ((Boolean) -> Unit)?,
) {

    data class Step(
        val type: String,
        val code: String,
        val label: String,
        val holdMs: Long,
        val gapMs: Long,
    )

    @Volatile private var cancelled = false
    @Volatile private var worker: Thread? = null

    val busy: Boolean get() = worker?.isAlive == true

    /** Start playing [stepsJson]; ignored while another macro is running. */
    fun play(stepsJson: String) {
        if (busy) return
        val steps = parse(stepsJson)
        if (steps.isEmpty()) return
        cancelled = false
        val t = Thread {
            for (step in steps) {
                if (cancelled) break
                val action = resolve(step.type, step.code)
                if (action == null) {
                    // WAIT steps (and unresolvable strays) spend their time idle.
                    pause(step.holdMs + step.gapMs)
                    continue
                }
                action(true)
                pause(step.holdMs)
                action(false) // released even if cancelled mid-hold — nothing sticks
                if (cancelled) break
                pause(step.gapMs)
            }
        }
        worker = t
        t.start()
    }

    fun cancel() {
        cancelled = true
        worker?.let { runCatching { it.join(JOIN_MS) } }
        worker = null
    }

    private fun pause(ms: Long) {
        if (ms <= 0) return
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            cancelled = true
        }
    }

    companion object {
        const val DEFAULT_HOLD_MS = 60L
        const val DEFAULT_GAP_MS = 80L
        const val MAX_STEPS = 64
        private const val JOIN_MS = 400L

        /** Fail-soft parse: malformed input or nested macros yield an empty/short list. */
        fun parse(stepsJson: String): List<Step> = runCatching {
            val arr = JSONArray(stepsJson)
            buildList {
                for (i in 0 until minOf(arr.length(), MAX_STEPS)) {
                    val o = arr.getJSONObject(i)
                    val type = o.getString("t")
                    if (type == "MACRO") continue // no nesting
                    add(
                        Step(
                            type = type,
                            code = o.getString("c"),
                            label = o.optString("l", type),
                            holdMs = o.optLong("h", DEFAULT_HOLD_MS).coerceIn(10L, 5000L),
                            gapMs = o.optLong("g", DEFAULT_GAP_MS).coerceIn(0L, 5000L),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }

        /** The reserved type name for pure-delay steps (resolves to null on purpose). */
        const val TYPE_WAIT = "WAIT"

        /** Append a step to a (possibly empty/blank) steps JSON string. */
        fun appendStep(
            stepsJson: String,
            type: String,
            code: String,
            label: String,
            holdMs: Long = DEFAULT_HOLD_MS,
            gapMs: Long = DEFAULT_GAP_MS,
        ): String {
            val arr = runCatching { JSONArray(stepsJson) }.getOrElse { JSONArray() }
            arr.put(
                JSONObject()
                    .put("t", type).put("c", code).put("l", label)
                    .put("h", holdMs).put("g", gapMs),
            )
            return arr.toString()
        }

        /** Drop the last step; returns the shortened JSON (empty array stays valid). */
        fun removeLastStep(stepsJson: String): String = runCatching {
            val arr = JSONArray(stepsJson)
            val out = JSONArray()
            for (i in 0 until arr.length() - 1) out.put(arr.getJSONObject(i))
            out.toString()
        }.getOrElse { "[]" }

        /** Human summary for the editor dialog ("Copy → A → ↓ (3 steps)"). */
        fun describe(stepsJson: String): String {
            val steps = parse(stepsJson)
            if (steps.isEmpty()) return ""
            return steps.joinToString(" → ") { it.label }
        }
    }
}
