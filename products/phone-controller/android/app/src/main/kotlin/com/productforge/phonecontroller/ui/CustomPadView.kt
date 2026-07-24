/*
 * CustomPadView — renders a user-defined layout (Slice 6).
 *
 * PLAY mode: buttons are laid out at their percent positions and driven by the
 * shared SlidePadRouter (glide + chords), with per-button turbo wrapped by the
 * TurboEngine. MEDIA actions fire on press (tap semantics).
 *
 * EDIT mode: drag a button to move it (positions snap to 1%); a SHORT press
 * (< 300 ms, no drag) opens the per-button dialog the host provides (action /
 * size / turbo / label / delete). The host renders the editor toolbar
 * (add / rename / save / done) outside this view.
 */
package com.productforge.phonecontroller.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import com.productforge.phonecontroller.layout.CustomLayout
import com.productforge.phonecontroller.layout.PadButtonSpec

class CustomPadView(
    context: Context,
    private val layout: CustomLayout,
    private val editMode: Boolean,
    private val actionResolver: (PadButtonSpec) -> (Boolean) -> Unit,
    private val turbo: TurboEngine,
    private val onEditButton: ((PadButtonSpec) -> Unit)? = null,
) : FrameLayout(context) {

    private val buttonViews = LinkedHashMap<Button, PadButtonSpec>()

    init {
        rebuild()
    }

    /** Recreate all child buttons from the model (host calls after dialog edits). */
    @SuppressLint("ClickableViewAccessibility")
    fun rebuild() {
        removeAllViews()
        buttonViews.clear()
        val actions = LinkedHashMap<Button, (Boolean) -> Unit>()
        for (spec in layout.buttons) {
            val button = Button(context).apply {
                text = if (spec.turbo) "${spec.label} ⚡" else spec.label
                isAllCaps = false
                textSize = 14f
                isClickable = false
                isFocusable = false
            }
            buttonViews[button] = spec
            if (!editMode) {
                actions[button] = turbo.wrap(button, spec.turbo, actionResolver(spec))
            }
            addView(button)
        }
        setOnTouchListener(if (editMode) EditTouch() else SlidePadRouter(this, actions))
        requestLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        for ((button, spec) in buttonViews) {
            val bw = (spec.wPct * w).toInt()
            val bh = (spec.hPct * h).toInt()
            val bx = (spec.xPct * w).toInt()
            val by = (spec.yPct * h).toInt()
            button.measure(
                MeasureSpec.makeMeasureSpec(bw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(bh, MeasureSpec.EXACTLY),
            )
            button.layout(bx, by, bx + bw, by + bh)
        }
    }

    /** Edit-mode gesture handling: drag to move, short-press to configure. */
    private inner class EditTouch : OnTouchListener {

        private var grabbed: Button? = null
        private var grabDx = 0f
        private var grabDy = 0f
        private var downTime = 0L
        private var dragged = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    grabbed = buttonViews.keys.lastOrNull { b ->
                        event.x >= b.left && event.x <= b.right && event.y >= b.top && event.y <= b.bottom
                    }
                    grabbed?.let { b ->
                        grabDx = event.x - b.left
                        grabDy = event.y - b.top
                        b.isPressed = true
                    }
                    downTime = event.eventTime
                    dragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val b = grabbed ?: return true
                    val spec = buttonViews[b] ?: return true
                    if (width == 0 || height == 0) return true
                    val newX = (event.x - grabDx) / width
                    val newY = (event.y - grabDy) / height
                    // 1% snap keeps saved layouts tidy and diffs stable.
                    val snappedX = (newX * 100).toInt() / 100f
                    val snappedY = (newY * 100).toInt() / 100f
                    if (snappedX != spec.xPct || snappedY != spec.yPct) {
                        if (event.eventTime - downTime > TAP_MS || dragged) dragged = true
                        spec.xPct = snappedX
                        spec.yPct = snappedY
                        spec.clampToPad()
                        requestLayout()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val b = grabbed
                    grabbed?.isPressed = false
                    grabbed = null
                    if (b != null && !dragged && event.eventTime - downTime < TAP_MS) {
                        buttonViews[b]?.let { onEditButton?.invoke(it) }
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
