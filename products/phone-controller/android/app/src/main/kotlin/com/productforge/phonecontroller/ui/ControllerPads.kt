/*
 * ControllerPads — the switchable input pads, built programmatically with plain
 * android.widget so the app stays AndroidX-free (the standing dependency-light
 * choice from Slice 3).
 *
 * Input semantics:
 *   * Hold-capable controls send PRESS on finger-down and RELEASE on finger-up —
 *     that is what makes holds work on the receiving device (hold-to-walk in an
 *     emulator is a held HID report, not a repeated tap).
 *   * GAME pads (Full gamepad · GBA pad · Emu keys) route touches through
 *     [SlidePadRouter]: every finger position is hit-tested per event, so SLIDING
 *     between buttons presses the new one and releases the old without lifting the
 *     finger (owner playtest ask, 2026-07-23: D-pad glide). Multi-finger chords
 *     keep working — the router diffs the union of buttons under all fingers.
 *   * The QWERTY keyboard pad deliberately does NOT slide-activate (gliding across
 *     a typing surface would spam letters); its keys are classic per-button holds.
 *     Held keys auto-repeat courtesy of the host OS's typematic handling.
 *   * Media controls keep Slice-2 tap semantics (press+release pair per tap).
 *
 * Pad layout notes:
 *   * Full gamepad: D-pad left, A/B/X/Y diamond right (Xbox-style positions,
 *     matching the KEYCODE_BUTTON_* each bit maps to on the receiver), L1/R1
 *     shoulders, Select/Start below.
 *   * GBA pad: D-pad left, B/A pair right (GBA's diagonal arrangement), L/R
 *     shoulders, Select/Start below — matched to the owner's GBA-emulator use.
 *   * Touchpad: TouchpadView surface (drag = pointer, tap = left click, two-finger
 *     tap = right click, two-finger drag = natural scroll) + hold-capable
 *     Left/Middle/Right buttons and a sensitivity slider.
 */
package com.productforge.phonecontroller.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.productforge.phonecontroller.hid.DpadDirection
import com.productforge.phonecontroller.hid.GamepadButton
import com.productforge.phonecontroller.hid.KeyUsage
import com.productforge.phonecontroller.hid.MediaButton
import com.productforge.phonecontroller.hid.MouseButton

/** The UI→transport sink a pad drives. Implemented by MainActivity. */
interface PadHost {
    fun onGamepadButton(button: GamepadButton, down: Boolean)
    fun onDpad(direction: DpadDirection, down: Boolean)
    fun onKey(usage: Int, down: Boolean)
    fun onModifier(mask: Int, down: Boolean)
    fun onMediaTap(button: MediaButton)
    fun onMouseMove(dx: Int, dy: Int)
    fun onMouseScroll(notches: Int)
    fun onMouseButton(button: MouseButton, down: Boolean)
    fun onMouseClick(button: MouseButton)
    fun onLeftStick(x: Int, y: Int)
    fun onRightStick(z: Int, rz: Int)
}

/** Touchpad tuning the host owns (persisted in activity prefs). */
interface TouchpadConfig {
    /** Pointer-speed multiplier, 0.5..3.0. */
    var sensitivity: Float

    /** Invert two-finger scroll (Slice 10 Settings toggle; default natural). */
    val invertScroll: Boolean get() = false
}

/** Gyro control surface the Analog pad's toggle drives (owned by MainActivity). */
interface GyroToggle {
    val available: Boolean
    val running: Boolean

    /** Flip the driver; returns the resulting running state. */
    fun toggle(): Boolean

    /** Re-capture neutral from the current hold (long-press; "set 0 input"). */
    fun recenter() {}
}

object ControllerPads {

    // ---- game pads (slide-over activation) ------------------------------------------

