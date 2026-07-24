/*
 * SlidePadRouter — pad-level touch routing for slide-over activation (Slice 5;
 * shared since Slice 6 by the preset pads AND custom layouts).
 *
 * Hit-tests every active finger against the pad's registered buttons on each event
 * and press/releases on the DIFF, so a finger gliding from ◀ to ▲ releases ◀ and
 * presses ▲ without lifting. Multi-finger chords keep working (union across
 * fingers). Buttons under this router must be non-clickable — a clickable child
 * would capture the touch stream from finger-down and the glide would never be
 * seen by the root.
 */
package com.productforge.phonecontroller.ui

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button

internal class SlidePadRouter(
    private val root: ViewGroup,
    private val actions: Map<Button, (Boolean) -> Unit>,
) : View.OnTouchListener {

    private val byPointer = HashMap<Int, Button?>()
    private val held = HashSet<Button>()
    private val hitRect = Rect()

    private fun buttonAt(x: Float, y: Float): Button? {
        for (button in actions.keys) {
            hitRect.set(0, 0, button.width, button.height)
            root.offsetDescendantRectToMyCoords(button, hitRect)
            if (hitRect.contains(x.toInt(), y.toInt())) return button
        }
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                byPointer[event.getPointerId(i)] = buttonAt(event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    byPointer[event.getPointerId(i)] = buttonAt(event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                byPointer.remove(event.getPointerId(event.actionIndex))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> byPointer.clear()
            else -> return true
        }
        refresh()
        return true
    }

    private fun refresh() {
        val now = byPointer.values.filterNotNullTo(HashSet())
        for (button in held) {
            if (button !in now) {
                button.isPressed = false
                actions[button]?.invoke(false)
            }
        }
        for (button in now) {
            if (button !in held) {
                button.isPressed = true
                Haptics.tick(button)
                actions[button]?.invoke(true)
            }
        }
        held.clear()
        held.addAll(now)
    }
}
