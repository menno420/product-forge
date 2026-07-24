/*
 * CustomPadView — renders a user-defined layout (Slice 6; widgets added Slice 15).
 *
 * PLAY mode: buttons are laid out at their percent positions and driven by the
 * shared SlidePadRouter (glide + chords) with per-button turbo; WIDGETS (analog
 * sticks / 8-way D-pad / touchpad / gyro toggle) render as their real interactive
 * views wired to the host, and consume their own touches so they coexist with the
 * button router.
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

        val actions = LinkedHashMap<Button, (Boolean) -> Unit>()
        for (spec in layout.buttons) {
            val button = Button(context).apply {
                text = if (spec.turbo) "${spec.label} ⚡" else spec.label
                isAllCaps = false
                isClickable = false
                isFocusable = false
            }
            ButtonStyler.apply(button, spec, 0)
            buttonViews[button] = spec
            editSpecs[button] = spec
            if (!editMode) actions[button] = turbo.wrap(button, spec.turbo, actionResolver(spec))
            addView(button)
        }

        for (spec in layout.widgets) {
            val view = if (editMode) widgetPlaceholder(spec) else widgetView(spec)
            widgetViews[view] = spec
            editSpecs[view] = spec
            addView(view)
        }

        setOnTouchListener(if (editMode) EditTouch() else SlidePadRouter(this, actions))
        requestLayout()
    }

    /** The live interactive view for a widget (play mode). */
    @SuppressLint("ClickableViewAccessibility")
    private fun widgetView(spec: PadWidgetSpec): View = when (spec.type) {
        PadWidgetType.LEFT_STICK ->
            StickView(context) { x, y -> host?.onLeftStick(x, y) }.apply { deadzonePct = this@CustomPadView.deadzonePct }
        PadWidgetType.RIGHT_STICK ->
            StickView(context) { z, rz -> host?.onRightStick(z, rz) }.apply { deadzonePct = this@CustomPadView.deadzonePct }
        PadWidgetType.DPAD ->
            DpadView(context) { d, down -> host?.onDpad(d, down) }
        PadWidgetType.TOUCHPAD ->
            TouchpadView(
                context,
                object : TouchpadView.Listener {
                    override fun onMove(dx: Int, dy: Int) { host?.onMouseMove(dx, dy) }
                    override fun onScroll(notches: Int) { host?.onMouseScroll(notches) }
                    override fun onTap(button: MouseButton) { host?.onMouseClick(button) }
                },
            )
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
    }
}
