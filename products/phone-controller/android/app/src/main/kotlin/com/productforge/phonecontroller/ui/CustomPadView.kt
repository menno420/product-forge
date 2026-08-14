/*
 * CustomPadView — renders a user-defined layout (Slice 6; widgets added Slice 15).
 *
 * PLAY mode: buttons are laid out at their percent positions; each button CONSUMES
 * its own touch (press on down, release on up) with per-button turbo, so Android's
 * motion-event splitting delivers every finger independently — 4+ simultaneous
 * inputs work (Slice 16). WIDGETS (analog sticks / 8-way D-pad / touchpad / gyro
 * toggle) render as their real interactive views wired to the host and likewise
 * consume their own touches.
 *
 * EDIT mode: drag a button OR widget to move it (positions snap to 1%); a SHORT
 * press opens the per-element dialog the host provides. Widgets render as labelled
 * placeholders (non-interactive) so editing never fires real input.
 */
package com.productforge.phonecontroller.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.productforge.phonecontroller.R
import com.productforge.phonecontroller.hid.MouseButton
import com.productforge.phonecontroller.layout.CustomLayout
import com.productforge.phonecontroller.layout.PadAction
import com.productforge.phonecontroller.layout.PadButtonSpec
import com.productforge.phonecontroller.layout.PadPositioned
import com.productforge.phonecontroller.layout.PadWidgetSpec
import com.productforge.phonecontroller.layout.PadWidgetType

