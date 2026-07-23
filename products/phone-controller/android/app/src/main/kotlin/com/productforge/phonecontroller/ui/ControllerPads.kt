/*
 * ControllerPads — the three switchable input pads (gamepad / keys / media), built
 * programmatically with plain android.widget so the app stays AndroidX-free (the
 * standing dependency-light choice from Slice 3).
 *
 * Every hold-capable control sends PRESS on ACTION_DOWN and RELEASE on
 * ACTION_UP/ACTION_CANCEL — that is what makes holds work on the receiving device
 * (hold-to-walk in an emulator is a held HID report, not a repeated tap). Media
 * controls keep Slice-2 tap semantics (press+release pair per tap).
 *
 * Pad layout notes:
 *   * Gamepad: D-pad left, A/B/X/Y diamond right (Xbox-style positions: Y top,
 *     X left, B right, A bottom — matching the KEYCODE_BUTTON_* each bit maps to on
 *     the receiver), L1/R1 on the shoulders row, Select/Start centered below.
 *   * Keys: arrows + Z/X/A/S + Enter/Space/Shift/Esc/Tab — the classic emulator
 *     default keyboard binds (RetroArch et al.), so it works even where a receiver
 *     app only listens to keyboards.
 */
package com.productforge.phonecontroller.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.productforge.phonecontroller.hid.DpadDirection
import com.productforge.phonecontroller.hid.GamepadButton
import com.productforge.phonecontroller.hid.KeyUsage
import com.productforge.phonecontroller.hid.MediaButton

/** The UI→transport sink a pad drives. Implemented by MainActivity. */
interface PadHost {
    fun onGamepadButton(button: GamepadButton, down: Boolean)
    fun onDpad(direction: DpadDirection, down: Boolean)
    fun onKey(usage: Int, down: Boolean)
    fun onModifier(mask: Int, down: Boolean)
    fun onMediaTap(button: MediaButton)
}

object ControllerPads {

    fun buildGamepadPad(context: Context, host: PadHost): View {
        val b = Builder(context)

        val shoulders = b.row(
            1f,
            b.hold("L1") { down -> host.onGamepadButton(GamepadButton.L1, down) },
            b.gap(2f),
            b.hold("R1") { down -> host.onGamepadButton(GamepadButton.R1, down) },
        )

        val dpad = b.grid3(
            null, b.hold("▲") { d -> host.onDpad(DpadDirection.UP, d) }, null,
            b.hold("◀") { d -> host.onDpad(DpadDirection.LEFT, d) }, null,
            b.hold("▶") { d -> host.onDpad(DpadDirection.RIGHT, d) },
            null, b.hold("▼") { d -> host.onDpad(DpadDirection.DOWN, d) }, null,
        )

        val diamond = b.grid3(
            null, b.hold("Y") { d -> host.onGamepadButton(GamepadButton.Y, d) }, null,
            b.hold("X") { d -> host.onGamepadButton(GamepadButton.X, d) }, null,
            b.hold("B") { d -> host.onGamepadButton(GamepadButton.B, d) },
            null, b.hold("A") { d -> host.onGamepadButton(GamepadButton.A, d) }, null,
        )

        val clusters = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(dpad, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f))
            addView(b.gap(0.5f))
            addView(diamond, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f))
        }

        val meta = b.row(
            1f,
            b.gap(1f),
            b.hold("SELECT") { d -> host.onGamepadButton(GamepadButton.SELECT, d) },
            b.hold("START") { d -> host.onGamepadButton(GamepadButton.START, d) },
            b.gap(1f),
        )

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(shoulders, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(clusters, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f))
            addView(meta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    fun buildKeysPad(context: Context, host: PadHost): View {
        val b = Builder(context)
        fun k(label: String, usage: Int) = b.hold(label) { d -> host.onKey(usage, d) }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(b.row(1f, b.gap(1f), k("↑", KeyUsage.ARROW_UP), b.gap(1f)))
            addView(
                b.row(
                    1f,
                    k("←", KeyUsage.ARROW_LEFT),
                    k("↓", KeyUsage.ARROW_DOWN),
                    k("→", KeyUsage.ARROW_RIGHT),
                ),
            )
            addView(
                b.row(
                    1f,
                    k("Z", KeyUsage.Z), k("X", KeyUsage.X), k("A", KeyUsage.A), k("S", KeyUsage.S),
                ),
            )
            addView(
                b.row(
                    1f,
                    b.hold("SHIFT") { d -> host.onModifier(KeyUsage.MOD_LEFT_SHIFT, d) },
                    k("SPACE", KeyUsage.SPACE),
                    k("ENTER", KeyUsage.ENTER),
                ),
            )
            addView(b.row(1f, k("ESC", KeyUsage.ESCAPE), k("TAB", KeyUsage.TAB), b.gap(2f)))
        }
    }

    fun buildMediaPad(context: Context, host: PadHost): View {
        val b = Builder(context)
        fun m(label: String, button: MediaButton) = b.tap(label) { host.onMediaTap(button) }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(b.row(1f, m("⏯ Play / Pause", MediaButton.PLAY_PAUSE)))
            addView(
                b.row(
                    1f,
                    m("⏮ Prev", MediaButton.PREVIOUS), m("⏹ Stop", MediaButton.STOP), m("⏭ Next", MediaButton.NEXT),
                ),
            )
            addView(
                b.row(
                    1f,
                    m("Vol −", MediaButton.VOLUME_DOWN), m("Mute", MediaButton.MUTE), m("Vol +", MediaButton.VOLUME_UP),
                ),
            )
        }
    }

    /** Small per-pad widget factory (dp math + the hold/tap button shapes). */
    private class Builder(val context: Context) {

        fun dp(v: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics,
        ).toInt()

        /** A hold-capable button: press on finger-down, release on finger-up/cancel. */
        @SuppressLint("ClickableViewAccessibility")
        fun hold(label: String, onChange: (down: Boolean) -> Unit): Button =
            base(label).apply {
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            v.isPressed = true
                            onChange(true)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.isPressed = false
                            onChange(false)
                            v.performClick()
                            true
                        }
                        else -> false
                    }
                }
            }

        /** A tap button (press+release pair is sent by the transport). */
        fun tap(label: String, onTap: () -> Unit): Button =
            base(label).apply { setOnClickListener { onTap() } }

        private fun base(label: String): Button = Button(context).apply {
            text = label
            isAllCaps = false
            textSize = 18f
            minimumHeight = dp(56)
        }

        fun gap(weight: Float): View = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        }

        /** A horizontal row; every child gets equal weight unless it carries params. */
        fun row(weight: Float, vararg children: View): LinearLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                children.forEach { child ->
                    val lp = child.layoutParams as? LinearLayout.LayoutParams
                        ?: LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    addView(child, lp)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, weight,
                )
            }

        /** A 3×3 grid (null = empty cell), used for the D-pad and the face diamond. */
        fun grid3(vararg cells: View?): LinearLayout {
            require(cells.size == 9) { "grid3 needs exactly 9 cells" }
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                for (r in 0..2) {
                    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    for (c in 0..2) {
                        val cell = cells[r * 3 + c] ?: View(context)
                        row.addView(
                            cell,
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
                        )
                    }
                    addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                }
            }
        }
    }
}