    fun buildGamepadPad(context: Context, host: PadHost): View {
        val b = Builder(context)
        val shoulders = b.row(
            1f,
            b.slide("L1") { d -> host.onGamepadButton(GamepadButton.L1, d) },
            b.gap(2f),
            b.slide("R1") { d -> host.onGamepadButton(GamepadButton.R1, d) },
        )
        val clusters = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(b.dpadGrid(host), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f))
            addView(b.gap(0.5f))
            addView(
                b.grid3(
                    null, b.slideTinted("Y", TINT_Y) { d -> host.onGamepadButton(GamepadButton.Y, d) }, null,
                    b.slideTinted("X", TINT_X) { d -> host.onGamepadButton(GamepadButton.X, d) }, null,
                    b.slideTinted("B", TINT_B) { d -> host.onGamepadButton(GamepadButton.B, d) },
                    null, b.slideTinted("A", TINT_A) { d -> host.onGamepadButton(GamepadButton.A, d) }, null,
                ),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f),
            )
        }
        val meta = b.row(
            1f,
            b.gap(1f),
            b.slide("SELECT") { d -> host.onGamepadButton(GamepadButton.SELECT, d) },
            b.slide("START") { d -> host.onGamepadButton(GamepadButton.START, d) },
            b.gap(1f),
        )
        return b.slidePadRoot(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(shoulders, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(clusters, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f))
                addView(meta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            },
        )
    }

    fun buildGbaPad(context: Context, host: PadHost): View {
        val b = Builder(context)
        val shoulders = b.row(
            1f,
            b.slide("L") { d -> host.onGamepadButton(GamepadButton.L1, d) },
            b.gap(2f),
            b.slide("R") { d -> host.onGamepadButton(GamepadButton.R1, d) },
        )
        // GBA face pair: A upper-right, B lower-left (the console's diagonal).
        val face = b.grid3(
            null, null, b.slideTinted("A", TINT_A) { d -> host.onGamepadButton(GamepadButton.A, d) },
            null, null, null,
            b.slideTinted("B", TINT_B) { d -> host.onGamepadButton(GamepadButton.B, d) }, null, null,
        )
        val clusters = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(b.dpadGrid(host), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f))
            addView(b.gap(0.5f))
            addView(face, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f))
        }
        val meta = b.row(
            1f,
            b.gap(1f),
            b.slide("SELECT") { d -> host.onGamepadButton(GamepadButton.SELECT, d) },
            b.slide("START") { d -> host.onGamepadButton(GamepadButton.START, d) },
            b.gap(1f),
        )
        return b.slidePadRoot(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(shoulders, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(clusters, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f))
                addView(meta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            },
        )
    }

    fun buildEmuKeysPad(context: Context, host: PadHost): View {
        val b = Builder(context)
        fun k(label: String, usage: Int) = b.slide(label) { d -> host.onKey(usage, d) }
        return b.slidePadRoot(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(b.row(1f, b.gap(1f), k("↑", KeyUsage.ARROW_UP), b.gap(1f)))
                addView(b.row(1f, k("←", KeyUsage.ARROW_LEFT), k("↓", KeyUsage.ARROW_DOWN), k("→", KeyUsage.ARROW_RIGHT)))
                addView(b.row(1f, k("Z", KeyUsage.Z), k("X", KeyUsage.X), k("A", KeyUsage.A), k("S", KeyUsage.S)))
                addView(
                    b.row(
                        1f,
                        b.slide("SHIFT") { d -> host.onModifier(KeyUsage.MOD_LEFT_SHIFT, d) },
                        k("SPACE", KeyUsage.SPACE),
                        k("ENTER", KeyUsage.ENTER),
                    ),
                )
                addView(b.row(1f, k("ESC", KeyUsage.ESCAPE), k("TAB", KeyUsage.TAB), b.gap(2f)))
            },
        )
    }

    // ---- analog pad (two sticks + slide-over buttons + gyro toggle) -------------------

    fun buildAnalogPad(
        context: Context,
        host: PadHost,
        deadzonePct: Float,
        gyro: GyroToggle,
        gyroLabel: String,
        gyroOffLabel: String,
    ): View {
        val b = Builder(context)

        val shoulders = b.row(
            1f,
            b.slide("L1") { d -> host.onGamepadButton(GamepadButton.L1, d) },
            b.slide("L2") { d -> host.onGamepadButton(GamepadButton.L2, d) },
            b.gap(1f),
            b.slide("R2") { d -> host.onGamepadButton(GamepadButton.R2, d) },
            b.slide("R1") { d -> host.onGamepadButton(GamepadButton.R1, d) },
        )

        val leftStick = StickView(context) { x, y -> host.onLeftStick(x, y) }
            .apply { this.deadzonePct = deadzonePct }
        val rightStick = StickView(context) { z, rz -> host.onRightStick(z, rz) }
            .apply { this.deadzonePct = deadzonePct }

        val diamond = b.grid3(
            null, b.slideTinted("Y", TINT_Y) { d -> host.onGamepadButton(GamepadButton.Y, d) }, null,
            b.slideTinted("X", TINT_X) { d -> host.onGamepadButton(GamepadButton.X, d) }, null,
            b.slideTinted("B", TINT_B) { d -> host.onGamepadButton(GamepadButton.B, d) },
            null, b.slideTinted("A", TINT_A) { d -> host.onGamepadButton(GamepadButton.A, d) }, null,
        )

        val sticksRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(leftStick, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.6f))
            addView(diamond, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f))
            addView(rightStick, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.6f))
        }

        val gyroButton = b.tap(if (gyro.running) gyroLabel else gyroOffLabel) {}
            .apply { isEnabled = gyro.available }
        gyroButton.setOnClickListener {
            Haptics.tick(gyroButton)
            val running = gyro.toggle()
            gyroButton.text = if (running) gyroLabel else gyroOffLabel
        }
        // Long-press = recenter (set the neutral "0 input" to the current hold).
        gyroButton.setOnLongClickListener {
            Haptics.tick(gyroButton)
            gyro.recenter()
            true
        }

        val meta = b.row(
            1f,
            b.slide("SELECT") { d -> host.onGamepadButton(GamepadButton.SELECT, d) },
            b.slide("START") { d -> host.onGamepadButton(GamepadButton.START, d) },
            gyroButton,
        )

        return b.slidePadRoot(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(shoulders, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(sticksRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3.2f))
                addView(meta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            },
        )
    }

    // ---- typing keyboard (per-key holds, no slide) -----------------------------------

    fun buildKeyboardPad(context: Context, host: PadHost): View {
        val b = Builder(context)
        fun k(label: String, usage: Int, weight: Float = 1f) =
            b.hold(label, weight) { d -> host.onKey(usage, d) }
        fun letters(row: String) = row.map { c -> k(c.uppercaseChar().toString(), KeyUsage.letterUsage(c)) }

        val r0 = mutableListOf<View>()
        for (n in 1..12) {
            r0.add(k("F$n", KeyUsage.F1 + (n - 1)))
        }

        val r1 = mutableListOf<View>(k("ESC", KeyUsage.ESCAPE, 1.4f))
        "1234567890".forEach { c -> r1.add(k(c.toString(), KeyUsage.digitUsage(c))) }
        r1.add(k("⌫", KeyUsage.BACKSPACE, 1.4f))

        val r2 = mutableListOf<View>(k("TAB", KeyUsage.TAB, 1.4f))
        r2.addAll(letters("qwertyuiop"))
        r2.add(k("DEL", KeyUsage.DELETE_FORWARD, 1.4f))

        val r3 = mutableListOf<View>()
        r3.addAll(letters("asdfghjkl"))
        r3.add(k(";", KeyUsage.SEMICOLON))
        r3.add(k("'", KeyUsage.APOSTROPHE))
        r3.add(k("⏎", KeyUsage.ENTER, 1.6f))

        val r4 = mutableListOf<View>(b.hold("SHIFT", 1.8f) { d -> host.onModifier(KeyUsage.MOD_LEFT_SHIFT, d) })
        r4.addAll(letters("zxcvbnm"))
        r4.add(k(",", KeyUsage.COMMA))
        r4.add(k(".", KeyUsage.PERIOD))
        r4.add(k("/", KeyUsage.SLASH))
        r4.add(k("↑", KeyUsage.ARROW_UP))

        val r5 = mutableListOf<View>(
            b.hold("CTRL", 1.3f) { d -> host.onModifier(KeyUsage.MOD_LEFT_CTRL, d) },
            b.hold("ALT", 1.3f) { d -> host.onModifier(KeyUsage.MOD_LEFT_ALT, d) },
            k("-", KeyUsage.MINUS), k("=", KeyUsage.EQUALS),
            k("[", KeyUsage.LEFT_BRACKET), k("]", KeyUsage.RIGHT_BRACKET),
            k("\\", KeyUsage.BACKSLASH), k("`", KeyUsage.GRAVE),
            k("SPACE", KeyUsage.SPACE, 2.6f),
            k("←", KeyUsage.ARROW_LEFT), k("↓", KeyUsage.ARROW_DOWN), k("→", KeyUsage.ARROW_RIGHT),
        )

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(b.row(0.7f, *r0.toTypedArray()))
            listOf(r1, r2, r3, r4, r5).forEach { cells ->
                addView(b.row(1f, *cells.toTypedArray()))
            }
        }
    }

    // ---- touchpad ----------------------------------------------------------------------

    fun buildTouchpadPad(
        context: Context,
        host: PadHost,
        config: TouchpadConfig,
        hint: String,
        sensitivityLabel: String,
    ): View {
        val b = Builder(context)
        val surface = TouchpadView(
            context,
            object : TouchpadView.Listener {
                override fun onMove(dx: Int, dy: Int) = host.onMouseMove(dx, dy)
                override fun onScroll(notches: Int) = host.onMouseScroll(notches)
                override fun onTap(button: MouseButton) = host.onMouseClick(button)
            },
        ).apply {
            sensitivity = config.sensitivity
            invertScroll = config.invertScroll
        }

        val hintView = TextView(context).apply {
            text = hint
            textSize = 12f
            gravity = Gravity.CENTER
        }

        val slider = SeekBar(context).apply {
            max = SENS_STEPS
            progress = ((config.sensitivity - SENS_MIN) / (SENS_MAX - SENS_MIN) * SENS_STEPS)
                .toInt().coerceIn(0, SENS_STEPS)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val s = SENS_MIN + (SENS_MAX - SENS_MIN) * progress / SENS_STEPS
                    surface.sensitivity = s
                    config.sensitivity = s
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        val sliderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply { text = sensitivityLabel; textSize = 12f },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            addView(slider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val buttons = b.row(
            1f,
            b.hold("LEFT") { d -> host.onMouseButton(MouseButton.LEFT, d) },
            b.hold("MID") { d -> host.onMouseButton(MouseButton.MIDDLE, d) },
            b.hold("RIGHT") { d -> host.onMouseButton(MouseButton.RIGHT, d) },
        )

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(hintView)
            addView(surface, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 5f))
            addView(sliderRow)
            addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    // ---- NDS pad (touch screen + DS button set, Slice 8) --------------------------------

    /**
     * Nintendo-DS-style combo pad: a touch-screen area (the DS bottom screen) plus the
     * DS button set — D-pad, X/A/B/Y in Nintendo positions (X top, Y left, A right,
     * B bottom), L/R, Start/Select. The touch area defaults to PEN mode: finger
     * contact = held LEFT button, so drags DRAW on the emulator's touch screen like a
     * stylus; the Pen toggle switches to hover (classic touchpad) for repositioning.
     * Portrait stacks screen-over-buttons like a held DS; landscape puts the clusters
     * beside the screen like a DS on its side.
     */
    fun buildNdsPad(
        context: Context,
        host: PadHost,
        config: TouchpadConfig,
        penEnabled: Boolean,
        onPenToggled: (Boolean) -> Unit,
        penOnLabel: String,
        penOffLabel: String,
        sensitivityLabel: String,
    ): View {
        val b = Builder(context)

        val surface = TouchpadView(
            context,
            object : TouchpadView.Listener {
                override fun onMove(dx: Int, dy: Int) = host.onMouseMove(dx, dy)
                override fun onScroll(notches: Int) = host.onMouseScroll(notches)
                override fun onTap(button: MouseButton) = host.onMouseClick(button)
                override fun onPen(down: Boolean) = host.onMouseButton(MouseButton.LEFT, down)
            },
        ).apply {
            sensitivity = config.sensitivity
            invertScroll = config.invertScroll
            penMode = penEnabled
        }

        val penButton = b.tap(if (penEnabled) penOnLabel else penOffLabel) {}
        penButton.setOnClickListener {
            Haptics.tick(penButton)
            surface.penMode = !surface.penMode
            penButton.text = if (surface.penMode) penOnLabel else penOffLabel
            onPenToggled(surface.penMode)
        }

        val slider = SeekBar(context).apply {
            max = SENS_STEPS
            progress = ((config.sensitivity - SENS_MIN) / (SENS_MAX - SENS_MIN) * SENS_STEPS)
                .toInt().coerceIn(0, SENS_STEPS)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val s = SENS_MIN + (SENS_MAX - SENS_MIN) * progress / SENS_STEPS
                    surface.sensitivity = s
                    config.sensitivity = s
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        val controlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply { text = sensitivityLabel; textSize = 12f },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            addView(slider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(penButton, LinearLayout.LayoutParams(b.dp(96), LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        // DS face diamond: X top, Y left, A right, B bottom (Nintendo positions).
        val diamond = b.grid3(
            null, b.slideTinted("X", TINT_X) { d -> host.onGamepadButton(GamepadButton.X, d) }, null,
            b.slideTinted("Y", TINT_Y) { d -> host.onGamepadButton(GamepadButton.Y, d) }, null,
            b.slideTinted("A", TINT_A) { d -> host.onGamepadButton(GamepadButton.A, d) },
            null, b.slideTinted("B", TINT_B) { d -> host.onGamepadButton(GamepadButton.B, d) }, null,
        )
        val dpad = b.dpadGrid(host)

        val landscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val root: ViewGroup = if (landscape) {
            val leftCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    b.slide("L") { d -> host.onGamepadButton(GamepadButton.L1, d) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.6f),
                )
                addView(dpad, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.4f))
                addView(
                    b.slide("SELECT") { d -> host.onGamepadButton(GamepadButton.SELECT, d) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.6f),
                )
            }
            val centerCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(surface, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(controlRow)
            }
            val rightCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    b.slide("R") { d -> host.onGamepadButton(GamepadButton.R1, d) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.6f),
                )
                addView(diamond, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.4f))
                addView(
                    b.slide("START") { d -> host.onGamepadButton(GamepadButton.START, d) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.6f),
                )
            }
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(leftCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
                addView(centerCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.7f))
                addView(rightCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
            }
        } else {
            val rail = b.row(
                0.7f,
                b.slide("L") { d -> host.onGamepadButton(GamepadButton.L1, d) },
                b.slide("SELECT") { d -> host.onGamepadButton(GamepadButton.SELECT, d) },
                b.slide("START") { d -> host.onGamepadButton(GamepadButton.START, d) },
                b.slide("R") { d -> host.onGamepadButton(GamepadButton.R1, d) },
            )
            val clusters = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(dpad, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f))
                addView(b.gap(0.3f))
                addView(diamond, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f))
            }
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(rail, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.7f))
                addView(surface, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.5f))
                addView(controlRow)
                addView(clusters, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.2f))
            }
        }
        return b.slidePadRoot(root)
    }

    // ---- presenter (Slice 9: slide keys + chords + on-phone timer + pointer strip) ------

    /**
     * Presentation clicker: big Prev/Next (PageUp/PageDown — what PowerPoint, Impress
     * and Google Slides all accept), Start (F5) / From-here (Shift+F5) / End (Esc) /
     * Blank (B), an elapsed-time Chronometer (tap = start/pause, long-press = reset)
     * and a touchpad strip for waving the host pointer at the slide.
     */
    fun buildPresenterPad(context: Context, host: PadHost, config: TouchpadConfig): View {
        val b = Builder(context)
        fun chord(mask: Int, usage: Int): (Boolean) -> Unit = { d ->
            if (d) {
                host.onModifier(mask, true)
                host.onKey(usage, true)
            } else {
                host.onKey(usage, false)
                host.onModifier(mask, false)
            }
        }

        val mainRow = b.row(
            2.2f,
            b.hold("◀ Prev") { d -> host.onKey(KeyUsage.PAGE_UP, d) },
            b.hold("Next ▶") { d -> host.onKey(KeyUsage.PAGE_DOWN, d) },
        )
        val controlRow = b.row(
            1f,
            b.hold("Start") { d -> host.onKey(KeyUsage.F1 + 4, d) }, // F5
            b.hold("Here", onChange = chord(KeyUsage.MOD_LEFT_SHIFT, KeyUsage.F1 + 4)),
            b.hold("End") { d -> host.onKey(KeyUsage.ESCAPE, d) },
            b.hold("Blank") { d -> host.onKey(KeyUsage.letterUsage('b'), d) },
        )

        // Elapsed-talk timer, entirely on-phone (no HID traffic): tap toggles,
        // long-press resets. Pause is modeled by remembering elapsed at stop.
        val timer = android.widget.Chronometer(context).apply {
            textSize = 30f
            gravity = Gravity.CENTER
            var running = false
            var elapsedAtPause = 0L
            setOnClickListener {
                Haptics.tick(it)
                if (running) {
                    elapsedAtPause = android.os.SystemClock.elapsedRealtime() - base
                    stop()
                } else {
                    base = android.os.SystemClock.elapsedRealtime() - elapsedAtPause
                    start()
                }
                running = !running
            }
            setOnLongClickListener {
                running = false
                elapsedAtPause = 0L
                stop()
                base = android.os.SystemClock.elapsedRealtime()
                true
            }
        }

        val pointer = TouchpadView(
            context,
            object : TouchpadView.Listener {
                override fun onMove(dx: Int, dy: Int) = host.onMouseMove(dx, dy)
                override fun onScroll(notches: Int) = host.onMouseScroll(notches)
                override fun onTap(button: MouseButton) = host.onMouseClick(button)
            },
        ).apply {
            sensitivity = config.sensitivity
            invertScroll = config.invertScroll
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(mainRow)
            addView(controlRow)
            addView(
                timer,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            addView(pointer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.7f))
        }
    }

    // ---- shortcuts deck (Slice 9: serverless macro pad) ---------------------------------

    /**
     * A 4×4 grid of the everyday chords ([Combos.PRESETS]) — hold-capable so Alt+Tab
     * keeps the switcher open while held. No host software: the deck IS a keyboard.
     */
    fun buildShortcutsPad(context: Context, host: PadHost): View {
        val b = Builder(context)
        fun chordButton(chord: Combos.Chord): Button = b.hold(chord.label) { d ->
            if (d) {
                if (chord.mask != 0) host.onModifier(chord.mask, true)
                host.onKey(chord.usage, true)
            } else {
                host.onKey(chord.usage, false)
                if (chord.mask != 0) host.onModifier(chord.mask, false)
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            Combos.PRESETS.take(16).chunked(4).forEach { rowChords ->
                addView(b.row(1f, *rowChords.map { chordButton(it) }.toTypedArray()))
            }
        }
    }

    // ---- media (tap semantics, unchanged) ----------------------------------------------

    fun buildMediaPad(context: Context, host: PadHost): View {
        val b = Builder(context)
        fun m(label: String, button: MediaButton) = b.tap(label) { host.onMediaTap(button) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(b.row(1f, m("⏯ Play / Pause", MediaButton.PLAY_PAUSE)))
            addView(b.row(1f, m("⏮ Prev", MediaButton.PREVIOUS), m("⏹ Stop", MediaButton.STOP), m("⏭ Next", MediaButton.NEXT)))
            addView(b.row(1f, m("Vol −", MediaButton.VOLUME_DOWN), m("Mute", MediaButton.MUTE), m("Vol +", MediaButton.VOLUME_UP)))
        }
    }

    private const val SENS_MIN = 0.5f
    private const val SENS_MAX = 3.0f
    private const val SENS_STEPS = 25

    // Classic face-button colors, solid since the Slice-8 dark theme (console-style
    // pop on the dark surface; pressed states darken via ButtonStyler.flatStyle).
    // Plain vals: hex literals above Int.MAX_VALUE need .toInt(), which is not a
    // constant expression.
    private val TINT_A = 0xFF43A047.toInt() // green
    private val TINT_B = 0xFFE53935.toInt() // red
    private val TINT_X = 0xFF1E88E5.toInt() // blue
    private val TINT_Y = 0xFFFDD835.toInt() // yellow

    /** Small per-pad widget factory (dp math, button shapes, slide-pad assembly). */
    private class Builder(val context: Context) {

        /** Buttons awaiting a SlidePadRouter, in registration order. */
        val slideActions = LinkedHashMap<Button, (Boolean) -> Unit>()

        fun dp(v: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics,
        ).toInt()

        /** A slide-over button: pressed/released by the pad router, not its own listener. */
        fun slide(label: String, onChange: (down: Boolean) -> Unit): Button =
            base(label).apply {
                isClickable = false
                isFocusable = false
                slideActions[this] = onChange
            }

        /** A slide button in a classic face color (flat-styled, auto-contrast text). */
        fun slideTinted(label: String, argb: Int, onChange: (down: Boolean) -> Unit): Button =
            slide(label, onChange).apply {
                ButtonStyler.flatStyle(this, argb)
            }

        /** Wire the router onto a finished slide-pad tree. */
        @SuppressLint("ClickableViewAccessibility")
        fun slidePadRoot(root: ViewGroup): ViewGroup {
            root.setOnTouchListener(SlidePadRouter(root, LinkedHashMap(slideActions)))
            slideActions.clear()
            return root
        }

        /** The shared 3×3 D-pad grid (slide-over). */
        fun dpadGrid(host: PadHost): LinearLayout = grid3(
            null, slide("▲") { d -> host.onDpad(DpadDirection.UP, d) }, null,
            slide("◀") { d -> host.onDpad(DpadDirection.LEFT, d) }, null,
            slide("▶") { d -> host.onDpad(DpadDirection.RIGHT, d) },
            null, slide("▼") { d -> host.onDpad(DpadDirection.DOWN, d) }, null,
        )

        /** A classic hold button: press on finger-down, release on finger-up/cancel. */
        @SuppressLint("ClickableViewAccessibility")
        fun hold(label: String, weight: Float = 1f, onChange: (down: Boolean) -> Unit): Button =
            base(label).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            v.isPressed = true
                            Haptics.tick(v)
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
            base(label).apply {
                setOnClickListener {
                    Haptics.tick(it)
                    onTap()
                }
            }

        private fun base(label: String): Button = Button(context).apply {
            text = label
            isAllCaps = false
            // Auto-size instead of a fixed size: long labels (SELECT) shrink to fit
            // one line instead of wrapping (owner recording, 2026-07-24).
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(9, 16, 1, TypedValue.COMPLEX_UNIT_SP)
            minimumHeight = dp(48)
            ButtonStyler.flatStyle(this, ButtonStyler.SURFACE)
        }

        fun gap(weight: Float): View = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        }

        /** A horizontal row; children without params get equal weight. */
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

        /** A 3×3 grid (null = empty cell), used for D-pads and face diamonds. */
        fun grid3(vararg cells: View?): LinearLayout {
            require(cells.size == 9) { "grid3 needs exactly 9 cells" }
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                for (r in 0..2) {
                    val rowLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    for (c in 0..2) {
                        val cell = cells[r * 3 + c] ?: View(context)
                        rowLayout.addView(
                            cell,
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
                        )
                    }
                    addView(rowLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                }
            }
        }
    }
}