class CustomPadView(
    context: Context,
    private val layout: CustomLayout,
    private val editMode: Boolean,
    private val actionResolver: (PadButtonSpec) -> (Boolean) -> Unit,
    private val turbo: TurboEngine,
    private val onEditButton: ((PadButtonSpec) -> Unit)? = null,
    private val host: PadHost? = null,
    private val gyro: GyroToggle? = null,
    private val deadzonePct: Float = 0.08f,
    private val onEditWidget: ((PadWidgetSpec) -> Unit)? = null,
    /** Global touchpad tuning; per-widget overrides win where set (Slice 18). */
    private val touchpad: TouchpadConfig? = null,
    /** Resolver for a button's long-press alternate action (Slice 18). */
    private val altResolver: ((PadAction) -> (Boolean) -> Unit)? = null,
    /** Hold time before a long-press alternate engages, from Settings. */
    private val altHoldMs: Long = 350L,
) : FrameLayout(context) {

    private val buttonViews = LinkedHashMap<Button, PadButtonSpec>()
    private val widgetViews = LinkedHashMap<View, PadWidgetSpec>()
    // Combined view→spec map for the generalized edit-mode drag (buttons + widgets).
    private val editSpecs = LinkedHashMap<View, PadPositioned>()

    init {
        rebuild()
    }

    /** Recreate all child views from the model (host calls after dialog edits). */
    @SuppressLint("ClickableViewAccessibility")
    fun rebuild() {
        removeAllViews()
        buttonViews.clear()
        widgetViews.clear()
        editSpecs.clear()
        setBackgroundColor(layout.bgColorArgb ?: 0x00000000)

        for (spec in layout.buttons) {
            val alt = spec.altAction // read once — dialogs mutate the spec live
            val button = Button(context).apply {
                text = buildString {
                    append(spec.label)
                    if (spec.turbo && alt == null) append(" ⚡")
                    if (alt != null) append(" ⏱")
                }
                isAllCaps = false
            }
            ButtonStyler.apply(button, spec, 0)
            buttonViews[button] = spec
            editSpecs[button] = spec
            if (!editMode) {
                // Each button consumes its own touch → 4+ simultaneous inputs (Slice 16).
                // An alternate replaces turbo (mutually exclusive; editor enforces —
                // this branch decides deterministically for imported layouts).
                if (alt != null && altResolver != null) {
                    button.setOnTouchListener(
                        HoldTouch(actionResolver(spec), altResolver.invoke(alt), altHoldMs),
                    )
                } else {
                    val action = turbo.wrap(button, spec.turbo, actionResolver(spec))
                    button.setOnTouchListener(HoldTouch(action))
                }
            } else {
                button.isClickable = false
                button.isFocusable = false
            }
            addView(button)
        }

        for (spec in layout.widgets) {
            val view = if (editMode) widgetPlaceholder(spec) else widgetView(spec)
            widgetViews[view] = spec
            editSpecs[view] = spec
            addView(view)
        }

        // Edit mode drags via the root; play mode leaves touches to the children.
        setOnTouchListener(if (editMode) EditTouch() else null)
        isMotionEventSplittingEnabled = true
        requestLayout()
    }

    /**
     * Per-button consuming press/release (play mode) — the multi-touch-safe path.
     *
     * Without an [altAction] this is the classic hold: press on finger-down, release
     * on finger-up. With one (Slice 18), the button carries two inputs: a quick tap
     * fires the primary as a press+release pair, and holding past [holdMs] engages
     * the alternate (held until finger-up). The primary therefore fires on RELEASE
     * for alt-carrying buttons — the unavoidable price of overloading one surface,
     * which is why alternates are per-button opt-in.
     */
    private inner class HoldTouch(
        private val action: (Boolean) -> Unit,
        private val altAction: ((Boolean) -> Unit)? = null,
        private val holdMs: Long = 0L,
    ) : OnTouchListener {

        private var altEngaged = false
        private var pendingEngage: Runnable? = null

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    Haptics.tick(v)
                    if (altAction == null) {
                        action(true)
                    } else {
                        altEngaged = false
                        val engage = Runnable {
                            // The pad may have been switched away mid-hold; a detached
                            // view must never start a held input nothing will release.
                            if (v.isAttachedToWindow) {
                                altEngaged = true
                                Haptics.tick(v)
                                altAction.invoke(true)
                            }
                        }
                        pendingEngage = engage
                        v.postDelayed(engage, holdMs)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (altAction == null) {
                        action(false)
                    } else {
                        pendingEngage?.let(v::removeCallbacks)
                        pendingEngage = null
                        if (altEngaged) {
                            altEngaged = false
                            altAction.invoke(false)
                        } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                            // Quick tap: primary as a press+release pair. The release
                            // rides a short delay so the host registers the report
                            // transition (same shape as voice-command taps).
                            action(true)
                            v.postDelayed({ action(false) }, TAP_FIRE_MS)
                        }
                    }
                    v.performClick()
                    return true
                }
            }
            return false
        }
    }

    /** The live interactive view for a widget (play mode), per-widget config applied. */
    @SuppressLint("ClickableViewAccessibility")
    private fun widgetView(spec: PadWidgetSpec): View = when (spec.type) {
        PadWidgetType.LEFT_STICK ->
            StickView(context) { x, y -> host?.onLeftStick(x, y) }.apply {
                deadzonePct = spec.deadzonePct ?: this@CustomPadView.deadzonePct
                invertY = spec.invertY
            }
        PadWidgetType.RIGHT_STICK ->
            StickView(context) { z, rz -> host?.onRightStick(z, rz) }.apply {
                deadzonePct = spec.deadzonePct ?: this@CustomPadView.deadzonePct
                invertY = spec.invertY
            }
        PadWidgetType.DPAD ->
            DpadView(context) { d, down -> host?.onDpad(d, down) }.apply { fourWay = spec.fourWay }
        PadWidgetType.TOUCHPAD ->
            TouchpadView(
                context,
                object : TouchpadView.Listener {
                    override fun onMove(dx: Int, dy: Int) { host?.onMouseMove(dx, dy) }
                    override fun onScroll(notches: Int) { host?.onMouseScroll(notches) }
                    override fun onTap(button: MouseButton) { host?.onMouseClick(button) }
                    override fun onPen(down: Boolean) { host?.onMouseButton(MouseButton.LEFT, down) }
                },
            ).apply {
                // Global Settings apply (they previously never reached custom-layout
                // touchpads at all — fixed in Slice 18); per-widget overrides win.
                sensitivity = spec.speedPct?.let { it / 100f } ?: touchpad?.sensitivity ?: 1.0f
                invertScroll = touchpad?.invertScroll ?: false
                penMode = spec.penMode
            }
        PadWidgetType.GYRO -> Button(context).apply {
            isAllCaps = false
            fun label() = if (gyro?.running == true) context.getString(R.string.gyro_on) else context.getString(R.string.gyro_off)
            text = label()
            isEnabled = gyro?.available == true
            ButtonStyler.flatStyle(this, ButtonStyler.SURFACE)
            setOnClickListener {
                Haptics.tick(this)
                gyro?.toggle()
                text = label()
            }
            setOnLongClickListener {
                Haptics.tick(this)
                gyro?.recenter()
                true
            }
        }
    }

    /** A labelled, non-interactive box shown for a widget in edit mode. */
    private fun widgetPlaceholder(spec: PadWidgetSpec): View = TextView(context).apply {
        text = widgetLabel(spec.type)
        gravity = Gravity.CENTER
        setTextColor(0xFFCED6DD.toInt())
        background = GradientDrawable().apply {
            setColor(0x22A9B4C0)
            cornerRadius = 10f * resources.displayMetrics.density
            setStroke((2f * resources.displayMetrics.density).toInt(), 0x88A9B4C0.toInt())
        }
    }

    private fun widgetLabel(type: PadWidgetType): String = when (type) {
        PadWidgetType.LEFT_STICK -> context.getString(R.string.widget_left_stick)
        PadWidgetType.RIGHT_STICK -> context.getString(R.string.widget_right_stick)
        PadWidgetType.DPAD -> context.getString(R.string.widget_dpad)
        PadWidgetType.TOUCHPAD -> context.getString(R.string.widget_touchpad)
        PadWidgetType.GYRO -> context.getString(R.string.widget_gyro)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        for ((button, spec) in buttonViews) {
            val bw = (spec.wPct * w).toInt()
            val bh = (spec.hPct * h).toInt()
            val bx = (spec.xPct * w).toInt()
            val by = (spec.yPct * h).toInt()
            ButtonStyler.apply(button, spec, bh) // real height → correct corner radii
            button.measure(
                MeasureSpec.makeMeasureSpec(bw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(bh, MeasureSpec.EXACTLY),
            )
            button.layout(bx, by, bx + bw, by + bh)
        }
        for ((view, spec) in widgetViews) {
            val bw = (spec.wPct * w).toInt()
            val bh = (spec.hPct * h).toInt()
            val bx = (spec.xPct * w).toInt()
            val by = (spec.yPct * h).toInt()
            view.measure(
                MeasureSpec.makeMeasureSpec(bw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(bh, MeasureSpec.EXACTLY),
            )
            view.layout(bx, by, bx + bw, by + bh)
        }
    }

    /** Edit-mode gesture handling: drag to move (button OR widget), short-press to configure. */
    private inner class EditTouch : OnTouchListener {

        private var grabbed: View? = null
        private var grabDx = 0f
        private var grabDy = 0f
        private var downTime = 0L
        private var dragged = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    grabbed = editSpecs.keys.lastOrNull { c ->
                        event.x >= c.left && event.x <= c.right && event.y >= c.top && event.y <= c.bottom
                    }
                    grabbed?.let { c ->
                        grabDx = event.x - c.left
                        grabDy = event.y - c.top
                        c.isPressed = true
                    }
                    downTime = event.eventTime
                    dragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val c = grabbed ?: return true
                    val spec = editSpecs[c] ?: return true
                    if (width == 0 || height == 0) return true
                    val snappedX = ((event.x - grabDx) / width * 100).toInt() / 100f
                    val snappedY = ((event.y - grabDy) / height * 100).toInt() / 100f
                    if (snappedX != spec.xPct || snappedY != spec.yPct) {
                        if (event.eventTime - downTime > TAP_MS || dragged) dragged = true
                        spec.xPct = snappedX
                        spec.yPct = snappedY
                        spec.clampToPad()
                        requestLayout()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val c = grabbed
                    grabbed?.isPressed = false
                    grabbed = null
                    if (c != null && !dragged && event.eventTime - downTime < TAP_MS) {
                        when (val spec = editSpecs[c]) {
                            is PadButtonSpec -> onEditButton?.invoke(spec)
                            is PadWidgetSpec -> onEditWidget?.invoke(spec)
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    grabbed?.isPressed = false
                    grabbed = null
                }
            }
            return true
        }
    }

    private companion object {
        const val TAP_MS = 300L

        /** Press→release gap for an alt-button's quick-tap primary fire. */
        const val TAP_FIRE_MS = 60L
    }
}
